package com.maxlanceund.blackswallow

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
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

enum class ReadMode {
    VERTICAL_SEAMLESS,
    VERTICAL_GAP
}

data class ComicItem(val file: File, val category: String)

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
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("BlackSwallowPrefs", Context.MODE_PRIVATE) }
    
    // 刷新触发器（移动文件后用来重新扫描）
    var refreshTrigger by remember { mutableStateOf(0) }
    
    var showHiddenCategories by remember { mutableStateOf(prefs.getBoolean("show_hidden_categories", false)) }
    var selectedCategory by remember { mutableStateOf("全部") }

    // 弹窗状态：要移动的漫画
    var comicToMove by remember { mutableStateOf<ComicItem?>(null) }

    val comicDir = remember { File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BlackSwallow") }
    
    val allComics = remember(refreshTrigger) {
        val list = mutableListOf<ComicItem>()
        if (comicDir.exists() && comicDir.isDirectory) {
            comicDir.walkTopDown().forEach { file ->
                if (file.isFile && (file.extension.lowercase() == "zip" || file.extension.lowercase() == "cbz")) {
                    val relativePath = file.absolutePath.substring(comicDir.absolutePath.length).trimStart(File.separatorChar)
                    val category = if (relativePath.contains(File.separator)) {
                        relativePath.substringBefore(File.separator)
                    } else {
                        "未分类"
                    }
                    list.add(ComicItem(file, category))
                }
            }
        }
        list
    }

    // 提取当前所有真实的物理文件夹分类
    val physicalCategories = remember(allComics) {
        allComics.map { it.category }.distinct().sorted().filter { it != "未分类" }
    }

    val allCategories = remember(physicalCategories) {
        mutableListOf("全部").apply { addAll(physicalCategories) }
    }

    val categoryVisibility = remember(allCategories) {
        mutableStateMapOf<String, Boolean>().apply {
            allCategories.forEach { cat ->
                put(cat, prefs.getBoolean("cat_visible_$cat", true))
            }
        }
    }

    val visibleCategories = if (showHiddenCategories) allCategories else allCategories.filter { categoryVisibility[it] != false }

    LaunchedEffect(visibleCategories) {
        if (selectedCategory !in visibleCategories) selectedCategory = "全部"
    }

    val filteredComics = if (selectedCategory == "全部") allComics else allComics.filter { it.category == selectedCategory }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 顶部栏
            if (allComics.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(count = visibleCategories.size) { index ->
                            val cat = visibleCategories[index]
                            val isSelected = cat == selectedCategory
                            Box(
                                modifier = Modifier
                                    .border(1.dp, if (isSelected) Color.White else Color.Transparent)
                                    .clickable { selectedCategory = cat }
                                    .pointerInput(cat) {
                                        detectTapGestures(onLongPress = {
                                            val current = categoryVisibility[cat] ?: true
                                            categoryVisibility[cat] = !current
                                            prefs.edit().putBoolean("cat_visible_$cat", !current).apply()
                                        })
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(cat, color = if (isSelected) Color.White else Color.Gray, fontFamily = FontFamily.Serif, fontSize = 14.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .border(1.dp, if (showHiddenCategories) Color.White else Color.Transparent)
                            .clickable {
                                showHiddenCategories = !showHiddenCategories
                                prefs.edit().putBoolean("show_hidden_categories", showHiddenCategories).apply()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(if (showHiddenCategories) "隐藏分类: 开" else "隐藏分类: 关", color = if (showHiddenCategories) Color.White else Color.Gray, fontFamily = FontFamily.Serif, fontSize = 12.sp)
                    }
                }
            }

            // 漫画网格
            if (allComics.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("书架空空如也\n请把 ZIP/CBZ 放到\nDownload/BlackSwallow 文件夹", color = Color.White, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center, lineHeight = 30.sp)
                }
            } else if (filteredComics.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("该分类下暂无漫画", color = Color.Gray, fontFamily = FontFamily.Serif)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    items(count = filteredComics.size) { index ->
                        val item = filteredComics[index]
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { onComicClick(item.file) }
                                // 【核心功能】：长按漫画封面，弹出分类选择
                                .pointerInput(item.file.path) {
                                    detectTapGestures(onLongPress = { comicToMove = item })
                                }
                        ) {
                            Box(modifier = Modifier.aspectRatio(0.7f).border(1.dp, Color.White).background(Color.Black)) {
                                val coverData = remember(item.file.path) { getFirstImageFromZip(item.file) }
                                if (coverData != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current).data(coverData).crossfade(true).size(300).build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(item.file.nameWithoutExtension, color = Color.White, fontFamily = FontFamily.Serif, fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        // 【移动分类的极简弹窗】
        if (comicToMove != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { comicToMove = null }, // 点击空白处关闭
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .border(1.dp, Color.White)
                        .background(Color.Black)
                        .padding(24.dp)
                        .width(IntrinsicSize.Max),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("移动到分类", color = Color.White, fontFamily = FontFamily.Serif, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 现有的所有物理分类
                    physicalCategories.forEach { cat ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // 【物理移动逻辑】
                                    val targetDir = File(comicDir, cat)
                                    if (!targetDir.exists()) targetDir.mkdirs()
                                    val targetFile = File(targetDir, comicToMove!!.file.name)
                                    if (comicToMove!!.file.renameTo(targetFile)) {
                                        comicToMove = null
                                        refreshTrigger++ // 移动成功，触发刷新
                                    }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(cat, color = Color.White, fontFamily = FontFamily.Serif)
                        }
                    }

                    // 如果需要移回根目录（未分类）
                    if (comicToMove?.category != "未分类") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val targetFile = File(comicDir, comicToMove!!.file.name)
                                    if (comicToMove!!.file.renameTo(targetFile)) {
                                        comicToMove = null
                                        refreshTrigger++
                                    }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("移出分类 (未分类)", color = Color.Gray, fontFamily = FontFamily.Serif)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReaderScreen(file: File, onBack: () -> Unit) {
    // 阅读器代码保持完美不变
    var isUiVisible by remember { mutableStateOf(true) }
    val imageEntries = remember(file.path) { getAllImagesFromZip(file) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("BlackSwallowPrefs", Context.MODE_PRIVATE) }
    
    var readMode by remember { mutableStateOf(if (prefs.getString("read_mode", "SEAMLESS") == "GAP") ReadMode.VERTICAL_GAP else ReadMode.VERTICAL_SEAMLESS) }
    var isFullScreen by remember { mutableStateOf(prefs.getBoolean("full_screen", true)) }

    DisposableEffect(isFullScreen) {
        val window = (context as ComponentActivity).window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (isFullScreen) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
        onDispose {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(if (!isFullScreen) Modifier.statusBarsPadding() else Modifier)
            .pointerInput(Unit) { detectTapGestures(onTap = { isUiVisible = !isUiVisible }) }
    ) {
        when (readMode) {
            ReadMode.VERTICAL_SEAMLESS -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(imageEntries) { _, entryName -> TiledImageItem(file = file, entryName = entryName) }
                }
            }
            ReadMode.VERTICAL_GAP -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(imageEntries) { _, entryName ->
                        Box(modifier = Modifier.padding(vertical = 8.dp)) { TiledImageItem(file = file, entryName = entryName) }
                    }
                }
            }
        }

        if (isUiVisible) {
            Column(modifier = Modifier.padding(top = if (isFullScreen) 40.dp else 16.dp, start = 16.dp).align(Alignment.TopStart)) {
                Box(modifier = Modifier.border(1.dp, Color.White).background(Color.Black.copy(alpha = 0.8f)).clickable { onBack() }.padding(8.dp)) {
                    Text("< 返回", color = Color.White, fontFamily = FontFamily.Serif)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier.border(1.dp, Color.White).background(Color.Black.copy(alpha = 0.8f)).clickable {
                        val newMode = if (readMode == ReadMode.VERTICAL_SEAMLESS) ReadMode.VERTICAL_GAP else ReadMode.VERTICAL_SEAMLESS
                        readMode = newMode
                        prefs.edit().putString("read_mode", if (newMode == ReadMode.VERTICAL_GAP) "GAP" else "SEAMLESS").apply()
                    }.padding(8.dp)
                ) {
                    Text(if (readMode == ReadMode.VERTICAL_SEAMLESS) "模式: 无缝" else "模式: 连页", color = Color.White, fontFamily = FontFamily.Serif)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier.border(1.dp, Color.White).background(Color.Black.copy(alpha = 0.8f)).clickable {
                        isFullScreen = !isFullScreen
                        prefs.edit().putBoolean("full_screen", isFullScreen).apply()
                    }.padding(8.dp)
                ) {
                    Text(if (isFullScreen) "全屏: 沉浸" else "全屏: 状态栏", color = Color.White, fontFamily = FontFamily.Serif)
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
                withContext(Dispatchers.Main) { tiles = decodedTiles }
            }
        }
    }
    if (tiles.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(1000.dp).background(Color.Black))
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            tiles.forEach { imageBitmap ->
                Image(bitmap = imageBitmap, contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
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
            if (bitmap != null) result.add(bitmap.asImageBitmap())
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
