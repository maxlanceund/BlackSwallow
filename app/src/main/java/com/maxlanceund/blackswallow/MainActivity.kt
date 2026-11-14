package com.maxlanceund.blackswallow

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BlackSwallowApp()
        }
    }
}

@Composable
fun BlackSwallowApp() {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasPermission = isGranted }
    )

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            launcher.launch(permission)
        }
    }

    if (hasPermission) {
        BookshelfScreen()
    } else {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "[ 黑燕 ]\n需要文件权限才能阅读",
                color = Color.White,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
                fontSize = 20.sp
            )
        }
    }
}

@Composable
fun BookshelfScreen() {
    // 读取 Download/BlackSwallow 文件夹
    val comicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BlackSwallow")
    
    // 扫描里面的 zip/cbz 文件
    val comicFiles = remember {
        if (comicDir.exists() && comicDir.isDirectory) {
            comicDir.listFiles { file ->
                file.extension.lowercase() == "zip" || file.extension.lowercase() == "cbz"
            }?.toList() ?: emptyList()
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
        // 如果扫描到文件，显示极简黑白网格书架
        LazyVerticalGrid(
            columns = GridCells.Fixed(3), // 一排 3 本
            modifier = Modifier.fillMaxSize().background(Color.Black).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(comicFiles) { file ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 因为还没写解压逻辑，先用一个纯黑色的方块加上白边框代替封面
                    Box(
                        modifier = Modifier
                            .aspectRatio(0.7f) // 漫画书的比例
                            .border(1.dp, Color.White) // 极简白边框
                            .background(Color.Black)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 极其极简的书名（用文件名代替）
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
