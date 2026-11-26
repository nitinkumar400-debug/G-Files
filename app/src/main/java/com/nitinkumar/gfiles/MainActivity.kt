package com.nitinkumar.gfiles

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.os.Build
import android.util.Size
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.DateFormat
import java.util.Date

private val Navy = Color(0xFF071426)
private val Panel = Color(0xFF102844)
private val Blue = Color(0xFF42A5F5)
private val Cyan = Color(0xFF68E1FD)
private val Muted = Color(0xFF9DB2C9)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = android.graphics.Color.rgb(8, 91, 145)
        window.navigationBarColor = android.graphics.Color.rgb(7, 20, 38)
        val prefs = getSharedPreferences("gfiles_settings", MODE_PRIVATE)
        var headerImage by mutableStateOf(prefs.getString("header_image_uri", null))
        val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                prefs.edit().putString("header_image_uri", uri.toString()).apply()
                headerImage = uri.toString()
            }
        }
        setContent {
            GFilesApp(
                hasAccess = { Environment.isExternalStorageManager() },
                requestAccess = {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName"))
                    startActivity(intent)
                },
                headerImageUri = headerImage,
                chooseHeaderImage = { imagePicker.launch(arrayOf("image/*")) },
                openFile = { file ->
                    val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.provider", file)
                    val mime = if (file.extension.equals("apk", true)) "application/vnd.android.package-archive" else contentResolver.getType(uri) ?: "*/*"
                    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun GFilesApp(hasAccess: () -> Boolean, requestAccess: () -> Unit, headerImageUri: String?, chooseHeaderImage: () -> Unit, openFile: (File) -> Unit) {
    var current by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    var refresh by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var dark by remember { mutableStateOf(true) }
    var grid by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf("LOCAL") }
    var home by remember { mutableStateOf(true) }
    var menuFile by remember { mutableStateOf<File?>(null) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var renameText by remember { mutableStateOf("") }
    var infoDialog by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    val selectedFiles = remember { mutableStateListOf<File>() }
    var clipboardFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    val context = LocalContext.current

    BackHandler(enabled = !home || page != "LOCAL") {
        if (page != "LOCAL") { page = "LOCAL"; home = true; query = "" }
        else {
            val root = Environment.getExternalStorageDirectory()
            val parent = current.parentFile
            if (current.name.equals("Download", true)) { current = root; home = true; query = "" }
            else if (parent != null && (parent.absolutePath == root.absolutePath || parent.absolutePath.startsWith(root.absolutePath + File.separator))) current = parent
            else { current = root; home = true; query = "" }
        }
    }

    val bg = if (dark) Navy else Color(0xFFF2F6FC)
    val fg = if (dark) Color.White else Color(0xFF102033)
    val card = if (dark) Panel else Color.White
    val storage = remember(refresh) {
        runCatching {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            Triple(total, total - free, if (total > 0) (total - free).toFloat() / total else 0f)
        }.getOrElse { Triple(0L, 0L, 0f) }
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Blue, background = bg, surface = card)
        else lightColorScheme(primary = Color(0xFF1565C0), background = bg, surface = card)) {
        Box(Modifier.fillMaxSize().background(bg)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF087FAE), Color(0xFF164E91), Color(0xFF172C55)))).padding(horizontal = 18.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Folder, null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(top = 5.dp)) {
                    Text("G Files", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (home) "Your storage at a glance" else current.name.ifBlank { "Internal storage" },
                        color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { searchOpen = true }) {
                    Icon(Icons.Default.Search, "Search", tint = Color.White)
                }
                IconButton(onClick = { dark = !dark }) {
                    Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, "Theme", tint = Color.White)
                }
                IconButton(onClick = { settingsOpen = true }) {
                    Icon(Icons.Default.MoreVert, "More", tint = Color.White)
                }
            }

            if (home && page == "LOCAL") {
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier.fillMaxWidth().height(190.dp)
                            .background(Brush.verticalGradient(listOf(Color(0xFF087FAE), Color(0xFF14518C), bg)))
                    ) {
                        val context = LocalContext.current
                        val headerBitmap = remember(headerImageUri) {
                            runCatching { headerImageUri?.let { context.contentResolver.openInputStream(Uri.parse(it))?.use { stream -> BitmapFactory.decodeStream(stream) } } }.getOrNull()
                        }
                        if (headerBitmap != null) {
                            Image(bitmap = headerBitmap.asImageBitmap(), contentDescription = "Custom home header",
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Box(Modifier.fillMaxSize().background(Color(0x66071426)))
                        }
                        Text("Nitin Chaudhary", modifier = Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 24.dp),
                            color = Color.White, style = MaterialTheme.typography.headlineMedium,
                            fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Bold)
                    }

                    Row(Modifier.fillMaxWidth().background(card), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("LOCAL", "NETWORK").forEach { tab ->
                            Column(
                                Modifier.weight(1f).clickable { page = tab; home = tab == "LOCAL" },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(tab, color = if (page == tab) Blue else fg.copy(alpha = .65f),
                                    modifier = Modifier.padding(vertical = 15.dp), fontWeight = FontWeight.Bold)
                                Box(Modifier.fillMaxWidth().height(3.dp).background(if (page == tab) Blue else Color.Transparent))
                            }
                        }
                    }

                    if (page == "LOCAL") {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                HomeTile("Main storage", Icons.Default.FolderSpecial, Color(0xFF5B6FE8), Modifier.weight(1f)) {
                                    current = Environment.getExternalStorageDirectory(); home = false
                                }
                                HomeTile("Downloads", Icons.Default.Downloading, Color(0xFF00AFA0), Modifier.weight(1f)) {
                                    current = File(Environment.getExternalStorageDirectory(), "Download")
                                    home = false
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            } else if (page != "LOCAL") {
                Column(Modifier.weight(1f).fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (page == "LIBRARY") Icons.Default.CollectionsBookmark else Icons.Default.Wifi,
                        null, tint = Blue, modifier = Modifier.size(54.dp))
                    Spacer(Modifier.height(14.dp))
                    Text(if (page == "LIBRARY") "Library" else "Network", color = fg, style = MaterialTheme.typography.headlineSmall)
                    Text(if (page == "LIBRARY") "Your photos, videos and documents will appear here."
                        else "Connect to shared network storage in a future update.",
                        color = fg.copy(alpha = .65f))
                    TextButton(onClick = { page = "LOCAL"; home = true }) { Text("Back to Local") }
                }
            } else {
                Column(Modifier.weight(1f).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(current.name.ifBlank { "Internal storage" }, color = fg, fontWeight = FontWeight.SemiBold)
                            Text(current.absolutePath, color = fg.copy(alpha = .55f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        IconButton(onClick = { grid = !grid }) { Icon(if (grid) Icons.Default.ViewList else Icons.Default.GridView, "Layout", tint = fg) }
                        IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, "Refresh", tint = fg) }
                        IconButton(onClick = { home = true; query = "" }) { Icon(Icons.Default.Home, "Home", tint = fg) }
                    }
                    if (!hasAccess()) {
                        Card(colors = CardDefaults.cardColors(containerColor = card), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp)) {
                                Text("Allow file access", color = fg, fontWeight = FontWeight.Bold)
                                Text("G Files needs All files access to browse and manage local storage.", color = fg.copy(alpha = .7f))
                                Button(onClick = requestAccess, modifier = Modifier.padding(top = 10.dp)) { Text("Grant permission") }
                            }
                        }
                    } else {
                        val files = remember(current, refresh, query) {
                            (current.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }) ?: emptyList())
                                .filter { !it.name.startsWith(".") || query.isNotBlank() }
                                .filter { it.name.contains(query, true) }
                        }
                        if (files.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("This folder is empty", color = fg.copy(alpha = .6f))
                        } else if (grid) {
                            LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 112.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                gridItems(files, key = { it.absolutePath }) { file ->
                                    Card(colors = CardDefaults.cardColors(containerColor = card.copy(alpha = .96f)),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth().combinedClickable(
                                            onClick = { if (selectedFiles.isNotEmpty()) { if (selectedFiles.contains(file)) selectedFiles.remove(file) else selectedFiles.add(file) } else if (file.isDirectory) current = file else openFile(file) },
                                            onLongClick = { if (!selectedFiles.contains(file)) selectedFiles.add(file) }
                                        )) {
                                        Column(Modifier.padding(9.dp)) {
                                            FileThumbnail(file = file, modifier = Modifier.fillMaxWidth().height(112.dp),
                                                tint = if (file.isDirectory) Cyan else Blue)
                                            Spacer(Modifier.height(7.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(file.name, color = fg, fontWeight = FontWeight.Medium,
                                                    maxLines = 2, modifier = Modifier.weight(1f),
                                                    style = MaterialTheme.typography.bodySmall)
                                                IconButton(onClick = { menuFile = file }, modifier = Modifier.size(30.dp)) {
                                                    Icon(Icons.Default.MoreVert, "More", tint = fg, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                            Text(if (file.isDirectory) "Folder" else formatSize(file.length()),
                                                color = fg.copy(alpha = .55f), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(files, key = { it.absolutePath }) { file ->
                                Card(colors = CardDefaults.cardColors(containerColor = card.copy(alpha = .96f)),
                                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().combinedClickable(
                                        onClick = { if (selectedFiles.isNotEmpty()) { if (selectedFiles.contains(file)) selectedFiles.remove(file) else selectedFiles.add(file) } else if (file.isDirectory) current = file else openFile(file) },
                                        onLongClick = { if (!selectedFiles.contains(file)) selectedFiles.add(file) }
                                    )) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (file.isDirectory) Icons.Default.Folder else fileIcon(file), null,
                                            tint = if (file.isDirectory) Cyan else Blue, modifier = Modifier.size(28.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(file.name, color = fg, fontWeight = FontWeight.Medium, maxLines = 1)
                                            Text(if (file.isDirectory) "Folder" else "${formatSize(file.length())} • ${DateFormat.getDateInstance().format(Date(file.lastModified()))}",
                                                color = fg.copy(alpha = .55f), style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(onClick = { menuFile = file }) { Icon(Icons.Default.MoreVert, "More", tint = fg) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedFiles.isNotEmpty()) {
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp)
                .navigationBarsPadding(),
            color = Color(0xE6224568), shape = RoundedCornerShape(24.dp), shadowElevation = 18.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .22f))
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Text("${selectedFiles.size}", color = Color.White, fontWeight = FontWeight.Bold)
                IconButton(onClick = { clipboardFiles = selectedFiles.toList(); selectedFiles.clear() }) {
                    Icon(Icons.Default.ContentCopy, "Copy", tint = Color.White)
                }
                IconButton(enabled = clipboardFiles.isNotEmpty(), onClick = {
                    clipboardFiles.forEach { source -> runCatching {
                        val target = File(current, source.name)
                        if (source.isDirectory) source.copyRecursively(target, true) else source.copyTo(target, true)
                    } }
                    clipboardFiles = emptyList(); refresh++
                }) { Icon(Icons.Default.ContentPaste, "Paste", tint = if (clipboardFiles.isNotEmpty()) Color.White else Muted) }
                IconButton(onClick = { selectedFiles.toList().forEach { runCatching { it.deleteRecursively() } }; selectedFiles.clear(); refresh++ }) {
                    Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFFF8A80))
                }
                IconButton(onClick = {
                    runCatching {
                        val uris = ArrayList<Uri>()
                        selectedFiles.forEach { file -> uris.add(androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)) }
                        val share = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
                            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(share, "Share files"))
                    }
                }) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
                IconButton(enabled = selectedFiles.size == 1, onClick = {
                    selectedFiles.firstOrNull()?.let { renameTarget = it; renameText = it.name }
                    selectedFiles.clear()
                }) { Icon(Icons.Default.Edit, "Rename", tint = if (selectedFiles.size == 1) Color.White else Muted) }
            }
        }
    }
    }
    if (searchOpen) {
        AlertDialog(onDismissRequest = { searchOpen = false },
            title = { Text("Search files and folders") },
            text = {
                OutlinedTextField(value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Enter file or folder name") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") } })
            },
            confirmButton = { TextButton(onClick = { searchOpen = false; home = false }) { Text("Search") } },
            dismissButton = { TextButton(onClick = { searchOpen = false; query = "" }) { Text("Cancel") } })
    }
    if (settingsOpen) {
        AlertDialog(onDismissRequest = { settingsOpen = false }, title = { Text("Settings") },
            text = { Text("Home header image") },
            confirmButton = { TextButton(onClick = { settingsOpen = false; chooseHeaderImage() }) { Text("Choose image") } },
            dismissButton = { TextButton(onClick = { settingsOpen = false }) { Text("Close") } })
    }
    infoDialog?.let { message ->
        AlertDialog(onDismissRequest = { infoDialog = null }, title = { Text("G Files") },
            text = { Text(message) }, confirmButton = { TextButton(onClick = { infoDialog = null }) { Text("OK") } })
    }
    menuFile?.let { f ->
        AlertDialog(onDismissRequest = { menuFile = null }, title = { Text(f.name) }, text = {
            Column { Text("Size: ${formatSize(f.length())}"); Text("Path: ${f.absolutePath}") }
        }, confirmButton = { TextButton(onClick = { renameTarget = f; renameText = f.name; menuFile = null }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { f.deleteRecursively(); menuFile = null; refresh++ }) { Text("Delete") } })
    }
    renameTarget?.let { f ->
        AlertDialog(onDismissRequest = { renameTarget = null }, title = { Text("Rename file") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { val dest = File(f.parentFile, renameText); if (renameText.isNotBlank()) f.renameTo(dest); renameTarget = null; refresh++ }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } })
    }
}

