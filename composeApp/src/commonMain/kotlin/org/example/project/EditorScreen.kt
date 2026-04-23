package org.example.project

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class ViewMode { EDIT, SPLIT, PREVIEW }

// ── Plain-mode content blocks ─────────────────────────────────────────────────

sealed class PlainBlock {
    abstract val id: String
    data class Text(override val id: String, val tfv: TextFieldValue) : PlainBlock()
    data class Img(override val id: String, val path: String) : PlainBlock()
    data class File(override val id: String, val path: String) : PlainBlock()
}

private val IMG_RE  = Regex("""^\[IMG:(.+)]$""")
private val FILE_RE = Regex("""^\[FILE:(.+)]$""")
private val imageExts = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

private fun contentToBlocks(content: String): List<PlainBlock> {
    val result = mutableListOf<PlainBlock>()
    val buf = StringBuilder()

    fun flush() { result += PlainBlock.Text(newId(), TextFieldValue(buf.toString())); buf.clear() }

    for (line in content.lines()) {
        val imgPath  = IMG_RE.find(line)?.groupValues?.get(1)
        val filePath = FILE_RE.find(line)?.groupValues?.get(1)
        when {
            imgPath  != null -> { flush(); result += PlainBlock.Img(newId(), imgPath) }
            filePath != null -> { flush(); result += PlainBlock.File(newId(), filePath) }
            else             -> { if (buf.isNotEmpty()) buf.append('\n'); buf.append(line) }
        }
    }
    result += PlainBlock.Text(newId(), TextFieldValue(buf.toString()))
    return result
}

private fun blocksToContent(blocks: List<PlainBlock>): String = buildString {
    blocks.forEachIndexed { i, block ->
        if (i > 0) append('\n')
        when (block) {
            is PlainBlock.Text -> append(block.tfv.text)
            is PlainBlock.Img  -> append("[IMG:${block.path}]")
            is PlainBlock.File -> append("[FILE:${block.path}]")
        }
    }
}

