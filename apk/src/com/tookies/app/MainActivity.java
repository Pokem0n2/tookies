package com.tookies.app;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStream;
import org.json.JSONArray;
import org.json.JSONObject;
public class MainActivity extends Activity {
private static final int FILE_REQ = 51001;
private ValueCallback mFileCallback;
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
webView.addJavascriptInterface(new SysInfoBridge(this), "NativeInfo");
webView.setWebViewClient(new WebViewClient());
// 文件选择器（GLB 预览"换模型"用）：API 21+ 未重写此方法时 <input type=file> 在 WebView 中无响应。
// 注：static 嵌套类 + raw ValueCallback（同 BackHandler），规避 d8 8.2.2 泛型 Signature/匿名类 NPE。
webView.setWebChromeClient(new FileChooserClient(this));
webView.loadUrl("file:///android_asset/index.html");
setContentView(webView);
}
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
if (requestCode == FILE_REQ) {
Uri[] uris = null;
if (resultCode == RESULT_OK && data != null && data.getData() != null) {
uris = new Uri[]{ data.getData() };
}
if (mFileCallback != null) {
mFileCallback.onReceiveValue(uris);
mFileCallback = null;
}
return;
}
super.onActivityResult(requestCode, resultCode, data);
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
static class BackHandler implements ValueCallback {private final WebView webView;
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
/** GLB 预览截图保存：WebView 内 <a download> 无效，原生侧落盘到相册（Pictures/Tookies）。
 *  API 29+ 用 MediaStore 无需存储权限；API 21-28 写公共 Pictures 也免权限（legacy external storage）。 */
@JavascriptInterface
public void saveImage(final String dataUrl) {
try {
final String b64;
if (dataUrl != null && dataUrl.startsWith("data:image/png;base64,")) {
b64 = dataUrl.substring("data:image/png;base64,".length());
} else { b64 = dataUrl; }
final byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
final android.content.ContentResolver cr = activity.getContentResolver();
final String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
.format(new java.util.Date());
if (Build.VERSION.SDK_INT >= 29) {
android.content.ContentValues cv = new android.content.ContentValues();
cv.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "poser_" + ts + ".png");
cv.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
cv.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Tookies");
final Uri uri = cr.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
if (uri == null) throw new RuntimeException("MediaStore insert null");
OutputStream os = cr.openOutputStream(uri);
os.write(bytes); os.flush(); os.close();
} else {
File dir = new File(android.os.Environment
.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "Tookies");
if (!dir.exists()) dir.mkdirs();
File out = new File(dir, "poser_" + ts + ".png");
FileOutputStream fos = new FileOutputStream(out);
fos.write(bytes); fos.flush(); fos.close();
}
activity.runOnUiThread(new ToastRunner(activity, "已保存截图 poser_" + ts + ".png"));
} catch (final Exception e) {
activity.runOnUiThread(new ToastRunner(activity, "截图保存失败: " + e.getMessage()));
}
}
/** GLB 预览 pose JSON 导出：与 saveImage 同路径规则（Pictures/Tookies 目录）。 */
@JavascriptInterface
public void saveTextFile(final String filename, final String text) {
try {
final String dirName = "Tookies";
final String safe = (filename == null || filename.length() == 0) ? "pose.json" : filename;
final String bytes = text == null ? "" : text;
final byte[] data = bytes.getBytes("UTF-8");
final android.content.ContentResolver cr = activity.getContentResolver();
if (Build.VERSION.SDK_INT >= 29) {
android.content.ContentValues cv = new android.content.ContentValues();
cv.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, safe);
cv.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "application/json");
cv.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/" + dirName);
final Uri uri = cr.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
if (uri == null) throw new RuntimeException("MediaStore insert null");
OutputStream os = cr.openOutputStream(uri);
os.write(data); os.flush(); os.close();
} else {
File dir = new File(android.os.Environment
.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), dirName);
if (!dir.exists()) dir.mkdirs();
File out = new File(dir, safe);
FileOutputStream fos = new FileOutputStream(out);
fos.write(data); fos.flush(); fos.close();
}
activity.runOnUiThread(new ToastRunner(activity, "已保存 " + safe));
} catch (final Exception e) {
activity.runOnUiThread(new ToastRunner(activity, "保存失败: " + e.getMessage()));
}
}
}
/** 系统信息桥（移植自 asi-z android-sys-info v0.8.0 DeviceBridge）：
 *  每方法返回 JSON 字符串，键缺失时 JS 侧显示「不可用」。
 *  全部 static 内部类 + 无泛型签名（d8 8.2.2 工具链坑，勿回退）。 */
