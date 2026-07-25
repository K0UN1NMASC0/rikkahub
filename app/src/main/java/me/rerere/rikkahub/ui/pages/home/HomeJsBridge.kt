package me.rerere.rikkahub.ui.pages.home

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface

class HomeJsBridge(
    private val loader: HomeDataLoader,
    private val context: Context,
) {
    @JavascriptInterface
    fun getData(): String {
        return loader.currentJson(context)
    }

    @JavascriptInterface
    fun log(message: String) {
        Log.d("TulpaHome", message)
    }
}
