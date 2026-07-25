package me.rerere.rikkahub.ui.pages.home

import android.webkit.JavascriptInterface

/**
 * JavaScript bridge for Tulpa Home WebView.
 * HTMLからは window.TulpaHome.getData() で呼び出せる。
 */
class HomeJsBridge(private val loader: HomeDataLoader) {

    @JavascriptInterface
    fun getData(): String = loader.currentJson()

    @JavascriptInterface
    fun log(msg: String) {
        android.util.Log.d("TulpaHome", "JS: $msg")
    }
}
