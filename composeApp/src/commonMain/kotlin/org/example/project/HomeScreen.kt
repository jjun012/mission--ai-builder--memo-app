package org.example.project

import androidx.compose.animation.*
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun relativeTime(modifiedAt: Long): String {
    val now = currentTimeMillis()
    val diff = now - modifiedAt
    return when {
        diff < 60_000L      -> "방금 전"
        diff < 3_600_000L   -> "${diff / 60_000}분 전"
        diff < 86_400_000L  -> "${diff / 3_600_000}시간 전"
        diff < 604_800_000L -> "${diff / 86_400_000}일 전"
        else                -> "${diff / 86_400_000}일 전"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repo: MemoRepository,
    isDark: Boolean,
    onThemeToggle: () -> Unit,
    folderId: String? = null,
    folderName: String? = null,
    parentId: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenFolder: (MemoFolder) -> Unit,
    onOpenNote: (MemoNote) -> Unit,
    onNewNote: () -> Unit
) {
    var folders by remember(folderId) { mutableStateOf(repo.listFolders(folderId)) }
    var notes   by remember(folderId) { mutableStateOf(repo.listNotes(folderId)) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    var coverRefreshKey by remember { mutableStateOf(0) }

    fun refresh() {
        folders = repo.listFolders(folderId)
        notes   = repo.listNotes(folderId)
        coverRefreshKey++
    }

    val filteredFolders = remember(folders, searchQuery) {
        if (searchQuery.isBlank()) folders
        else folders.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val filteredNotes = remember(notes, searchQuery) {
        val base = if (searchQuery.isBlank()) notes
        else notes.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.content.contains(searchQuery, ignoreCase = true)
        }
        base.sortedWith(compareByDescending<MemoNote> { it.pinned }.thenByDescending { it.modifiedAt })
    }

    val isEmpty = filteredFolders.isEmpty() && filteredNotes.isEmpty()
    val cs = MaterialTheme.colorScheme

    Scaffold(
        containerColor = cs.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(folderName ?: "메모장", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            if (folders.isNotEmpty() || notes.isNotEmpty()) {
                                val summary = buildString {
                                    if (folders.isNotEmpty()) append("폴더 ${folders.size}개")
                                    if (folders.isNotEmpty() && notes.isNotEmpty()) append("  ·  ")
                                    if (notes.isNotEmpty()) append("메모 ${notes.size}개")
                                }
                                Text(summary, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            }
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBackIosNew, null)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onThemeToggle) {
                            Icon(if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode, null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
                )
                // 검색바
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    isFocused = isSearchFocused,
                    onFocusChange = { isSearchFocused = it },
                    focusRequester = searchFocusRequester,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
                )
            }
        },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { showAddMenu = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("추가", fontWeight = FontWeight.SemiBold) },
                    containerColor = cs.primary,
                    contentColor = cs.onPrimary,
                )
                DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("새 메모") },
                        onClick = { showAddMenu = false; onNewNote() },
                        leadingIcon = { Icon(Icons.Default.EditNote, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("새 폴더") },
                        onClick = { showAddMenu = false; showNewFolderDialog = true },
                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) }
                    )
                }
            }
        }
    ) { padding ->
        if (isEmpty && searchQuery.isBlank()) {
            EmptyState(modifier = Modifier.fillMaxSize().padding(padding))
        } else if (isEmpty) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(48.dp), tint = cs.outlineVariant)
                    Text("'$searchQuery' 검색 결과 없음", color = cs.onSurfaceVariant, fontSize = 15.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filteredFolders.isNotEmpty()) {
                    item { SectionLabel("폴더") }
                    items(filteredFolders, key = { "f_${it.id}" }) { folder ->
                        FolderRow(
                            folder = folder,
                            cover = folder.coverPath,
                            coverRefreshKey = coverRefreshKey,
                            noteCount = remember(folder.id) { repo.listNotes(folder.id).size },
                            onClick = { onOpenFolder(folder) },
                            onChangeCover = {
                                val path = pickFile("표지 이미지 선택", "png", "jpg", "jpeg", "webp")
                                if (path != null) { repo.setFolderCover(folder.id, folderId, path); refresh() }
                            },
                            onRenameConfirm = { repo.renameFolder(folder.id, folderId, it); refresh() },
                            onDelete = { repo.deleteFolder(folder.id, folderId); refresh() }
                        )
                    }
                }

                if (filteredNotes.isNotEmpty()) {
                    item {
                        SectionLabel("메모", topPadding = if (filteredFolders.isNotEmpty()) 8.dp else 0.dp)
                    }
                    items(filteredNotes, key = { "n_${it.id}" }) { note ->
                        NoteRow(
                            note = note,
                            searchQuery = searchQuery,
                            onClick = { onOpenNote(note) },
                            onDelete = { repo.deleteNote(note.id, folderId); refresh() },
                            onTogglePin = { repo.togglePin(note.id, folderId); refresh() },
                            onDuplicate = { repo.duplicateNote(note.id, folderId); refresh() }
                        )
                    }
                }

                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }

    if (showNewFolderDialog) {
        InputDialog(
            title = "새 폴더",
            placeholder = "폴더 이름",
            onConfirm = { repo.createFolder(it, folderId); refresh(); showNewFolderDialog = false },
            onDismiss = { showNewFolderDialog = false }
        )
    }
}

