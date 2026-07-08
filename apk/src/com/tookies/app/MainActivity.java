package com.tookies.app;
import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
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
webView.setWebViewClient(new WebViewClient());
webView.setWebChromeClient(new WebChromeClient());
webView.loadUrl("file:///android_asset/index.html");
setContentView(webView);
}
@Override
public void onBackPressed() {
if(webView != null && webView.canGoBack()) {
webView.goBack();
}
// else: stay on current page (back is handled by in-app back button)
}
}