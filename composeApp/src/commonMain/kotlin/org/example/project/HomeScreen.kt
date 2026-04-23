package org.example.project

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    fun refresh() {
        folders = repo.listFolders(folderId)
        notes   = repo.listNotes(folderId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(folderName ?: "메모장", fontWeight = FontWeight.SemiBold) },
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { showAddMenu = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Default.Add, null)
                }
                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false }
                ) {
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
        if (folders.isEmpty() && notes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.NoteAdd, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                    Text("+ 버튼으로 시작하세요", color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(folders, key = { "f_${it.id}" }) { folder ->
                    FolderRow(
                        folder = folder,
                        cover = folder.coverPath,
                        noteCount = remember(folder.id) { repo.listNotes(folder.id).size },
                        onClick = { onOpenFolder(folder) },
                        onChangeCover = {
                            val path = pickFile("표지 이미지 선택", "png", "jpg", "jpeg", "webp")
                            if (path != null) { repo.setFolderCover(folder.id, folderId, path); refresh() }
                        },
                        onRename = { showDialog ->
                            if (showDialog) { /* handled inside */ }
                        },
                        onRenameConfirm = { newName ->
                            repo.renameFolder(folder.id, folderId, newName); refresh()
                        },
                        onDelete = { repo.deleteFolder(folder.id, folderId); refresh() }
                    )
                }

                if (folders.isNotEmpty() && notes.isNotEmpty()) {
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) }
                }

                items(notes, key = { "n_${it.id}" }) { note ->
                    NoteRow(
                        note = note,
                        onClick = { onOpenNote(note) },
                        onDelete = { repo.deleteNote(note.id, folderId); refresh() }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showNewFolderDialog) {
        InputDialog(title = "새 폴더", placeholder = "폴더 이름",
            onConfirm = { repo.createFolder(it, folderId); refresh(); showNewFolderDialog = false },
            onDismiss = { showNewFolderDialog = false }
        )
    }
}

@Composable
private fun FolderRow(
    folder: MemoFolder,
    cover: String?,
    noteCount: Int,
    onClick: () -> Unit,
    onChangeCover: () -> Unit,
    onRename: (Boolean) -> Unit,
    onRenameConfirm: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    val bitmap = remember(cover) { cover?.let { loadImageBitmap(it) } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 표지 썸네일 or 기본 아이콘
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(folder.name, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${noteCount}개의 메모", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outlineVariant)
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("표지 변경") }, onClick = { showMenu = false; onChangeCover() }, leadingIcon = { Icon(Icons.Default.Image, null) })
                DropdownMenuItem(text = { Text("이름 변경") }, onClick = { showMenu = false; showRenameDialog = true }, leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) })
                DropdownMenuItem(text = { Text("삭제", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) })
            }
        }
    }

    if (showRenameDialog) {
        InputDialog(title = "이름 변경", placeholder = "폴더 이름", initialValue = folder.name,
            onConfirm = { onRenameConfirm(it); showRenameDialog = false },
            onDismiss = { showRenameDialog = false }
        )
    }
}

@Composable
private fun NoteRow(note: MemoNote, onClick: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(note.title, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val preview = note.content.lines().firstOrNull { it.isNotBlank() }?.trim()
            if (!preview.isNullOrBlank()) {
                Text(preview, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("삭제", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) })
            }
        }
    }
}

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
