package org.example.project

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class MdBlock {
    data class Heading(val level: Int, val inlines: List<MdInline>) : MdBlock()
    data class Paragraph(val inlines: List<MdInline>) : MdBlock()
    data class Code(val code: String, val lang: String) : MdBlock()
    data class BulletList(val items: List<List<MdInline>>) : MdBlock()
    data class OrderedList(val items: List<List<MdInline>>) : MdBlock()
    data class Blockquote(val inlines: List<MdInline>) : MdBlock()
    data class ImageBlock(val alt: String, val src: String) : MdBlock()
    object Rule : MdBlock()
    object BlankLine : MdBlock()
}

sealed class MdInline {
    data class Plain(val text: String) : MdInline()
    data class Bold(val text: String) : MdInline()
    data class Italic(val text: String) : MdInline()
    data class BoldItalic(val text: String) : MdInline()
    data class Code(val text: String) : MdInline()
    data class Strike(val text: String) : MdInline()
    data class Link(val label: String, val url: String) : MdInline()
}

fun parseMarkdown(input: String): List<MdBlock> {
    val lines = input.lines()
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val imgMatch = Regex("^!\\[(.*)\\]\\((.+)\\)\\s*$").find(line)
        if (imgMatch != null) { blocks.add(MdBlock.ImageBlock(imgMatch.groupValues[1], imgMatch.groupValues[2])); i++; continue }
        val imgTagMatch = Regex("^\\[IMG:(.+)]\\s*$").find(line)
        if (imgTagMatch != null) { blocks.add(MdBlock.ImageBlock("", imgTagMatch.groupValues[1])); i++; continue }
        if (line.trimStart().startsWith("```")) {
            val lang = line.trimStart().removePrefix("```").trim(); val code = mutableListOf<String>(); i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) { code.add(lines[i]); i++ }
            blocks.add(MdBlock.Code(code.joinToString("\n"), lang)); i++; continue
        }
        if (line.trim().matches(Regex("^(---|\\*\\*\\*|___)\\s*$"))) { blocks.add(MdBlock.Rule); i++; continue }
        val hMatch = Regex("^(#{1,6})\\s+(.*)").find(line)
        if (hMatch != null) { blocks.add(MdBlock.Heading(hMatch.groupValues[1].length, parseInlines(hMatch.groupValues[2]))); i++; continue }
        if (line.startsWith(">")) {
            val qt = mutableListOf<String>()
            while (i < lines.size && lines[i].startsWith(">")) { qt.add(lines[i].removePrefix(">").trimStart()); i++ }
            blocks.add(MdBlock.Blockquote(parseInlines(qt.joinToString(" ")))); continue
        }
        if (line.matches(Regex("^[\\-\\*\\+]\\s+.*"))) {
            val items = mutableListOf<List<MdInline>>()
            while (i < lines.size && lines[i].matches(Regex("^[\\-\\*\\+]\\s+.*"))) { items.add(parseInlines(lines[i].replaceFirst(Regex("^[\\-\\*\\+]\\s+"), ""))); i++ }
            blocks.add(MdBlock.BulletList(items)); continue
        }
        if (line.matches(Regex("^\\d+\\.\\s+.*"))) {
            val items = mutableListOf<List<MdInline>>()
            while (i < lines.size && lines[i].matches(Regex("^\\d+\\.\\s+.*"))) { items.add(parseInlines(lines[i].replaceFirst(Regex("^\\d+\\.\\s+"), ""))); i++ }
            blocks.add(MdBlock.OrderedList(items)); continue
        }
        if (line.isBlank()) { blocks.add(MdBlock.BlankLine); i++; continue }
        val para = mutableListOf<String>()
        while (i < lines.size) {
            val l = lines[i]
            if (l.isBlank() || l.trimStart().startsWith("```") || l.trim().matches(Regex("^(---|\\*\\*\\*|___)\\s*$"))
                || l.matches(Regex("^#{1,6}\\s+.*")) || l.startsWith(">")
                || l.matches(Regex("^[\\-\\*\\+]\\s+.*")) || l.matches(Regex("^\\d+\\.\\s+.*"))
                || Regex("^!\\[(.*)\\]\\((.+)\\)\\s*$").matches(l)) break
            para.add(l); i++
        }
        if (para.isNotEmpty()) blocks.add(MdBlock.Paragraph(parseInlines(para.joinToString(" "))))
    }
    return blocks
}

