# Tookies

一个轻量安卓工具箱，初代目标体积逼近 17KB。

## 已实现工具

- **简易画板**：画布、调色盘（8 色）、粗细滑轨（1-30px）、橡皮擦、单步撤销/恢复、一键清屏
- **屏幕尺**：屏幕长边左右精确刻度尺（mm/cm/inch 三种单位自动换算）、双刻度线拖拽测距
- **计算器（xCalc）**：[tydlig-android](https://github.com/Pokem0n2/tydlig-android) 成品嵌入。无限画布数字连线、结果实时联动、科学函数、撤销/重做、画布自动保存与导出分享
- **系统信息**：[asi-z](https://github.com/Pokem0n2/asi-z) v0.8.0 成品移植。设备/内存/闪存三级探测/显示/存储/系统/CPU 集群与 MIDR 解码/GPU EGL 探针/电池健康度/传感器，10 组只读信息一键复制
- **GLB预览**：GLB 人偶摆姿势工具移植。骨骼级姿势编辑（选中关节球旋转）、整体移动、截图存相册、模型库（localStorage）、pose 保存/载入（JSON）
- **库若思**：[cross100](https://github.com/Pokem0n2/cross100) v2.1.3 成品移植。四难度色彩消除拼图（EASY 4×4·3色 → HELL 10×10·9色）：点击色块，整行整列各降一级色阶；BGM/激光/爆炸音效、音量面板、seed 复现、破解提示、撤销、双主题、中英双语
- **传感器**：[op13t-sensors](https://github.com/Pokem0n2/op13t-sensors) v0.2.2 成品移植。固定十传感器直取（加速度/磁场/陀螺仪/光线等），实时监听数据流（200ms 节流推送）、规格详情（功耗/分辨率/量程/FIFO）、单卡开关、后台自动停流省电

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
│   ├── index.html           # 单文件 SPA（主页+画板+屏幕尺）
│   ├── calc.html            # xCalc 画布计算器（独立页面，tydlig-android v0.4.7）
│   ├── sysinfo.html         # 系统信息（asi-z v0.8.0 移植，独立页面）
│   ├── glb.html             # GLB 预览（poser.html 移植，人偶摆姿势）
│   ├── cross.html           # 库若思（cross100 v2.1.3 主线版移植，含 bgm/laser/explosion 音效）
│   └── sensors.html         # 传感器（op13t-sensors v0.2.2 移植，需 SensorsBridge 原生桥）
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
