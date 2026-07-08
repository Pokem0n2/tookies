# Tookies

一个轻量安卓工具箱，目标体积逼近 17KB（比 cross100 v2.0.19 的 78KB 更小）。

## 已实现工具

- **简易画板**：画布、调色盘（8 色）、粗细滑轨（1-30px）、橡皮擦、单步撤销/恢复、一键清屏
- **屏幕尺**：屏幕长边左右精确刻度尺（mm/cm/inch 三种单位自动换算）

## 后续规划

根据需要继续添加工具模块（统一在主页以 3 列瀑布流展示）。

## 技术栈

- 单 Activity + WebView + 单 HTML SPA（无外部依赖）
- 构建链：`javac → d8 → aapt2 → zipalign → apksigner`
- aarch64（DGX Spark）通过 `box64` 运行 x86_64 build-tools

## 构建

```bash
# 需要：ANDROID_HOME + build-tools 34.0.0 + platforms;android-34
git tag v0.1.0   # 版本号从 tag 提取
bash apk/build.sh
```

输出：`tookies-v0.1.0.apk`

## 目录

```
apk/
├── AndroidManifest.xml      # 应用清单（minSdk 21, targetSdk 34）
├── build.sh                 # 构建脚本
├── assets/
│   └── index.html           # 单文件 SPA（主页+画板+屏幕尺）
├── res/
│   ├── drawable/
│   │   └── ic_launcher_foreground.xml   # 自适应图标前景
│   ├── mipmap-anydpi-v26/
│   │   └── ic_launcher.xml              # 自适应图标描述
│   ├── mipmap-mdpi/
│   │   └── ic_launcher.png              # 1×1 透明 PNG（老版本兜底）
│   └── values/
│       ├── colors.xml
│       └── strings.xml
└── src/com/tookies/app/
    └── MainActivity.java    # 单 Activity
```