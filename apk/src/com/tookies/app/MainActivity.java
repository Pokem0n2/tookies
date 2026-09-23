package com.tookies.app;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
public class MainActivity extends Activity {
private WebView webView;
@Override
protected void onCreate(Bundle savedInstanceState) {
super.onCreate(savedInstanceState);
requestWindowFeature(Window.FEATURE_NO_TITLE);
getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
WindowManager.LayoutParams.FLAG_FULLSCREEN);
webView = new WebView(this);
WebSettings ws = webView.getSettings();
ws.setJavaScriptEnabled(true);
ws.setDomStorageEnabled(true);
ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
ws.setAllowFileAccess(true);
ws.setUseWideViewPort(true);
ws.setLoadWithOverviewMode(true);
ws.setMediaPlaybackRequiresUserGesture(false);
ws.setTextZoom(100);
webView.addJavascriptInterface(new Bridge(), "Android");
webView.setWebViewClient(new WebViewClient());
webView.setWebChromeClient(new WebChromeClient());
webView.loadUrl("file:///android_asset/index.html");
setContentView(webView);
}
@Override
public void onBackPressed() {
// 计算器(xCalc)页面：优先让页面收起面板/取消待定运算符
final String req = "window.__tydligBack ? window.__tydligBack() : false";
webView.evaluateJavascript(req, new android.webkit.ValueCallback<String>() {
@Override
public void onReceiveValue(String result) {
if (!"true".equals(result)) {
if (webView.canGoBack()) webView.goBack();
// else: stay on current page (back is handled by in-app back button)
}
}
});
}
/** 提供给页面 JS 的原生能力：系统分享、Toast（xCalc 导出画布用）。 */
private class Bridge {
@JavascriptInterface
public void share(String text) {
Intent send = new Intent(Intent.ACTION_SEND);
send.setType("text/plain");
send.putExtra(Intent.EXTRA_TEXT, text);
startActivity(Intent.createChooser(send, "分享 xCalc 数据"));
}
@JavascriptInterface
public void toast(final String msg) {
runOnUiThread(new Runnable() {
@Override
public void run() {
Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
}
});
}
}
}