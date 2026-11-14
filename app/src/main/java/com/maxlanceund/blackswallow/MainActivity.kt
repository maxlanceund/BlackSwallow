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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookshelfScreen(onComicClick: (File) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("BlackSwallowPrefs", Context.MODE_PRIVATE) }
    
    var refreshTrigger by remember { mutableStateOf(0) }
    var selectedCategory by remember { mutableStateOf("全部") }
    
    // 移动与管理的弹窗状态
    var comicToMove by remember { mutableStateOf<ComicItem?>(null) }
    var showManagement by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

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

    val physicalCategories = remember(allComics) {
        allComics.map { it.category }.distinct().sorted().filter { it != "未分类" }
    }

    // 加入“未分类”用于管理面板
    val allManageableCategories = remember(physicalCategories) {
        mutableListOf<String>().apply {
            addAll(physicalCategories)
            add("未分类")
        }
    }

    // 首页展示的标签
    val visibleCategories = remember(refreshTrigger) {
        mutableListOf("全部").apply {
            allManageableCategories.forEach { cat ->
                if (prefs.getBoolean("cat_visible_$cat", true)) {
                    add(cat)
                }
            }
        }
    }

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
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(cat, color = if (isSelected) Color.White else Color.Gray, fontFamily = FontFamily.Serif, fontSize = 14.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // 统一的分类管理入口
                    Box(
                        modifier = Modifier
                            .border(1.dp, Color.White)
                            .clickable { showManagement = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("管理", color = Color.White, fontFamily = FontFamily.Serif, fontSize = 12.sp)
                    }
                }
            }

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
                                .combinedClickable(
                                    onClick = { onComicClick(item.file) },
                                    onLongClick = { comicToMove = item; newCategoryName = "" }
                                )
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

        // 移动漫画弹窗
        if (comicToMove != null) {
            Dialog(onDismissRequest = { comicToMove = null }) {
                Column(
                    modifier = Modifier
                        .border(1.dp, Color.White)
                        .background(Color.Black)
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("移动至分类", color = Color.White, fontFamily = FontFamily.Serif, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    physicalCategories.forEach { cat ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    moveComic(comicToMove!!.file, comicDir, cat) {
                                        comicToMove = null
                                        refreshTrigger++
                                    }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(cat, color = Color.White, fontFamily = FontFamily.Serif)
                        }
                    }

                    if (comicToMove?.category != "未分类") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    moveComic(comicToMove!!.file, comicDir, "") {
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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp).background(Color.DarkGray), thickness = 1.dp)
                    
                    Text("新建分类并移动", color = Color.Gray, fontFamily = FontFamily.Serif, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    BasicTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center, fontSize = 14.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.White)
                            .padding(12.dp),
                        decorationBox = { innerTextField ->
                            if (newCategoryName.isEmpty()) Text("输入新分类名字...", color = Color.DarkGray, fontFamily = FontFamily.Serif, fontSize = 14.sp)
                            innerTextField()
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .border(1.dp, Color.White)
                            .clickable {
                                if (newCategoryName.isNotBlank()) {
                                    moveComic(comicToMove!!.file, comicDir, newCategoryName.trim()) {
                                        comicToMove = null
                                        refreshTrigger++
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("确认新建并移动", color = Color.White, fontFamily = FontFamily.Serif, fontSize = 12.sp)
                    }
                }
            }
        }

        // 分类管理弹窗
        if (showManagement) {
            Dialog(onDismissRequest = { showManagement = false }) {
                Column(
                    modifier = Modifier
                        .border(1.dp, Color.White)
                        .background(Color.Black)
                        .padding(24.dp)
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("分类管理", color = Color.White, fontFamily = FontFamily.Serif, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    allManageableCategories.forEach { cat ->
                        val isVisible = prefs.getBoolean("cat_visible_$cat", true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cat, color = Color.White, fontFamily = FontFamily.Serif)
                            Box(
                                modifier = Modifier
                                    .border(1.dp, if (isVisible) Color.White else Color.DarkGray)
                                    .clickable {
                                        val newVisible = !isVisible
                                        prefs.edit().putBoolean("cat_visible_$cat", newVisible).apply()
                                        // 触发一次重组刷新
                                        refreshTrigger++ 
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    if (isVisible) "[ 显示 ]" else "[ 隐藏 ]",
                                    color = if (isVisible) Color.White else Color.Gray,
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .border(1.dp, Color.White)
                            .clickable { showManagement = false }
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text("完成", color = Color.White, fontFamily = FontFamily.Serif)
                    }
                }
            }
        }
    }
}

fun moveComic(sourceFile: File, rootDir: File, targetCategory: String, onSuccess: () -> Unit) {
    val targetDir = if (targetCategory.isEmpty()) rootDir else File(rootDir, targetCategory)
    if (!targetDir.exists()) targetDir.mkdirs()
    val targetFile = File(targetDir, sourceFile.name)
    if (sourceFile.renameTo(targetFile)) {
        onSuccess()
    }
}

@Composable
fun ReaderScreen(file: File, onBack: () -> Unit) {
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
EOFcat << 'EOF' > app/src/main/java/com/maxlanceund/blackswallow/MainActivity.kt
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookshelfScreen(onComicClick: (File) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("BlackSwallowPrefs", Context.MODE_PRIVATE) }
    
    var refreshTrigger by remember { mutableStateOf(0) }
    var selectedCategory by remember { mutableStateOf("全部") }
    
    // 移动与管理的弹窗状态
    var comicToMove by remember { mutableStateOf<ComicItem?>(null) }
    var showManagement by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

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

    val physicalCategories = remember(allComics) {
        allComics.map { it.category }.distinct().sorted().filter { it != "未分类" }
    }

    // 加入“未分类”用于管理面板
    val allManageableCategories = remember(physicalCategories) {
        mutableListOf<String>().apply {
            addAll(physicalCategories)
            add("未分类")
        }
    }

    // 首页展示的标签
    val visibleCategories = remember(refreshTrigger) {
        mutableListOf("全部").apply {
            allManageableCategories.forEach { cat ->
                if (prefs.getBoolean("cat_visible_$cat", true)) {
                    add(cat)
                }
            }
        }
    }

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
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(cat, color = if (isSelected) Color.White else Color.Gray, fontFamily = FontFamily.Serif, fontSize = 14.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // 统一的分类管理入口
                    Box(
                        modifier = Modifier
                            .border(1.dp, Color.White)
                            .clickable { showManagement = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("管理", color = Color.White, fontFamily = FontFamily.Serif, fontSize = 12.sp)
                    }
                }
            }

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
                                .combinedClickable(
                                    onClick = { onComicClick(item.file) },
                                    onLongClick = { comicToMove = item; newCategoryName = "" }
                                )
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

        // 移动漫画弹窗
        if (comicToMove != null) {
            Dialog(onDismissRequest = { comicToMove = null }) {
                Column(
                    modifier = Modifier
                        .border(1.dp, Color.White)
                        .background(Color.Black)
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("移动至分类", color = Color.White, fontFamily = FontFamily.Serif, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    physicalCategories.forEach { cat ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    moveComic(comicToMove!!.file, comicDir, cat) {
                                        comicToMove = null
                                        refreshTrigger++
                                    }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(cat, color = Color.White, fontFamily = FontFamily.Serif)
                        }
                    }

                    if (comicToMove?.category != "未分类") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    moveComic(comicToMove!!.file, comicDir, "") {
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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp).background(Color.DarkGray), thickness = 1.dp)
                    
                    Text("新建分类并移动", color = Color.Gray, fontFamily = FontFamily.Serif, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    BasicTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center, fontSize = 14.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.White)
                            .padding(12.dp),
                        decorationBox = { innerTextField ->
                            if (newCategoryName.isEmpty()) Text("输入新分类名字...", color = Color.DarkGray, fontFamily = FontFamily.Serif, fontSize = 14.sp)
                            innerTextField()
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .border(1.dp, Color.White)
                            .clickable {
                                if (newCategoryName.isNotBlank()) {
                                    moveComic(comicToMove!!.file, comicDir, newCategoryName.trim()) {
                                        comicToMove = null
                                        refreshTrigger++
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("确认新建并移动", color = Color.White, fontFamily = FontFamily.Serif, fontSize = 12.sp)
                    }
                }
            }
        }

        // 分类管理弹窗
        if (showManagement) {
            Dialog(onDismissRequest = { showManagement = false }) {
                Column(
                    modifier = Modifier
                        .border(1.dp, Color.White)
                        .background(Color.Black)
                        .padding(24.dp)
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("分类管理", color = Color.White, fontFamily = FontFamily.Serif, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    allManageableCategories.forEach { cat ->
                        val isVisible = prefs.getBoolean("cat_visible_$cat", true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cat, color = Color.White, fontFamily = FontFamily.Serif)
                            Box(
                                modifier = Modifier
                                    .border(1.dp, if (isVisible) Color.White else Color.DarkGray)
                                    .clickable {
                                        val newVisible = !isVisible
                                        prefs.edit().putBoolean("cat_visible_$cat", newVisible).apply()
                                        // 触发一次重组刷新
                                        refreshTrigger++ 
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    if (isVisible) "[ 显示 ]" else "[ 隐藏 ]",
                                    color = if (isVisible) Color.White else Color.Gray,
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .border(1.dp, Color.White)
                            .clickable { showManagement = false }
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text("完成", color = Color.White, fontFamily = FontFamily.Serif)
                    }
                }
            }
        }
    }
}

fun moveComic(sourceFile: File, rootDir: File, targetCategory: String, onSuccess: () -> Unit) {
    val targetDir = if (targetCategory.isEmpty()) rootDir else File(rootDir, targetCategory)
    if (!targetDir.exists()) targetDir.mkdirs()
    val targetFile = File(targetDir, sourceFile.name)
    if (sourceFile.renameTo(targetFile)) {
        onSuccess()
    }
}

@Composable
fun ReaderScreen(file: File, onBack: () -> Unit) {
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
