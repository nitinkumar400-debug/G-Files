package com.nitinkumar.gfiles

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GFilesApp(
            hasAccess = { Environment.isExternalStorageManager() },
            requestAccess = {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName"))
                startActivity(intent)
            },
            openFile = { file ->
                val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.provider", file)
                val mime = contentResolver.getType(uri) ?: "*/*"
                startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        ) }
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
    var menuFile by remember { mutableStateOf<File?>(null) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var renameText by remember { mutableStateOf("") }
    val bg = if (dark) Navy else Color(0xFFF2F6FC)
    val fg = if (dark) Color.White else Color(0xFF102033)
    val card = if (dark) Panel else Color.White

    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Blue, background = bg, surface = card)
        else lightColorScheme(primary = Color(0xFF1565C0), background = bg, surface = card)) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg, if (dark) Color(0xFF0B2038) else Color(0xFFE5F0FC)))).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Surface(shape = RoundedCornerShape(16.dp), color = Blue.copy(alpha=.2f), modifier=Modifier.size(48.dp)) {
                    Icon(Icons.Default.Folder, null, tint=Cyan, modifier=Modifier.padding(11.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("G Files", color=fg, style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold)
                    Text("Your files, beautifully organized", color=fg.copy(alpha=.65f), style=MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick={dark=!dark}) { Icon(if(dark) Icons.Default.LightMode else Icons.Default.DarkMode, "Theme", tint=fg) }
                IconButton(onClick={grid=!grid}) { Icon(if(grid) Icons.Default.ViewList else Icons.Default.GridView, "Layout", tint=fg) }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),
                placeholder={Text("Search files and folders")},leadingIcon={Icon(Icons.Default.Search,null)},
                singleLine=true,shape=RoundedCornerShape(18.dp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("All","Images","Videos","Audio","Documents").forEach { tab ->
                    FilterChip(selected=selectedTab==tab,onClick={selectedTab=tab},label={Text(tab)})
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment=Alignment.CenterVertically, modifier=Modifier.fillMaxWidth()) {
                IconButton(onClick={ current.parentFile?.let { current=it; query="" } }) { Icon(Icons.Default.ArrowBack, "Back", tint=fg) }
                Column(Modifier.weight(1f)) {
                    Text(current.name.ifBlank { "Internal storage" }, color=fg, fontWeight=FontWeight.SemiBold)
                    Text(current.absolutePath, color=fg.copy(alpha=.55f), style=MaterialTheme.typography.labelSmall, maxLines=1)
                }
                IconButton(onClick={refresh++}) { Icon(Icons.Default.Refresh,"Refresh",tint=fg) }
            }
            if (!hasAccess()) {
                Card(colors=CardDefaults.cardColors(containerColor=card),modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Allow file access",color=fg,fontWeight=FontWeight.Bold)
                        Text("G Files needs All files access to browse and manage local storage.",color=fg.copy(alpha=.7f))
                        Button(onClick=requestAccess,modifier=Modifier.padding(top=10.dp)) { Text("Grant permission") }
                    }
                }
            } else {
                val files = remember(current,refresh,query,selectedTab) {
                    (current.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }) ?: emptyList())
                        .filter { !it.name.startsWith(".") || query.isNotBlank() }
                        .filter { it.name.contains(query,true) }
                        .filter { f -> when(selectedTab) {
                            "Images" -> f.isDirectory || f.extension.lowercase() in listOf("jpg","jpeg","png","webp","gif","heic")
                            "Videos" -> f.isDirectory || f.extension.lowercase() in listOf("mp4","mkv","mov","webm","3gp")
                            "Audio" -> f.isDirectory || f.extension.lowercase() in listOf("mp3","wav","m4a","aac","flac","ogg")
                            "Documents" -> f.isDirectory || f.extension.lowercase() in listOf("pdf","doc","docx","txt","xls","xlsx","ppt","pptx")
                            else -> true
                        } }
                }
                if (files.isEmpty()) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                    Text("This folder is empty",color=fg.copy(alpha=.6f))
                } else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(files,key={it.absolutePath}) { file ->
                        Card(colors=CardDefaults.cardColors(containerColor=card.copy(alpha=.94f)),shape=RoundedCornerShape(16.dp),
                            modifier=Modifier.fillMaxWidth().clickable {
                                if(file.isDirectory) current=file else openFile(file)
                            }) {
                            Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                                Icon(if(file.isDirectory) Icons.Default.Folder else fileIcon(file),null,
                                    tint=if(file.isDirectory) Cyan else Blue,modifier=Modifier.size(28.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(file.name,color=fg,fontWeight=FontWeight.Medium,maxLines=1)
                                    Text(if(file.isDirectory) "Folder" else "${formatSize(file.length())} • ${DateFormat.getDateInstance().format(Date(file.lastModified()))}",
                                        color=fg.copy(alpha=.55f),style=MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick={menuFile=file}) { Icon(Icons.Default.MoreVert,"More",tint=fg) }
                            }
                        }
                    }
                }
            }
        }
    }
    menuFile?.let { f ->
        AlertDialog(onDismissRequest={menuFile=null},title={Text(f.name)},text={
            Column { Text("Size: ${formatSize(f.length())}"); Text("Path: ${f.absolutePath}") }
        },confirmButton={TextButton(onClick={renameTarget=f;renameText=f.name;menuFile=null}){Text("Rename")}},
            dismissButton={TextButton(onClick={f.deleteRecursively();menuFile=null;refresh++}){Text("Delete")}})
    }
    renameTarget?.let { f ->
        AlertDialog(onDismissRequest={renameTarget=null},title={Text("Rename file")},
            text={OutlinedTextField(value=renameText,onValueChange={renameText=it},singleLine=true)},
            confirmButton={TextButton(onClick={ val dest=File(f.parentFile,renameText); if(renameText.isNotBlank()) f.renameTo(dest); renameTarget=null;refresh++ }){Text("Save")}},
            dismissButton={TextButton(onClick={renameTarget=null}){Text("Cancel")}})
    }
}

private fun fileIcon(file: File) = when(file.extension.lowercase()) {
    "jpg","jpeg","png","webp","gif","heic" -> Icons.Default.Image
    "mp4","mkv","mov","3gp" -> Icons.Default.VideoFile
    "mp3","wav","m4a","aac","flac" -> Icons.Default.AudioFile
    "pdf" -> Icons.Default.PictureAsPdf
    else -> Icons.Default.InsertDriveFile
}
private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024*1024 -> "%.1f KB".format(bytes/1024.0)
    bytes < 1024L*1024*1024 -> "%.1f MB".format(bytes/(1024.0*1024))
    else -> "%.1f GB".format(bytes/(1024.0*1024*1024))
}
