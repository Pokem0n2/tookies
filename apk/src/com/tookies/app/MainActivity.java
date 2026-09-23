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
import android.webkit.ValueCallback;
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
webView.addJavascriptInterface(new Bridge(this), "Android");
webView.setWebViewClient(new WebViewClient());
webView.setWebChromeClient(new WebChromeClient());
webView.loadUrl("file:///android_asset/index.html");
setContentView(webView);
}
@Override
public void onBackPressed() {
// 计算器(xCalc)页面：优先让页面收起面板/取消待定运算符，再 WebView 后退
// 注：ValueCallback 用 raw type 实现（onReceiveValue(Object)），
//     避开本机 d8 8.2.2 对泛型 Signature 属性的 NPE 崩溃（类文件版本 65）。
final String req = "window.__tydligBack ? window.__tydligBack() : false";
webView.evaluateJavascript(req, new BackHandler(webView));
}
/** 返回键回调：页面未处理时 WebView 后退（回到主页）。 */
static class BackHandler implements ValueCallback {
private final WebView webView;
BackHandler(WebView webView) { this.webView = webView; }
@Override
public void onReceiveValue(Object result) {
String r = (result == null) ? null : String.valueOf(result);
if (!"true".equals(r)) {
if (webView.canGoBack()) webView.goBack();
// else: stay on current page (back is handled by in-app back button)
}
}
}
/** 提供给页面 JS 的原生能力：系统分享、Toast（xCalc 导出画布用）。 */
static class Bridge {
private final MainActivity activity;
Bridge(MainActivity activity) { this.activity = activity; }
@JavascriptInterface
public void share(String text) {
Intent send = new Intent(Intent.ACTION_SEND);
send.setType("text/plain");
send.putExtra(Intent.EXTRA_TEXT, text);
activity.startActivity(Intent.createChooser(send, "分享 xCalc 数据"));
}
@JavascriptInterface
public void toast(final String msg) {
activity.runOnUiThread(new ToastRunner(activity, msg));
}
}
/** Toast 须在 UI 线程显示。 */
static class ToastRunner implements Runnable {
private final MainActivity activity;
private final String msg;
ToastRunner(MainActivity activity, String msg) { this.activity = activity; this.msg = msg; }
@Override
public void run() {
Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
}
}
}
