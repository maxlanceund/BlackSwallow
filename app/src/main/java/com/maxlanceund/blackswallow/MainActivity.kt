package com.maxlanceund.blackswallow

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File

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

    // 检查是否拥有“所有文件访问权限”
    fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasAllFilesPermission = Environment.isExternalStorageManager()
        } else {
            hasAllFilesPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    // 每次回到页面都重新检查（因为用户可能刚去设置里授权了）
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
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "[ 黑燕 ]\n需要授权“所有文件访问权限”\n才能扫描本地漫画",
                    color = Color.White,
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center,
                    fontSize = 18.sp,
                    lineHeight = 30.sp
                )
                Spacer(modifier = Modifier.height(30.dp))
                // 一个极简的白框按钮
                Box(
                    modifier = Modifier
                        .border(1.dp, Color.White)
                        .clickable {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                intent.data = Uri.parse("package:${context.packageName}")
                                context.startActivity(intent)
                            } else {
                                // 老版本安卓直接申请
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                intent.data = Uri.parse("package:${context.packageName}")
                                context.startActivity(intent)
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text("前往设置开启权限", color = Color.White, fontFamily = FontFamily.Serif)
                }
            }
        }
    } else {
        BookshelfScreen()
    }
}

@Composable
fun BookshelfScreen() {
    val comicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BlackSwallow")
    
    // 【核心修复】：使用 walkTopDown() 递归扫描所有子文件夹！
    val comicFiles = remember {
        if (comicDir.exists() && comicDir.isDirectory) {
            comicDir.walkTopDown()
                .filter { it.isFile && (it.extension.lowercase() == "zip" || it.extension.lowercase() == "cbz") }
                .toList()
        } else {
            emptyList()
        }
    }

    if (comicFiles.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "书架空空如也\n请把 ZIP/CBZ 放到\nDownload/BlackSwallow 文件夹",
                color = Color.White,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
                lineHeight = 30.sp
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().background(Color.Black).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(comicFiles) { file ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(0.7f)
                            .border(1.dp, Color.White)
                            .background(Color.Black)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = file.nameWithoutExtension,
                        color = Color.White,
                        fontFamily = FontFamily.Serif,
                        fontSize = 12.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
