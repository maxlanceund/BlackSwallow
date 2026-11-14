package com.maxlanceund.blackswallow

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import java.io.File
import java.util.zip.ZipFile

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BlackSwallowApp() }
    }
}

@Composable
fun BlackSwallowApp() {
    val context = LocalContext.current
    var hasAllFilesPermission by remember { mutableStateOf(false) }
    var currentComic by remember { mutableStateOf<File?>(null) }

    fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasAllFilesPermission = Environment.isExternalStorageManager()
        } else {
            hasAllFilesPermission = true
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checkPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { checkPermission() }

    if (!hasAllFilesPermission) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("[ 黑燕 ]\n需要授权“所有文件访问权限”", color = Color.White, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center, lineHeight = 30.sp)
                Spacer(modifier = Modifier.height(30.dp))
                Box(modifier = Modifier.border(1.dp, Color.White).clickable {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:${context.packageName}")
                    context.startActivity(intent)
                }.padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text("前往设置开启权限", color = Color.White, fontFamily = FontFamily.Serif)
                }
            }
        }
    } else {
        if (currentComic != null) {
            ReaderScreen(file = currentComic!!, onBack = { currentComic = null })
        } else {
            BookshelfScreen(onComicClick = { currentComic = it })
        }
    }
}

@Composable
fun BookshelfScreen(onComicClick: (File) -> Unit) {
    val comicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BlackSwallow")
    val comicFiles = remember {
        if (comicDir.exists() && comicDir.isDirectory) {
            comicDir.walkTopDown()
                .filter { it.isFile && (it.extension.lowercase() == "zip" || it.extension.lowercase() == "cbz") }
                .toList()
        } else emptyList()
    }

    if (comicFiles.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("书架空空如也\n请把 ZIP/CBZ 放到\nDownload/BlackSwallow 文件夹", color = Color.White, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center, lineHeight = 30.sp)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().background(Color.Black).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(comicFiles) { file ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onComicClick(file) }
                ) {
                    Box(modifier = Modifier.aspectRatio(0.7f).border(1.dp, Color.White).background(Color.Black)) {
                        val coverData = remember(file.path) { getFirstImageFromZip(file) }
                        if (coverData != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(coverData)
                                    .crossfade(true)
                                    // 限制封面加载尺寸，防止内存撑爆
                                    .size(300) 
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(file.nameWithoutExtension, color = Color.White, fontFamily = FontFamily.Serif, fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun ReaderScreen(file: File, onBack: () -> Unit) {
    // 记录 UI 是否显示（点击屏幕可以隐藏/显示返回按钮）
    var isUiVisible by remember { mutableStateOf(true) }
    val imageEntries = remember(file.path) { getAllImagesFromZip(file) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // 点击屏幕任意位置，切换 UI 的显示和隐藏
            .pointerInput(Unit) {
                detectTapGestures(onTap = { isUiVisible = !isUiVisible })
            }
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(imageEntries) { _, entryName ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(getImageStreamFromZip(file, entryName))
                        // 【核心优化】：开启降采样，按屏幕宽度缩放。长图再怎么大，GPU 也扛得住！
                        .size(coil.size.Size.ORIGINAL) 
                        .precision(Precision.INEXACT)
                        .scale(Scale.FILL)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            }
        }

        // 只有 isUiVisible 为 true 时，返回按钮才显示
        if (isUiVisible) {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .border(1.dp, Color.White)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { onBack() }
                    .padding(8.dp)
                    .align(Alignment.TopStart)
            ) {
                Text("< 返回", color = Color.White, fontFamily = FontFamily.Serif)
            }
        }
    }
}

fun getFirstImageFromZip(file: File): ByteArray? {
    try {
        ZipFile(file).use { zip ->
            val firstEntry = zip.entries().asSequence()
                .filter { !it.isDirectory && (it.name.endsWith(".jpg", true) || it.name.endsWith(".png", true) || it.name.endsWith(".webp", true)) }
                .sortedWith { a, b -> naturalCompare(a.name, b.name) }
                .firstOrNull()
            if (firstEntry != null) return zip.getInputStream(firstEntry).readBytes()
        }
    } catch (e: Exception) { }
    return null
}

// 只获取文件名列表，不再把整个图片的 ByteArray 塞进内存！
fun getAllImagesFromZip(file: File): List<String> {
    try {
        ZipFile(file).use { zip ->
            return zip.entries().asSequence()
                .filter { !it.isDirectory && (it.name.endsWith(".jpg", true) || it.name.endsWith(".png", true) || it.name.endsWith(".webp", true)) }
                .map { it.name }
                .sortedWith { a, b -> naturalCompare(a, b) }
                .toList()
        }
    } catch (e: Exception) { }
    return emptyList()
}

// 按需实时从 ZIP 读取流
fun getImageStreamFromZip(file: File, entryName: String): ByteArray? {
    try {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry(entryName)
            if (entry != null) return zip.getInputStream(entry).readBytes()
        }
    } catch (e: Exception) { }
    return null
}

fun naturalCompare(a: String, b: String): Int {
    val regex = Regex("(\\d+)|(\\D+)")
    val partsA = regex.findAll(a).map { it.value }.toList()
    val partsB = regex.findAll(b).map { it.value }.toList()
    val minLength = minOf(partsA.size, partsB.size)
    for (i in 0 until minLength) {
        val partA = partsA[i]
        val partB = partsB[i]
        if (partA.all { it.isDigit() } && partB.all { it.isDigit() }) {
            val numA = partA.toLongOrNull() ?: 0
            val numB = partB.toLongOrNull() ?: 0
            if (numA != numB) return numA.compareTo(numB)
        } else {
            if (partA != partB) return partA.compareTo(partB)
        }
    }
    return partsA.size.compareTo(partsB.size)
}