static class SysInfoBridge {
private final Activity activity;
SysInfoBridge(Activity activity) { this.activity = activity; }

/** 应用自身版本（来自 PackageManager，与 APK 的 versionName 一致）。 */
@JavascriptInterface
public String getAppVersion() {
try {
return activity.getPackageManager()
.getPackageInfo(activity.getPackageName(), 0).versionName;
} catch (Exception e) {
return "0.0.0";
}
}

/** 设备识别：品牌 / 厂商 / 型号 / 内部代号 / 主板平台 / 设备昵称。 */
@JavascriptInterface
public String getDeviceInfo() {
JSONObject o = new JSONObject();
try {
o.put("brand", Build.BRAND);
o.put("manufacturer", Build.MANUFACTURER);
o.put("model", Build.MODEL);
o.put("device", Build.DEVICE);
o.put("board", Build.BOARD);
String name = Settings.Global.getString(
activity.getContentResolver(), "device_name");
o.put("deviceName", name == null ? "" : name);
} catch (Exception ignored) {
}
return o.toString();
}

/** 内存：总量 / 当前可用 / 阈值 / 低内存标志（ActivityManager.MemoryInfo）。 */
@JavascriptInterface
public String getMemoryInfo() {
JSONObject o = new JSONObject();
try {
ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
ActivityManager am = (ActivityManager) activity
.getSystemService(Context.ACTIVITY_SERVICE);
am.getMemoryInfo(mi);
o.put("total", mi.totalMem);
o.put("avail", mi.availMem);
o.put("threshold", mi.threshold);
o.put("low", mi.lowMemory);
} catch (Exception ignored) {
}
return o.toString();
}

/** 显示：分辨率 / 密度 / 刷新率 / 物理尺寸估算（WindowManager + DisplayMetrics）。 */
@JavascriptInterface
public String getDisplayInfo() {
JSONObject o = new JSONObject();
try {
DisplayMetrics rm = new DisplayMetrics();
activity.getWindowManager().getDefaultDisplay().getRealMetrics(rm);
o.put("width", rm.widthPixels);
o.put("height", rm.heightPixels);
o.put("densityDpi", rm.densityDpi);
o.put("density", rm.density);
o.put("xdpi", rm.xdpi);
o.put("ydpi", rm.ydpi);
float refresh = activity.getWindowManager()
.getDefaultDisplay().getRefreshRate();
o.put("refresh", refresh);
double inches = Math.sqrt(
Math.pow(rm.widthPixels / (double) rm.xdpi, 2)
+ Math.pow(rm.heightPixels / (double) rm.ydpi, 2));
o.put("inches", Math.round(inches * 100) / 100.0);
} catch (Exception ignored) {
}
return o.toString();
}

/** 存储容量：数据分区的总容量 / 可用 / 已用（StatFs）。 */
@JavascriptInterface
public String getStorageStat() {
JSONObject o = new JSONObject();
try {
File dataDir = activity.getFilesDir().getParentFile();
StatFs stat = new StatFs(dataDir.getAbsolutePath());
long total = stat.getTotalBytes();
long avail = stat.getAvailableBytes();
o.put("total", total);
o.put("avail", avail);
o.put("used", total - avail);
} catch (Exception ignored) {
}
return o.toString();
}

/** 系统：Android 版本 / API 等级 / 安全补丁 / 内核版本 / 构建指纹 / 启动时长。 */
@JavascriptInterface
public String getSystemInfo() {
JSONObject o = new JSONObject();
try {
o.put("release", Build.VERSION.RELEASE);
o.put("sdk", Build.VERSION.SDK_INT);
o.put("codename", Build.VERSION.CODENAME);
o.put("incremental", Build.VERSION.INCREMENTAL);
if (Build.VERSION.SDK_INT >= 23) {
o.put("securityPatch", Build.VERSION.SECURITY_PATCH);
}
o.put("kernel", System.getProperty("os.version"));
o.put("fingerprint", Build.FINGERPRINT);
o.put("host", Build.HOST);
o.put("tags", Build.TAGS);
o.put("bootUptime", SystemClock.elapsedRealtime());
o.put("buildTime", Build.TIME);
} catch (Exception ignored) {
}
return o.toString();
}

/** CPU：架构 / ABI 列表 / 核心数 / 各集群（按最高频率分桶 + MIDR 解码）。 */
@JavascriptInterface
public String getCpuInfo() {
JSONObject o = new JSONObject();
try {
o.put("abis", joinStrings(Build.SUPPORTED_ABIS));
String arch = Build.SUPPORTED_ABIS.length > 0
? Build.SUPPORTED_ABIS[0] : "";
o.put("arch", arch);
o.put("cores", Runtime.getRuntime().availableProcessors());
o.put("clusters", readCpuClusters());
} catch (Exception ignored) {
}
return o.toString();
}

/** 读 /sys/devices/system/cpu/，按「cpuinfo_max_freq」分桶聚合集群，
 *  并读取每个集群首个核心的 MIDR（regs/id/midr_el1）解码出核心型号。 */
private static JSONArray readCpuClusters() {
JSONArray arr = new JSONArray();
java.util.LinkedHashMap buckets = new java.util.LinkedHashMap();
for (int i = 0; i < 64; i++) {
File cpuDir = new File("/sys/devices/system/cpu/cpu" + i);
if (!cpuDir.isDirectory()) break;
String max = readOneLine(new File(cpuDir, "cpufreq/cpuinfo_max_freq"));
long m = -1;
if (max != null) {
try { m = Long.parseLong(max); } catch (NumberFormatException ignored) {}
}
java.util.ArrayList list = (java.util.ArrayList) buckets.get(m);
if (list == null) {
list = new java.util.ArrayList();
buckets.put(m, list);
}
list.add(i);
}
java.util.Iterator it = buckets.entrySet().iterator();
while (it.hasNext()) {
java.util.Map.Entry e = (java.util.Map.Entry) it.next();
java.util.ArrayList list = (java.util.ArrayList) e.getValue();
JSONObject c = new JSONObject();
try {
c.put("cpus", joinInts(list));
c.put("maxFreq", e.getKey());
int first = (Integer) list.get(0);
String midr = readOneLine(new File(
"/sys/devices/system/cpu/cpu" + first + "/regs/id/midr_el1"));
if (midr != null) {
c.put("midr", midr);
String core = decodeMidr(midr);
if (core != null) c.put("core", core);
}
} catch (Exception ignored) {
}
arr.put(c);
}
return arr;
}

/** MIDR_EL1 → 人类可读核心型号。字段布局见 ARM ARM (DDI 0487) MIDR 描述。 */
private static String decodeMidr(String midrHex) {
try {
long v = Long.parseUnsignedLong(midrHex.trim().replaceFirst("^0[xX]", ""), 16);
int implementer = (int) ((v >> 24) & 0xff);
int part = (int) ((v >> 4) & 0xfff);
String vendor;
switch (implementer) {
case 0x41: vendor = "ARM"; break;
case 0x51: vendor = "Qualcomm"; break;
case 0x53: vendor = "Samsung"; break;
case 0x48: vendor = "HiSilicon"; break;
case 0x4e: vendor = "NVIDIA"; break;
case 0x69: vendor = "Intel"; break;
default:   vendor = String.format("impl 0x%02x", implementer); break;
}
String core = armCoreName(part);
return core != null ? vendor + " " + core
: String.format("%s part 0x%03x", vendor, part);
} catch (Exception e) {
return null;
}
}

/** ARM 公版核心 part number（见各核心 TRM）；非公版（骁龙 X 系自研等）返回 null。 */
private static String armCoreName(int part) {
switch (part) {
case 0xd01: return "Cortex-A32";
case 0xd03: return "Cortex-A53";
case 0xd04: return "Cortex-A35";
case 0xd05: return "Cortex-A55";
case 0xd07: return "Cortex-A57";
case 0xd08: return "Cortex-A72";
case 0xd09: return "Cortex-A73";
case 0xd0a: return "Cortex-A75";
case 0xd0b: return "Cortex-A76";
case 0xd0c: return "Neoverse-N1";
case 0xd0d: return "Cortex-A77";
case 0xd0e: return "Cortex-A76AE";
case 0xd40: return "Neoverse-V1";
case 0xd41: return "Cortex-A78";
case 0xd44: return "Cortex-X1";
case 0xd46: return "Cortex-A510";
case 0xd47: return "Cortex-A710";
case 0xd48: return "Cortex-X2";
case 0xd49: return "Neoverse-N2";
case 0xd4d: return "Cortex-A715";
case 0xd4e: return "Cortex-X3";
case 0xd80: return "Cortex-A520";
case 0xd81: return "Cortex-A720";
case 0xd82: return "Cortex-X4";
case 0xd87: return "Cortex-A725";
case 0xd88: return "Cortex-X925";
default: return null;
}
}

/** GPU 信息：离屏 EGL surface 上调 glGetString 读渲染器与版本（见 GpuProbe）。 */
@JavascriptInterface
public String getGpuInfo() {
JSONObject o = new JSONObject();
try {
String[] gl = GpuProbe.query();
if (gl != null) {
o.put("renderer", gl[0]);
o.put("version", gl[1]);
}
} catch (Exception ignored) {
}
return o.toString();
}

private static String joinStrings(String[] arr) {
StringBuilder sb = new StringBuilder();
for (int i = 0; i < arr.length; i++) {
if (i > 0) sb.append(" / ");
sb.append(arr[i]);
}
return sb.toString();
}

private static String joinInts(java.util.ArrayList list) {
StringBuilder sb = new StringBuilder();
for (int i = 0; i < list.size(); i++) {
if (i > 0) sb.append(',');
sb.append(list.get(i));
}
return sb.toString();
}

/** 电池：电量 / 状态 / 健康 / 技术 / 电压 / 温度 / 设计容量（BATTERY_CHANGED 粘性广播）。 */
@JavascriptInterface
public String getBatteryInfo() {
JSONObject o = new JSONObject();
try {
IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
Intent i = activity.registerReceiver(null, f);
if (i != null) {
int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
if (level >= 0 && scale > 0) {
o.put("level", level * 100 / scale);
}
o.put("status", batteryStatusText(
i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)));
o.put("health", batteryHealthText(
i.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)));
String tech = i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
if (tech != null) o.put("technology", tech);
int volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
if (volt > 0) o.put("voltage", volt);
int temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
if (temp > 0) o.put("temperature", temp);
BatteryManager bm = (BatteryManager) activity
.getSystemService(Context.BATTERY_SERVICE);
long charge = bm.getLongProperty(
BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
if (charge > 0) o.put("chargeCounter", charge);
}
long design = i != null ? i.getLongExtra("design_capacity", -1) : -1;
if (design <= 0) design = readSysfsLong("/sys/class/power_supply/battery/charge_full_design");
if (design > 0) o.put("designCapacity", design);
long full = readBatteryExtra(i, "charge_full");
if (full <= 0) full = readSysfsLong("/sys/class/power_supply/battery/charge_full");
if (full > 0) o.put("fullCapacity", full);
} catch (Exception ignored) {
}
return o.toString();
}