fun parseInlines(text: String): List<MdInline> {
    val result = mutableListOf<MdInline>()
    var remaining = text
    while (remaining.isNotEmpty()) {
        val candidates = listOfNotNull(
            Regex("\\*\\*\\*(.+?)\\*\\*\\*").find(remaining)?.let { Triple(it.range.first, "bi", it) },
            Regex("\\*\\*(.+?)\\*\\*").find(remaining)?.let { Triple(it.range.first, "b", it) },
            Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)").find(remaining)?.let { Triple(it.range.first, "i", it) },
            Regex("~~(.+?)~~").find(remaining)?.let { Triple(it.range.first, "s", it) },
            Regex("`(.+?)`").find(remaining)?.let { Triple(it.range.first, "c", it) },
            Regex("\\[(.+?)]\\((.+?)\\)").find(remaining)?.let { Triple(it.range.first, "l", it) },
        ).sortedBy { it.first }
        if (candidates.isEmpty()) { result.add(MdInline.Plain(remaining)); break }
        val (start, type, match) = candidates.first()
        if (start > 0) result.add(MdInline.Plain(remaining.substring(0, start)))
        when (type) {
            "bi" -> result.add(MdInline.BoldItalic(match.groupValues[1]))
            "b"  -> result.add(MdInline.Bold(match.groupValues[1]))
            "i"  -> result.add(MdInline.Italic(match.groupValues[1]))
            "s"  -> result.add(MdInline.Strike(match.groupValues[1]))
            "c"  -> result.add(MdInline.Code(match.groupValues[1]))
            "l"  -> result.add(MdInline.Link(match.groupValues[1], match.groupValues[2]))
        }
        remaining = remaining.substring(match.range.last + 1)
    }
    return result
}

fun List<MdInline>.toAnnotatedString(codeBg: androidx.compose.ui.graphics.Color, linkColor: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        forEach { inline ->
            when (inline) {
                is MdInline.Plain      -> append(inline.text)
                is MdInline.Bold       -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inline.text) }
                is MdInline.Italic     -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inline.text) }
                is MdInline.BoldItalic -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) { append(inline.text) }
                is MdInline.Strike     -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(inline.text) }
                is MdInline.Code       -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, fontSize = 13.sp)) { append(inline.text) }
                is MdInline.Link       -> withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) { append(inline.label) }
            }
        }
    }

@Composable
fun MarkdownViewer(text: String, notesDir: String = "", modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val codeBg = colorScheme.surfaceVariant
    val linkColor = colorScheme.primary
    val blocks = remember(text) { parseMarkdown(text) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val (sz, fw) = when (block.level) {
                        1 -> 26.sp to FontWeight.Bold; 2 -> 20.sp to FontWeight.Bold
                        3 -> 17.sp to FontWeight.SemiBold; else -> 15.sp to FontWeight.Medium
                    }
                    Text(block.inlines.toAnnotatedString(codeBg, linkColor), fontSize = sz, fontWeight = fw,
                        modifier = Modifier.padding(top = if (block.level <= 2) 10.dp else 4.dp))
                }
                is MdBlock.Paragraph ->
                    Text(block.inlines.toAnnotatedString(codeBg, linkColor), lineHeight = 22.sp)
                is MdBlock.Code -> Surface(shape = RoundedCornerShape(8.dp), color = codeBg, modifier = Modifier.fillMaxWidth()) {
                    Text(block.code, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp))
                }
                is MdBlock.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    block.items.forEach { Row { Text("• ", fontWeight = FontWeight.Bold); Text(it.toAnnotatedString(codeBg, linkColor), lineHeight = 22.sp) } }
                }
                is MdBlock.OrderedList -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    block.items.forEachIndexed { idx, it -> Row { Text("${idx+1}. ", fontWeight = FontWeight.Bold); Text(it.toAnnotatedString(codeBg, linkColor), lineHeight = 22.sp) } }
                }
                is MdBlock.Blockquote -> Row {
                    Box(Modifier.width(3.dp).height(IntrinsicSize.Min).background(colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(10.dp))
                    Text(block.inlines.toAnnotatedString(codeBg, linkColor), color = colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic, lineHeight = 22.sp)
                }
                is MdBlock.ImageBlock -> {
                    val absPath = remember(block.src, notesDir) {
                        if (block.src.startsWith("/")) block.src
                        else FileSystem.join(notesDir, block.src)
                    }
                    val bitmap = remember(absPath) { loadImageBitmap(absPath) }
                    if (bitmap != null) {
                        Image(bitmap = bitmap, contentDescription = block.alt,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.FillWidth)
                    } else {
                        Text("🖼 ${block.alt.ifEmpty { block.src.substringAfterLast("/") }}", color = colorScheme.primary)
                    }
                }
                is MdBlock.Rule -> HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colorScheme.outlineVariant)
                is MdBlock.BlankLine -> Spacer(Modifier.height(6.dp))
            }
        }
    }
}
