package com.maxlanceund.blackswallow

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

// 定义阅读模式枚举
enum class ReadMode {
    VERTICAL_SEAMLESS, // 垂直无缝
    VERTICAL_GAP,      // 垂直有黑边
    HORIZONTAL         // 水平翻页
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
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
    var isUiVisible by remember { mutableStateOf(true) }
    val imageEntries = remember(file.path) { getAllImagesFromZip(file) }
    val context = LocalContext.current
    
    // 默认无缝垂直模式
    var readMode by remember { mutableStateOf(ReadMode.VERTICAL_SEAMLESS) }

    DisposableEffect(Unit) {
        val window = (context as ComponentActivity).window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onTap = { isUiVisible = !isUiVisible }) }
    ) {
        when (readMode) {
            ReadMode.VERTICAL_SEAMLESS -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(imageEntries) { _, entryName ->
                        TiledImageItem(file = file, entryName = entryName)
                    }
                }
            }
            ReadMode.VERTICAL_GAP -> {
                // 【新增】：上下滚动，但加入 8dp 的黑边缝隙
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(imageEntries) { _, entryName ->
                        Box(modifier = Modifier.padding(vertical = 8.dp)) { // 这里产生黑边
                            TiledImageItem(file = file, entryName = entryName)
                        }
                    }
                }
            }
            ReadMode.HORIZONTAL -> {
                val pagerState = rememberPagerState(pageCount = { imageEntries.size })
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val bytes = remember(imageEntries[page]) { getImageStreamFromZip(file, imageEntries[page]) }
                    if (bytes != null) {
                        AsyncImage(
                            model = bytes,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit 
                        )
                    }
                }
            }
        }

        // 悬浮菜单
        if (isUiVisible) {
            Column(
                modifier = Modifier
                    .padding(top = 40.dp, start = 16.dp)
                    .align(Alignment.TopStart)
            ) {
                Box(
                    modifier = Modifier
                        .border(1.dp, Color.White)
                        .background(Color.Black.copy(alpha = 0.8f))
                        .clickable { onBack() }
                        .padding(8.dp)
                ) {
                    Text("< 返回", color = Color.White, fontFamily = FontFamily.Serif)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 切换模式按钮
                Box(
                    modifier = Modifier
                        .border(1.dp, Color.White)
                        .background(Color.Black.copy(alpha = 0.8f))
                        .clickable { 
                            // 循环切换三种模式
                            readMode = when(readMode) {
                                ReadMode.VERTICAL_SEAMLESS -> ReadMode.VERTICAL_GAP
                                ReadMode.VERTICAL_GAP -> ReadMode.HORIZONTAL
                                ReadMode.HORIZONTAL -> ReadMode.VERTICAL_SEAMLESS
                            }
                        }
                        .padding(8.dp)
                ) {
                    Text(
                        text = when(readMode) {
                            ReadMode.VERTICAL_SEAMLESS -> "模式: 无缝"
                            ReadMode.VERTICAL_GAP -> "模式: 连页"
                            ReadMode.HORIZONTAL -> "模式: 翻页"
                        }, 
                        color = Color.White, 
                        fontFamily = FontFamily.Serif
                    )
                }
            }
        }
    }
}

@Composable
fun TiledImageItem(file: File, entryName: String) {
    var tiles by remember { mutableStateOf<List<androidx.compose.ui.graphics.ImageBitmap>>(emptyList()) }
    
    LaunchedEffect(entryName) {
        withContext(Dispatchers.IO) {
            val bytes = getImageStreamFromZip(file, entryName)
            if (bytes != null) {
                val decodedTiles = splitLargeImage(bytes)
                withContext(Dispatchers.Main) {
                    tiles = decodedTiles
                }
            }
        }
    }

    if (tiles.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(1000.dp).background(Color.Black))
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            tiles.forEach { imageBitmap ->
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}

fun splitLargeImage(bytes: ByteArray): List<androidx.compose.ui.graphics.ImageBitmap> {
    val result = mutableListOf<androidx.compose.ui.graphics.ImageBitmap>()
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    
    val width = options.outWidth
    val height = options.outHeight
    
    if (height <= 4096) {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bmp != null) result.add(bmp.asImageBitmap())
        return result
    }

    try {
        val decoder = BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
        val tileHeight = 2048
        var y = 0
        while (y < height) {
            val h = minOf(tileHeight, height - y)
            val rect = Rect(0, y, width, y + h)
            val bitmap = decoder?.decodeRegion(rect, null)
            if (bitmap != null) {
                result.add(bitmap.asImageBitmap())
            }
            y += tileHeight
        }
        decoder?.recycle()
    } catch (e: Exception) {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bmp != null) result.add(bmp.asImageBitmap())
    }
    
    return result
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
