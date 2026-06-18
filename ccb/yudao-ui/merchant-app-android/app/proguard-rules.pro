# WebView JS 接口保留（如后续加 @JavascriptInterface 需保留对应类）
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