private static long readBatteryExtra(Intent i, String extra) {
if (i == null) return -1;
try {
return i.getLongExtra(extra, -1);
} catch (Exception ignored) {
}
return -1;
}

private static long readSysfsLong(String path) {
String s = readOneLine(new File(path));
if (s == null) return -1;
try {
return Long.parseLong(s.trim());
} catch (NumberFormatException e) {
return -1;
}
}

private static String batteryStatusText(int status) {
switch (status) {
case BatteryManager.BATTERY_STATUS_CHARGING:     return "充电中";
case BatteryManager.BATTERY_STATUS_DISCHARGING:  return "放电中";
case BatteryManager.BATTERY_STATUS_FULL:         return "已充满";
case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "未充电";
default: return null;
}
}

private static String batteryHealthText(int health) {
switch (health) {
case BatteryManager.BATTERY_HEALTH_GOOD:         return "良好";
case BatteryManager.BATTERY_HEALTH_OVERHEAT:     return "过热";
case BatteryManager.BATTERY_HEALTH_DEAD:         return "已报废";
case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "过压";
case BatteryManager.BATTERY_HEALTH_COLD:         return "过冷";
default: return null;
}
}

/** 其他：传感器数量 / WebView 版本 / 语言时区。 */
@JavascriptInterface
public String getMiscInfo() {
JSONObject o = new JSONObject();
try {
SensorManager sm = (SensorManager) activity
.getSystemService(Context.SENSOR_SERVICE);
java.util.List sensors = sm.getSensorList(Sensor.TYPE_ALL);
o.put("sensorCount", sensors.size());
addSensor(o, "accel", Sensor.TYPE_ACCELEROMETER, sensors);
addSensor(o, "gyro", Sensor.TYPE_GYROSCOPE, sensors);
addSensor(o, "mag", Sensor.TYPE_MAGNETIC_FIELD, sensors);
addSensor(o, "light", Sensor.TYPE_LIGHT, sensors);

if (Build.VERSION.SDK_INT >= 26) {
android.content.pm.PackageInfo pkg = android.webkit.WebView.getCurrentWebViewPackage();
if (pkg != null) {
o.put("webview", pkg.packageName + " " + pkg.versionName);
}
} else {
String v = findWebViewVersionPre26();
if (v != null) o.put("webview", v);
}
o.put("locale", java.util.Locale.getDefault().toString());
o.put("timezone", java.util.TimeZone.getDefault().getID());
} catch (Exception ignored) {
}
return o.toString();
}

