package com.nitinkumar.gfiles

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
        setContent {
            GFilesApp(
                hasAccess = { Environment.isExternalStorageManager() },
                requestAccess = {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName"))
                    startActivity(intent)
                },
                openFile = { file ->
                    val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.provider", file)
                    val mime = contentResolver.getType(uri) ?: "*/*"
                    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            )
        }
    }
}

@Composable
fun GFilesApp(hasAccess: () -> Boolean, requestAccess: () -> Unit, openFile: (File) -> Unit) {
    var current by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    var refresh by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var dark by remember { mutableStateOf(true) }
    var grid by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("All") }
    var page by remember { mutableStateOf("LOCAL") }
    var home by remember { mutableStateOf(true) }
    var menuFile by remember { mutableStateOf<File?>(null) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var renameText by remember { mutableStateOf("") }
    var infoDialog by remember { mutableStateOf<String?>(null) }

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
        Column(Modifier.fillMaxSize().background(bg)) {
            Row(
                Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF087FAE), Color(0xFF164E91), Color(0xFF172C55))).padding(horizontal = 18.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Folder, null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("G Files", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (home) "Your storage at a glance" else current.name.ifBlank { "Internal storage" },
                        color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { dark = !dark }) {
                    Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, "Theme", tint = Color.White)
                }
                IconButton(onClick = { infoDialog = "G Files • Storage manager" }) {
                    Icon(Icons.Default.MoreVert, "More", tint = Color.White)
                }
            }

            if (home && page == "LOCAL") {
                Column(Modifier.weight(1f)) {
                    Column(
                        Modifier.fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color(0xFF087FAE), Color(0xFF14518C), bg)))
                            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 28.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(184.dp)) {
                                    CircularProgressIndicator(
                                        progress = { storage.third.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxSize(),
                                        color = Color.White,
                                        trackColor = Color.White.copy(alpha = .25f),
                                        strokeWidth = 9.dp
                                    )
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${(storage.third * 100).toInt()}%", color = Color.White,
                                            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Light)
                                        Text("Main storage", color = Color.White, fontWeight = FontWeight.Medium)
                                        Text("${formatSize(storage.second)} / ${formatSize(storage.first)}",
                                            color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            Spacer(Modifier.width(20.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf("Images" to Icons.Default.Image, "Audio" to Icons.Default.AudioFile, "Videos" to Icons.Default.VideoFile).forEach { (name, icon) ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable {
                                            selectedTab = name
                                            home = false
                                        },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(icon, null, tint = Color.White.copy(alpha = .9f), modifier = Modifier.size(21.dp))
                                        Spacer(Modifier.width(9.dp))
                                        Text(name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { infoDialog = "Storage used: ${formatSize(storage.second)}\nTotal capacity: ${formatSize(storage.first)}\nFree space: ${formatSize(storage.first - storage.second)}" },
                                    modifier = Modifier.fillMaxWidth(),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .8f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) { Text("STORAGE INFO", fontWeight = FontWeight.Bold) }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            Box(Modifier.size(8.dp).background(Color(0xFF8DA7FF), CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.size(8.dp).background(Color.White.copy(alpha = .35f), CircleShape))
                        }
                    }

                    Row(Modifier.fillMaxWidth().background(card), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("LOCAL", "LIBRARY", "NETWORK").forEach { tab ->
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
                                HomeTile("Main storage", Icons.Default.PhoneAndroid, Color(0xFF1598E8), Modifier.weight(1f)) {
                                    current = Environment.getExternalStorageDirectory(); selectedTab = "All"; home = false
                                }
                                HomeTile("Downloads", Icons.Default.Download, Color(0xFF278FE0), Modifier.weight(1f)) {
                                    current = File(Environment.getExternalStorageDirectory(), "Download")
                                    selectedTab = "All"; home = false
                                }
                                HomeTile("Apps", Icons.Default.Android, Color(0xFF83B936), Modifier.weight(1f)) {
                                    infoDialog = "Apps manager is not available in this version yet."
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                HomeTile("Recycle Bin", Icons.Default.Delete, Color(0xFF607D8B), Modifier.weight(1f)) {
                                    infoDialog = "Recycle Bin is not enabled yet. Deleted files are permanently removed."
                                }
                                Spacer(Modifier.weight(2f))
                            }
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
                        IconButton(onClick = { if (current.parentFile != null) current = current.parentFile!! else home = true }) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = fg)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(current.name.ifBlank { "Internal storage" }, color = fg, fontWeight = FontWeight.SemiBold)
                            Text(current.absolutePath, color = fg.copy(alpha = .55f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        IconButton(onClick = { grid = !grid }) { Icon(if (grid) Icons.Default.ViewList else Icons.Default.GridView, "Layout", tint = fg) }
                        IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, "Refresh", tint = fg) }
                        IconButton(onClick = { home = true; selectedTab = "All"; query = "" }) { Icon(Icons.Default.Home, "Home", tint = fg) }
                    }
                    OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search files and folders") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true, shape = RoundedCornerShape(16.dp))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("All", "Images", "Videos", "Audio", "Documents").forEach { tab ->
                            FilterChip(selected = selectedTab == tab, onClick = { selectedTab = tab }, label = { Text(tab) })
                        }
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
                        val files = remember(current, refresh, query, selectedTab) {
                            (current.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }) ?: emptyList())
                                .filter { !it.name.startsWith(".") || query.isNotBlank() }
                                .filter { it.name.contains(query, true) }
                                .filter { f -> when (selectedTab) {
                                    "Images" -> f.isDirectory || f.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif", "heic")
                                    "Videos" -> f.isDirectory || f.extension.lowercase() in listOf("mp4", "mkv", "mov", "webm", "3gp")
                                    "Audio" -> f.isDirectory || f.extension.lowercase() in listOf("mp3", "wav", "m4a", "aac", "flac", "ogg")
                                    "Documents" -> f.isDirectory || f.extension.lowercase() in listOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx")
                                    else -> true
                                } }
                        }
                        if (files.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("This folder is empty", color = fg.copy(alpha = .6f))
                        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(files, key = { it.absolutePath }) { file ->
                                Card(colors = CardDefaults.cardColors(containerColor = card.copy(alpha = .96f)),
                                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable {
                                        if (file.isDirectory) current = file else openFile(file)
                                    }) {
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