// ── EditorScreen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    repo: MemoRepository,
    noteId: String?,
    folderId: String?,
    folderName: String?,
    onBack: () -> Unit
) {
    val id = remember { noteId ?: newId() }
    val initialNote = remember { if (noteId != null) repo.loadNote(id, folderId) else null }

    var titleValue  by remember { mutableStateOf(initialNote?.title ?: "") }
    var fieldValue  by remember { mutableStateOf(TextFieldValue(initialNote?.content ?: "")) }
    var useMarkdown by remember { mutableStateOf(initialNote?.useMarkdown ?: false) }
    var viewMode    by remember { mutableStateOf(ViewMode.EDIT) }
    var isModified  by remember { mutableStateOf(false) }
    var savedLabel  by remember { mutableStateOf<String?>(null) }
    var showLinkDialog by remember { mutableStateOf(false) }

    // List<PlainBlock> in mutableStateOf → 할당(=)으로 재조합 보장
    var blocks by remember { mutableStateOf(contentToBlocks(initialNote?.content ?: "")) }
    var focusedBlockIdx by remember { mutableStateOf(0) }

    fun insertMarkdownAtCursor(text: String) {
        val c = fieldValue.selection.end
        fieldValue = TextFieldValue(
            fieldValue.text.substring(0, c) + text + fieldValue.text.substring(c),
            selection = TextRange(c + text.length)
        )
        isModified = true
    }

    fun insertBlock(srcPath: String) {
        val destPath = repo.addAttachment(id, folderId, srcPath)
        val isImage = destPath.substringAfterLast(".", "").lowercase() in imageExts
        val mediaBlock: PlainBlock = if (isImage) PlainBlock.Img(newId(), destPath) else PlainBlock.File(newId(), destPath)

        val idx   = focusedBlockIdx.coerceIn(0, (blocks.size - 1).coerceAtLeast(0))
        val block = blocks.getOrNull(idx)
        val newBlocks = blocks.toMutableList()

        if (block is PlainBlock.Text) {
            val text    = block.tfv.text
            val cursor  = block.tfv.selection.end
            val lineEnd = text.indexOf('\n', cursor).let { if (it == -1) text.length else it + 1 }
            val before  = text.substring(0, lineEnd)
            val after   = text.substring(lineEnd)
            newBlocks.removeAt(idx)
            newBlocks.addAll(idx, listOf(
                PlainBlock.Text(block.id, TextFieldValue(before, TextRange(before.length))),
                mediaBlock,
                PlainBlock.Text(newId(), TextFieldValue(after, TextRange(0)))
            ))
            focusedBlockIdx = idx + 2
        } else {
            newBlocks.add(idx + 1, mediaBlock)
            newBlocks.add(idx + 2, PlainBlock.Text(newId(), TextFieldValue("")))
            focusedBlockIdx = idx + 2
        }

        blocks = newBlocks   // ← 할당으로 확실히 재조합 트리거
        isModified = true
    }

    fun removeBlock(idx: Int) {
        if (idx !in blocks.indices) return
        val newBlocks = blocks.toMutableList()
        newBlocks.removeAt(idx)
        val prevIdx = idx - 1
        val curIdx  = idx
        if (prevIdx >= 0 && curIdx < newBlocks.size &&
            newBlocks[prevIdx] is PlainBlock.Text && newBlocks[curIdx] is PlainBlock.Text) {
            val prev = newBlocks[prevIdx] as PlainBlock.Text
            val cur  = newBlocks[curIdx]  as PlainBlock.Text
            newBlocks.removeAt(curIdx)
            newBlocks[prevIdx] = PlainBlock.Text(prev.id,
                TextFieldValue(prev.tfv.text + cur.tfv.text, TextRange(prev.tfv.text.length)))
            focusedBlockIdx = prevIdx
        }
        if (newBlocks.isEmpty()) newBlocks.add(PlainBlock.Text(newId(), TextFieldValue("")))
        blocks = newBlocks
        isModified = true
    }

    fun save() {
        val content = if (useMarkdown) fieldValue.text else blocksToContent(blocks)
        repo.saveNote(MemoNote(id, titleValue.ifBlank { "제목 없음" }, content, folderId, currentTimeMillis(), useMarkdown))
        isModified = false
        val ms = currentTimeMillis()
        savedLabel = "%02d:%02d 저장".format((ms / 3_600_000 % 24).toInt(), (ms / 60_000 % 60).toInt())
    }

    LaunchedEffect(isModified) {
        if (!isModified) return@LaunchedEffect
        delay(30_000L)
        if (isModified) save()
    }

    val cs = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.S && (e.isMetaPressed || e.isCtrlPressed)) { save(); true }
                else false
            }
    ) {
        TopAppBar(
            title = {
                Box {
                    if (titleValue.isEmpty()) Text("제목 없음", color = cs.onSurface.copy(alpha = 0.3f))
                    BasicTextField(
                        value = titleValue,
                        onValueChange = { titleValue = it; isModified = true },
                        singleLine = true,
                        textStyle = TextStyle(color = cs.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(cs.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = { save(); onBack() }) { Icon(Icons.Default.ArrowBackIosNew, null) }
            },
            actions = {
                FilterChip(
                    selected = useMarkdown,
                    onClick = { useMarkdown = !useMarkdown; isModified = true },
                    label = { Text("MD", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp).padding(end = 4.dp)
                )
                if (savedLabel != null) Text(savedLabel!!, style = MaterialTheme.typography.labelSmall, color = cs.primary, modifier = Modifier.padding(end = 8.dp))
                if (isModified) IconButton(onClick = ::save) { Icon(Icons.Default.Save, null) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
        )
        HorizontalDivider(color = cs.outline.copy(alpha = 0.5f))

        if (useMarkdown) {
            val noteFilesDir = if (folderId == null) FileSystem.join(FileSystem.baseDir, "notes")
                else FileSystem.join(FileSystem.baseDir, "folders", folderId, "notes")
            Row(modifier = Modifier.weight(1f)) {
                when (viewMode) {
                    ViewMode.EDIT    -> PlainEditor(value = fieldValue, onChange = { fieldValue = it; isModified = true }, monospace = true, modifier = Modifier.fillMaxSize())
                    ViewMode.PREVIEW -> MarkdownViewer(text = fieldValue.text, notesDir = noteFilesDir, modifier = Modifier.fillMaxSize())
                    ViewMode.SPLIT   -> {
                        PlainEditor(value = fieldValue, onChange = { fieldValue = it; isModified = true }, monospace = true, modifier = Modifier.weight(1f).fillMaxHeight())
                        VerticalDivider(color = cs.outline.copy(alpha = 0.4f))
                        MarkdownViewer(text = fieldValue.text, notesDir = noteFilesDir, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        } else {
            PlainBlockEditor(
                blocks = blocks,
                onBlocksChange = { blocks = it; isModified = true },
                onFocusChange = { focusedBlockIdx = it },
                onRemoveBlock = ::removeBlock,
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider(color = cs.outline.copy(alpha = 0.5f))

        Row(
            modifier = Modifier.fillMaxWidth().background(cs.background).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (useMarkdown) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    ViewMode.entries.forEach { mode ->
                        TextButton(
                            onClick = { viewMode = mode },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = if (viewMode == mode) cs.primary else cs.onSurfaceVariant)
                        ) {
                            Text(
                                when (mode) { ViewMode.EDIT -> "편집"; ViewMode.SPLIT -> "분할"; ViewMode.PREVIEW -> "미리보기" },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (viewMode == mode) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
                IconButton(onClick = { showLinkDialog = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Link, "링크", modifier = Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                }
                IconButton(onClick = {
                    pickFile("이미지 선택", "png", "jpg", "jpeg", "gif", "webp")?.let {
                        insertMarkdownAtCursor(repo.attachFileMarkdown(id, folderId, it)); save()
                    }
                }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Image, "이미지", modifier = Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                }
                IconButton(onClick = {
                    val paths = pickFiles("파일 첨부")
                    paths.forEach { insertMarkdownAtCursor(repo.attachFileMarkdown(id, folderId, it)) }
                    if (paths.isNotEmpty()) save()
                }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.AttachFile, "파일", modifier = Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                }
            } else {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    val paths = pickFiles("파일 첨부")
                    paths.forEach { insertBlock(it) }
                    if (paths.isNotEmpty()) save()
                }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.AttachFile, "파일 첨부", modifier = Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                }
                IconButton(onClick = {
                    pickFile("이미지 첨부", "png", "jpg", "jpeg", "gif", "webp")?.let { insertBlock(it); save() }
                }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Image, "이미지 첨부", modifier = Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                }
            }
        }
    }

    if (showLinkDialog) {
        LinkDialog(
            onConfirm = { t, u -> insertMarkdownAtCursor("[$t]($u)"); showLinkDialog = false },
            onDismiss = { showLinkDialog = false }
        )
    }
}

// ── Block-based plain editor ──────────────────────────────────────────────────

@Composable
fun PlainBlockEditor(
    blocks: List<PlainBlock>,
    onBlocksChange: (List<PlainBlock>) -> Unit,
    onFocusChange: (Int) -> Unit,
    onRemoveBlock: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()
    val isEmpty = blocks.size == 1 && blocks[0] is PlainBlock.Text && (blocks[0] as PlainBlock.Text).tfv.text.isEmpty()

    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    fun focusLastTextBlock() {
        val lastText = blocks.indexOfLast { it is PlainBlock.Text }
        if (lastText >= 0) focusRequesters[(blocks[lastText] as PlainBlock.Text).id]?.requestFocus()
    }

    Box(modifier = modifier.background(cs.background)) {
        if (isEmpty) {
            Text(
                "메모를 입력하세요...",
                color = cs.onSurface.copy(alpha = 0.28f),
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            blocks.forEachIndexed { index, block ->
                key(block.id) {
                    when (block) {
                        is PlainBlock.Text -> {
                            val prevIsMedia = index > 0 && blocks[index - 1] !is PlainBlock.Text
                            val nextIsMedia = index < blocks.size - 1 && blocks[index + 1] !is PlainBlock.Text
                            val isEdge = prevIsMedia || nextIsMedia || (index == blocks.size - 1 && blocks.size > 1)
                            val fr = remember(block.id) { FocusRequester().also { focusRequesters[block.id] = it } }

                            Box(modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = if (isEdge) 52.dp else 24.dp)) {
                                if (block.tfv.text.isEmpty() && isEdge) {
                                    Text("텍스트 입력...", color = cs.onSurface.copy(alpha = 0.2f), fontSize = 15.sp)
                                }
                                BasicTextField(
                                    value = block.tfv,
                                    onValueChange = { newTfv ->
                                        onBlocksChange(blocks.mapIndexed { i, b ->
                                            if (i == index) (b as PlainBlock.Text).copy(tfv = newTfv) else b
                                        })
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = if (isEdge) 52.dp else 24.dp)
                                        .focusRequester(fr)
                                        .onFocusChanged { if (it.isFocused) onFocusChange(index) },
                                    textStyle = TextStyle(color = cs.onSurface, fontSize = 15.sp, lineHeight = 26.sp),
                                    cursorBrush = SolidColor(cs.primary)
                                )
                            }
                        }
                        is PlainBlock.Img  -> {
                            InlineImageBlock(path = block.path, onRemove = { onRemoveBlock(index) })
                            Spacer(Modifier.height(4.dp))
                        }
                        is PlainBlock.File -> {
                            InlineFileBlock(path = block.path, onRemove = { onRemoveBlock(index) })
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.fillMaxWidth().height(160.dp).clickable { focusLastTextBlock() })
        }
    }
}

// ── Inline image / file blocks ────────────────────────────────────────────────

@Composable
private fun InlineImageBlock(path: String, onRemove: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var bitmap by remember(path) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(path) { bitmap = loadImageBitmap(path) }

    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) showMenu = true
                    }
                }
            }
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.widthIn(max = 480.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Fit
            )
        } else {
            Surface(shape = RoundedCornerShape(8.dp), color = cs.surfaceVariant, modifier = Modifier.fillMaxWidth().height(80.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(path.substringAfterLast("/"), color = cs.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("삭제") }, onClick = { onRemove(); showMenu = false })
        }
    }
}

@Composable
private fun InlineFileBlock(path: String, onRemove: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val name = path.substringAfterLast("/")
    val ext  = path.substringAfterLast(".", "").lowercase()
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) showMenu = true
                    }
                }
            }
    ) {
        Surface(shape = RoundedCornerShape(8.dp), color = cs.secondaryContainer) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val icon = when (ext) {
                    in setOf("mp3","wav","m4a","aac","flac","ogg") -> Icons.Default.AudioFile
                    "pdf" -> Icons.Default.PictureAsPdf
                    else  -> Icons.Default.InsertDriveFile
                }
                Icon(icon, null, tint = cs.onSecondaryContainer, modifier = Modifier.size(20.dp))
                Text(name, fontSize = 13.sp, color = cs.onSecondaryContainer, maxLines = 1)
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("삭제") }, onClick = { onRemove(); showMenu = false })
        }
    }
}

// ── Markdown-mode text editor ─────────────────────────────────────────────────

@Composable
fun PlainEditor(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    monospace: Boolean,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Box(modifier = modifier.background(cs.background)) {
        if (value.text.isEmpty()) {
            Text(
                if (monospace) "마크다운으로 작성하세요\n\n**굵게**  *기울임*  `코드`  # 제목"
                else           "메모를 입력하세요...",
                color = cs.onSurface.copy(alpha = 0.28f),
                modifier = Modifier.padding(20.dp),
                fontSize = if (monospace) 14.sp else 15.sp
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxSize().padding(20.dp),
            textStyle = TextStyle(
                color = cs.onSurface,
                fontFamily = if (monospace) androidx.compose.ui.text.font.FontFamily.Monospace
                             else androidx.compose.ui.text.font.FontFamily.Default,
                fontSize = 15.sp,
                lineHeight = if (monospace) 24.sp else 26.sp
            ),
            cursorBrush = SolidColor(cs.primary)
        )
    }
}

// ── Link dialog ───────────────────────────────────────────────────────────────

@Composable
private fun LinkDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var url  by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("링크 삽입", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { Text("표시할 텍스트") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = url,  onValueChange = { url  = it }, placeholder = { Text("https://...") },    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { if (url.isNotBlank()) onConfirm(text.ifBlank { url }, url) }) { Text("삽입") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