private void addSensor(JSONObject o, String key, int type,
java.util.List all) {
try {
java.util.Iterator it = all.iterator();
while (it.hasNext()) {
Sensor s = (Sensor) it.next();
if (s.getType() == type) {
o.put(key, s.getName());
return;
}
}
} catch (Exception ignored) {
}
}

/** API 21-25：从已知 WebView 提供包里翻版本号。 */
private String findWebViewVersionPre26() {
String[] pkgs = {
"com.google.android.webview",
"com.android.webview",
"com.android.chrome",
"com.sec.android.app.sbrowser",
};
for (String p : pkgs) {
try {
return p + " " + activity.getPackageManager()
.getPackageInfo(p, 0).versionName;
} catch (Exception ignored) {
}
}
return null;
}

/** 把整份报告文本写入系统剪贴板（JS 侧无法跨 file:// 源写剪贴板）。 */
@JavascriptInterface
public boolean copyToClipboard(String text) {
try {
android.content.ClipboardManager cm = (android.content.ClipboardManager)
activity.getSystemService(Context.CLIPBOARD_SERVICE);
cm.setPrimaryClip(android.content.ClipData.newPlainText(
"系统信息报告", text));
return true;
} catch (Exception e) {
return false;
}
}

/** 闪存类型探测（v0.8.0 三级回退，全部无权限可读）：
 *  ① ro.boot.boot_devices 系统属性：内核命令行传入的存储控制器设备路径，
 *     含 "ufshc" → UFS，含 "mmc" → eMMC（Android 8+ 普遍可读）
 *  ② /proc/partitions 块设备名：mmcblk* → eMMC，sd* → UFS(通用闪存层)，
 *     nvme* → NVMe
 *  ③ sysfs 直读细项（老 ROM 才可读，兼拿厂商/型号）
 *  SELinux 限制普通 app 读 /sys/block 与 /sys/class/ufshc，故前两级
 *  是 Android 8+ 真正走得通的路径。 */