@Composable
private fun FileThumbnail(file: File, modifier: Modifier = Modifier, tint: Color) {
    val bitmap = remember(file.absolutePath, file.lastModified()) {
        runCatching {
            when (file.extension.lowercase()) {
                "jpg", "jpeg", "png", "webp", "gif", "heic" -> BitmapFactory.decodeFile(file.absolutePath)
                "mp4", "mkv", "mov", "webm", "3gp" -> if (Build.VERSION.SDK_INT >= 29)
                    ThumbnailUtils.createVideoThumbnail(file, Size(320, 320), null)
                    else null
                else -> null
            }
        }.getOrNull()
    }
    Box(modifier.background(Color(0xFF1A3552), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = file.name,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(if (file.isDirectory) Icons.Default.Folder else fileIcon(file), null,
                tint = tint, modifier = Modifier.size(42.dp))
        }
    }
}

@Composable
private fun HomeTile(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color,
                     modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.background(Color.White.copy(alpha = .055f), RoundedCornerShape(18.dp))
        .clickable(onClick = onClick).padding(vertical = 15.dp, horizontal = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(66.dp).background(tint, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

private fun fileIcon(file: File) = when (file.extension.lowercase()) {
    "jpg", "jpeg", "png", "webp", "gif", "heic" -> Icons.Default.Image
    "mp4", "mkv", "mov", "3gp" -> Icons.Default.VideoFile
    "mp3", "wav", "m4a", "aac", "flac" -> Icons.Default.AudioFile
    "pdf" -> Icons.Default.PictureAsPdf
    else -> Icons.Default.InsertDriveFile
}
private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
}