// ── 검색바 ────────────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cs.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp), tint = cs.onSurfaceVariant)
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("검색", color = cs.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 14.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = cs.onSurface, fontSize = 14.sp),
                    cursorBrush = SolidColor(cs.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
            AnimatedVisibility(visible = query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(18.dp)) {
                    Icon(Icons.Default.Cancel, null, modifier = Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                }
            }
        }
    }
}

// ── 빈 상태 ───────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = cs.primaryContainer,
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.NoteAdd, null, modifier = Modifier.size(48.dp), tint = cs.primary)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("아직 메모가 없어요", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("우측 하단 추가 버튼으로 시작해 보세요", fontSize = 14.sp, color = cs.onSurfaceVariant)
            }
        }
    }
}

// ── 섹션 레이블 ───────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = topPadding, bottom = 4.dp)
    )
}

// ── 폴더 행 ───────────────────────────────────────────────────────────────────

@Composable
private fun FolderRow(
    folder: MemoFolder,
    cover: String?,
    coverRefreshKey: Int,
    noteCount: Int,
    onClick: () -> Unit,
    onChangeCover: () -> Unit,
    onRenameConfirm: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var bitmap by remember(cover) { mutableStateOf<ImageBitmap?>(null) }
    // coverRefreshKey 포함 → 같은 파일명으로 교체해도 즉시 재로딩
    LaunchedEffect(cover, coverRefreshKey) { bitmap = cover?.let { loadImageBitmap(it) } }
    val cs = MaterialTheme.colorScheme
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isHovered) cs.surfaceVariant.copy(alpha = 0.85f) else cs.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth().hoverable(hoverSource)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(10.dp), color = cs.primaryContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    val bmp = bitmap
                    if (bmp != null) {
                        Image(bitmap = bmp, contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.Folder, null, tint = cs.primary, modifier = Modifier.size(26.dp))
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(folder.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text("메모 ${noteCount}개", fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = cs.outlineVariant, modifier = Modifier.size(20.dp))
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("표지 변경") }, onClick = { showMenu = false; onChangeCover() }, leadingIcon = { Icon(Icons.Default.Image, null) })
                    DropdownMenuItem(text = { Text("이름 변경") }, onClick = { showMenu = false; showRenameDialog = true }, leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("삭제", color = cs.error) }, onClick = { showMenu = false; showDeleteConfirm = true }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = cs.error) })
                }
            }
        }
    }

    if (showRenameDialog) {
        InputDialog(title = "이름 변경", placeholder = "폴더 이름", initialValue = folder.name,
            onConfirm = { onRenameConfirm(it); showRenameDialog = false },
            onDismiss = { showRenameDialog = false })
    }
    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            message = "\"${folder.name}\" 폴더와 안의 모든 메모를 삭제할까요?",
            onConfirm = { onDelete(); showDeleteConfirm = false },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

// ── 메모 행 ───────────────────────────────────────────────────────────────────

@Composable
private fun NoteRow(
    note: MemoNote,
    searchQuery: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    onDuplicate: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val timeStr = remember(note.modifiedAt) { relativeTime(note.modifiedAt) }
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()

    val previewLines = remember(note.content) {
        note.content
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("[IMG:") && !it.startsWith("[FILE:") && !it.startsWith("#") }
            .take(2)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isHovered) cs.surfaceVariant.copy(alpha = 0.4f) else cs.surface,
        border = BorderStroke(1.dp, if (isHovered) cs.outlineVariant else cs.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().hoverable(hoverSource)
    ) {
        Row(
            modifier = Modifier.clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                    .background(if (note.pinned) cs.primary else cs.primary.copy(alpha = 0.25f))
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.pinned) {
                        Icon(Icons.Default.PushPin, null, modifier = Modifier.size(13.dp), tint = cs.primary)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        note.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(timeStr, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                if (previewLines.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    previewLines.forEach { line ->
                        Text(
                            line,
                            fontSize = 13.sp,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 19.sp
                        )
                    }
                }
                if (note.useMarkdown) {
                    Spacer(Modifier.height(6.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = cs.primaryContainer) {
                        Text("MD", style = MaterialTheme.typography.labelSmall, color = cs.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (note.pinned) "고정 해제" else "상단 고정") },
                        onClick = { showMenu = false; onTogglePin() },
                        leadingIcon = { Icon(if (note.pinned) Icons.Default.PushPin else Icons.Default.PushPin, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("복제") },
                        onClick = { showMenu = false; onDuplicate() },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("삭제", color = cs.error) },
                        onClick = { showMenu = false; showDeleteConfirm = true },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = cs.error) }
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            message = "\"${note.title}\" 메모를 삭제할까요?\n삭제 후 복구할 수 없습니다.",
            onConfirm = { onDelete(); showDeleteConfirm = false },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

// ── 삭제 확인 다이얼로그 ──────────────────────────────────────────────────────

@Composable
private fun ConfirmDeleteDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("삭제 확인", fontWeight = FontWeight.SemiBold) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("삭제") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

// ── 입력 다이얼로그 ───────────────────────────────────────────────────────────

@Composable
fun InputDialog(
    title: String,
    placeholder: String,
    initialValue: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onConfirm(value.trim()) }) { Text("확인") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