@JavascriptInterface
public String getStorageInfo() {
JSONObject o = new JSONObject();
try {
detectFlashByBootProp(o);
if (o.length() == 0) detectFlashByPartitions(o);
if (o.length() == 0) detectUfs(o);
if (o.length() == 0) detectEmmc(o);
} catch (Exception ignored) {
}
return o.toString();
}

/** ① 解析 ro.boot.boot_devices / ro.boot.bootdevice 属性值。 */
private void detectFlashByBootProp(JSONObject o) throws Exception {
String[] props = { "ro.boot.boot_devices", "ro.boot.bootdevice" };
for (String p : props) {
String v = getSystemProp(p);
if (v == null || v.isEmpty()) continue;
o.put("source", p);
if (v.contains("ufshc")) {
o.put("type", "UFS");
} else if (v.contains("mmc")) {
o.put("type", "eMMC");
} else if (v.contains("nvme")) {
o.put("type", "NVMe");
}
o.put("detail", v);
return;
}
}

/** ② /proc/partitions 主块设备名判型（system_server 自 8 起仍可读）。 */
private void detectFlashByPartitions(JSONObject o) throws Exception {
String content = readFileBlock("/proc/partitions");
if (content == null) return;
o.put("source", "/proc/partitions");
java.util.regex.Matcher m = java.util.regex.Pattern
.compile("^\\s*\\d+\\s+\\d+\\s+(\\d+)\\s+(\\S+)$",
java.util.regex.Pattern.MULTILINE)
.matcher(content);
while (m.find()) {
String name = m.group(2);
if (name.matches("mmcblk\\d+")) {
o.put("type", "eMMC");
o.put("detail", name);
return;
}
if (name.equals("sda") || name.equals("sdb")) {
o.put("type", "UFS");
o.put("detail", name);
return;
}
if (name.startsWith("nvme0n")) {
o.put("type", "NVMe");
o.put("detail", name);
return;
}
}
}

