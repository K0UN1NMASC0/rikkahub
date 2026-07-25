package me.rerere.rikkahub.ui.pages.home

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HomePage() {
    val loader: HomeDataLoader = koinInject()
    val context = LocalContext.current
    val bgColor = MaterialTheme.colorScheme.background.toArgb()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // 非同期でデータをロード
    LaunchedEffect(Unit) {
        loader.loadData(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home") },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = {
                        // リロード時にリモートデータも再取得
                        webViewRef?.reload()
                    }) {
                        Icon(HugeIcons.Refresh01, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(bgColor)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    addJavascriptInterface(HomeJsBridge(loader, ctx), "TulpaHome")
                    webViewClient = WebViewClient()
                    loadUrl("file:///android_asset/html/home.html")
                    webViewRef = this
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}