/** SystemProperties 反射读取（隐藏 API，无需权限）。 */
private static String getSystemProp(String key) {
try {
Class sp = Class.forName("android.os.SystemProperties");
return (String) sp.getMethod("get", String.class).invoke(null, key);
} catch (Exception e) {
return null;
}
}

/** 读多行文件（/proc 用），失败返回 null。 */
private static String readFileBlock(String path) {
BufferedReader br = null;
StringBuilder sb = new StringBuilder();
try {
br = new BufferedReader(new FileReader(path));
char[] buf = new char[4096];
int n;
while ((n = br.read(buf)) > 0) {
sb.append(buf, 0, n);
if (sb.length() > 65536) break;
}
return sb.toString();
} catch (Exception e) {
return null;
} finally {
if (br != null) try { br.close(); } catch (Exception ignored) {}
}
}

/** UFS：老 ROM 可直读 sysfs ufshc 目录拿细项（新 ROM 被 SELinux 拦，留作三级）。
 *  路径随版本有差异，逐个候选目录探测；目录名以 ufshc 开头即认定。
 *  读不到细项也至少给出类型。 */
private void detectUfs(JSONObject o) throws Exception {
File[] candidates = {
new File("/sys/class/ufshc"),
new File("/sys/class/misc"),
new File("/sys/devices/platform"),
};
for (File root : candidates) {
File[] children = root.listFiles();
if (children == null) continue;
for (File child : children) {
if (!child.isDirectory()) continue;
if (!child.getName().startsWith("ufshc")) continue;
o.put("type", "UFS");
String std = readOneLine(new File(child, "device_descriptor"));
String sub = readOneLine(new File(child, "strings_subtype"));
if (std != null && !std.isEmpty()) o.put("desc", std);
if (sub != null && !sub.isEmpty()) o.put("sub", sub);
return;
}
}
}

/** eMMC：/sys/block/mmcblk0 的 device 子目录下有 cid / name。 */
private void detectEmmc(JSONObject o) throws Exception {
File[] roots = {new File("/sys/block"), new File("/sys/class/block")};
for (File root : roots) {
File[] children = root.listFiles();
if (children == null) continue;
for (File child : children) {
if (!child.getName().startsWith("mmcblk")) continue;
File dev = new File(child, "device");
String cid = readOneLine(new File(dev, "cid"));
if (cid == null) continue;
o.put("type", "eMMC");
String man = emmcManufacturer(cid);
if (man != null) o.put("manufacturer", man);
String pname = readOneLine(new File(dev, "name"));
if (pname != null && !pname.isEmpty()) o.put("product", pname);
String manid = readOneLine(new File(dev, "manid"));
if (manid != null && !manid.isEmpty()) o.put("manid", "0x" + manid);
return;
}
}
}

/** eMMC CID 首字节 → 厂商名。表来源：mmc-utils lsmmc.c（JEDEC JEP106 派生）。 */
private static String emmcManufacturer(String cidHex) {
if (cidHex == null) return null;
String h = cidHex.trim();
if (h.length() < 2) return null;
final int id;
try {
id = Integer.parseInt(h.substring(0, 2), 16);
} catch (NumberFormatException e) {
return null;
}
switch (id) {
case 0x00: return "SanDisk";
case 0x02: return "Kingston/SanDisk";
case 0x03: return "Toshiba";
case 0x11: return "Toshiba";
case 0x13: return "Micron";
case 0x15: return "Samsung/SanDisk/LG";
case 0x2c: return "Kingston";
case 0x37: return "KingMax";
case 0x44: return "ATP";
case 0x45: return "SanDisk Corporation";
case 0x70: return "Kingston";
case 0xfe: return "Micron";
default:   return null;
}
}

/** 读 sysfs 单行文件，失败返回 null（不抛异常）。 */
private static String readOneLine(File f) {
BufferedReader br = null;
try {
br = new BufferedReader(new FileReader(f));
String line = br.readLine();
if (line != null) line = line.trim();
if (line != null && line.isEmpty()) line = null;
return line;
} catch (Exception e) {
return null;
} finally {
if (br != null) try { br.close(); } catch (Exception ignored) {}
}
}
}
/** Toast 须在 UI 线程显示。 */
static class ToastRunner implements Runnable {
private final Activity activity;
private final String msg;
ToastRunner(Activity activity, String msg) { this.activity = activity; this.msg = msg; }
@Override
public void run() {
Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
}
}
/** 文件选择器（GLB 换模型）：static 嵌套类，结果经 onActivityResult 回传 WebView。 */
static class FileChooserClient extends WebChromeClient {
private final MainActivity activity;
FileChooserClient(MainActivity activity) { this.activity = activity; }
@Override
public boolean onShowFileChooser(WebView v, ValueCallback cb, FileChooserParams params) {
if (activity.mFileCallback != null) { activity.mFileCallback.onReceiveValue(null); }
activity.mFileCallback = cb;
try {
activity.startActivityForResult(params.createIntent(), FILE_REQ);
return true;
} catch (Exception e) {
activity.mFileCallback = null;
return false;
}
}
}
}
