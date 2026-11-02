# WxScript 交接文档

> 独立微信脚本模块。本文件用于在新窗口继续开发，包含已完成内容、待办、素材位置、关键技术坑。
> 生成时间：2026-11-02　作者：Johnny520

---

## 0. 一句话现状

P1（构建骨架）已完成并构建成功：仓库 `Johnny520/wxscript` 里已经有一个能编译、能安装、4 Tab 空壳的 LSPosed 模块。
P2 起是真正的工作量：把脚本运行时从 WCX 迁移进来。

---

## 1. 项目定位与命名（已定稿，不要再改）

| 项目 | 值 |
|---|---|
| 仓库 | `Johnny520/wxscript`（默认分支 `main`） |
| 应用名 / 模块名 | **WxScript** |
| 微信设置入口标题 | **微信脚本** |
| applicationId / namespace | **`com.johnny.wxscript`** |
| 内部 Kotlin 包名 | `com.johnny.wxscript` |
| 数据目录 | `Android/data/com.tencent.mm/WxScript` |
| 日志前缀 | `wcxscript-` |
| Xposed 入口类 | `com.johnny.wxscript.entry.XposedEntry` |
| 作者署名 | Johnny520 |

**与 WCX 主模块的关系**：完全独立、并列关系，不是附属。两者可同时安装、LSPosed 中分别勾选、互不依赖。

---

## 2. P1 已完成的内容（真实状态）

### 2.1 提交记录

| commit | 内容 |
|---|---|
| `a314800d` | feat: WxScript P1 构建骨架（22 文件） |
| `47c22797` | fix(build): AGP 9 内置 Kotlin，移除 kotlin.android 插件 |

CI：`41368697495` success，artifact `wxscript-apk`（16.5 MB，debug + release unsigned）。

### 2.2 仓库文件清单（当前全部文件）

```
.github/workflows/ci.yml
.gitignore
README.md
build.gradle.kts
settings.gradle.kts
gradle.properties
gradle/libs.versions.toml
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
gradlew
app/build.gradle.kts
app/proguard-rules.pro
app/libs/xposed-api-82.jar
app/src/main/AndroidManifest.xml
app/src/main/assets/xposed_init
app/src/main/java/com/johnny/wxscript/WxScriptApp.kt
app/src/main/java/com/johnny/wxscript/entry/XposedEntry.kt
app/src/main/java/com/johnny/wxscript/activity/SettingsActivity.kt
app/src/main/java/com/johnny/wxscript/ui/WxTheme.kt
app/src/main/res/drawable/ic_launcher.xml
app/src/main/res/values/arrays.xml
app/src/main/res/values/strings.xml
```

### 2.3 构建配置实际取值

- AGP **9.3.0**，Kotlin **2.4.10**（由 AGP 内置，不再单独应用 `kotlin.android`）
- compileSdk **37**（minorApiLevel 0），minSdk **28**，targetSdk **37**
- JDK 21（CI），jvmTarget 17（`tasks.withType<KotlinCompile>`）
- Gradle wrapper **9.6.1**，distributionUrl 指向腾讯镜像
- Compose BOM `2026.06.01`，material3
- Xposed：**legacy API**（内置 `app/libs/xposed-api-82.jar`，`compileOnly`）
  - `AndroidManifest.xml` 有 `xposedmodule / xposeddescription / xposedminversion=51 / xposedscope`
  - `assets/xposed_init` 内容：`com.johnny.wxscript.entry.XposedEntry`
- 当前**未配置签名**，CI 只产 debug + unsigned release。要出可覆盖安装的正式包，需要在新仓库配置 4 个 secrets：
  `KEYSTORE_BASE64` / `KEY_ALIAS` / `KEY_PASSWORD` / `STORE_PASSWORD`（与 WCX 同一个 keystore），并在 `app/build.gradle.kts` 的 `signingConfigs` 里接上（参考 WCX 的写法）。

### 2.4 已实现的界面骨架

`SettingsActivity` 里 4 个底栏 Tab（默认落在「脚本」）：

- **首页**：引擎 / 微信 API / DexKit 三段就绪度卡片（目前全是 FAIL 占位）+ 快捷操作卡
- **脚本**：空列表占位（说明文案写了「单列列表：名称·版本·作者·右侧开关·右滑操作」）
- **市场**：占位
- **我的**：宿主包名、模块版本、API 文档、诊断中心、导出日志、关于

UI 库用的是 **material3**（不是 miuix）。若要与 WCX 视觉统一，改造点在 `ui/WxTheme.kt` + 各页面，换代成本中等，建议 P3 一次性决定。

---

## 3. 待办（P2 → P5）

### P2　迁移脚本运行时（工作量最大）

**2.1 需要从 WCX 历史恢复的文件**

| 来源 commit | 路径 | 数量 |
|---|---|---|
| `12f67bdb` 的父提交 | `app/src/main/java/h/Hchat/**`（hooks 136 / ui 7 / dexkit 6 / utils 6 / event 3 / preferences 3 / media 1） | 162 |
| 同上 | `app/src/main/java/me/hd/wauxv/**` | 10 |
| `69244b07`（`59592f60` 的父提交） | `com/Johnny/wcx/features/items/scripting_java/**` | 7 |
| 同上 | `com/Johnny/wcx/features/items/scripting_js/**` | 10 |
| 同上 | `com/Johnny/wcx/activity/settings/ScriptSubPages.kt`、`ScriptsPager.kt` | 2 |
| 同上 | `com/Johnny/wcx/features/hchat/HchatScriptBootstrap.kt` | 1 |

拉取方式（沙箱内可用）：

```bash
curl -s -H "Authorization: token $GITHUB_TOKEN" \
  "https://api.github.com/repos/Johnny520/wcx/git/trees/<commit>?recursive=1"
# 单文件：contents API + Accept: application/vnd.github.raw
```

**2.2 必须一起搬的基础设施（否则编译不过）**

`h/Hchat` 与 `scripting_*` 大量引用 WCX 的公共层，需要一并移植或重写精简版：

- `utils/`：`HostInfo`、`WeLogger`、`TargetProcesses`、`KnownPaths`、`fs/*`
- `utils/reflection/`：`ClassLoaders`、`Classes`、`DexKitUtils`、`MethodUtils`、`ProxyBuilderUtils`
- `preferences/`：`WePrefs`（`MmkvPrefsImpl("wekit_prefs")` 之类实现要换成自己的名字，如 `wxscript_prefs`）
- `dexkit/`：`abc/IResolveDex`、`dsl/*`（`dexClass`/`dexMethod`）、`cache/DexCacheManager`、`resolution/*`
- `features/core/`：`Feature`（注解，来自 `libs/common/annotation-scanner`）、`BaseFeature`、`SwitchFeature`、`ClickableFeature`、`ApiFeature`、`FeaturesLoader`、`FeaturesProvider`（由 KSP 生成）
- `features/api/`：微信 API 层 —— `WeChatApis`、`DexFinder`、`WeDatabaseApi`、`WeDatabaseListenerApi`、`WeMessageApi`、`WeContactApi`、`WeConversationApi`、`WePacketManager` 等
- `ui/`：`ui/content/*`（AlertDialogContent/Button/TextButton/DefaultColumn…）、`ui/utils/showComposeDialog`
- `libs/common/`：`bsh`（BeanShell fork）、`reflekt`、`annotation-scanner`

**2.3 装配流程（照抄 WCX 的 `HchatScriptBootstrap`，注意顺序）**

1. 构造 `h.Hchat.hooks.core.FeatureContext`（依赖 DexFinder / DexBridgeHolder / EventBus / ConfigStore(hostContext) / UIRegistry）
2. 自建并**长期持有** `DexKitBridge.create(HostInfo.appInfo.sourceDir)`
3. 安装 `WechatApiFeature` + `ScriptPluginFeature`：**每个 Feature 必须先 `onInit()` 再 `install()`**（漏 `onInit` → UIRegistry 没有 settings provider → 脚本设置页是空白）
4. 安装完调 `DexInstallScheduler.markDexBridgeReady()`
5. WARMUP 阶段由 `wechat_api:warmup` 任务尾部自动 `markDexWarmupReady()`
6. 兜底：独立 `HandlerThread("WxScript-AutoInit")`（**绝对不能放主线程**）延迟 5s 做 `ensureWarmupReady() + retryAllPending()`

**2.4 脚本桥 ClassLoader（高频踩坑）**

`ScriptPluginRuntime.currentBridge()` 必须用模块自己的 ClassLoader（WCX 里是 `ClassLoaders.MODULE`），**不能用 `context.applicationContext.classLoader`**（那是微信的 CL，看不到模块类 → 所有插件报 `Class ... not found in namespace`）。

**2.5 P2 验收标准**

- 设置页「脚本」Tab 里能看到插件列表
- 导入一个 `main.java`（BeanShell）+ `info.prop` 的插件目录，能加载、能执行
- 总开关能打开，不报「微信联系人数据库尚未就绪」
- 日志里 `[WxScript:DexInstall]` 各任务全部 installed

---

### P3　UI 重做（设计方案已定稿）

**信息架构：4 底栏 Tab，默认落地「脚本」**

1. **首页**：三段就绪度（脚本引擎 → 微信 API → DexKit），每行 = 状态点 + 名称 + 短语；DexKit 用线性进度条 + 百分比 + 当前解析类名（可折叠）；失败行右侧给「查看原因 / 重试」；下方「运行中脚本 + 停止」、快捷入口、最近 3 条日志预览
2. **脚本**：分段控件「已启用 / 全部」+ 搜索（名称/描述/作者/tag 全覆盖）+ 按来源分组 sticky header（本地导入 / 市场安装）；每行 = 图标 + 名称 + 副标题（版本·作者）+ 右侧 Switch；右滑出「编辑 / 日志 / 设置 / 卸载」
3. **市场**：搜索 + 分类 chips + 排序（下载/更新）+ 顶部「编辑精选」横滑卡片 + 列表；右上「已安装 / 可更新」；详情页 = README + 版本历史 + 兼容性（支持微信版本）+ 举报入口
4. **我的**：宿主包名与账号、主题、调试开关、API 文档、诊断中心、导出日志、关于/检查更新

**二级页**：脚本编辑/详情、市场详情、API 文档、诊断、日志详情。

**必须遵守的设计取舍**（调研结论，别走回头路）：

- 插件列表**用单列列表，不用卡片**（卡片一屏只看 4 个）
- 引擎状态**不单独开 Tab**，做成首页顶部卡片 + 全局细横幅
- 编辑器**内置为主**（跳外部编辑器会导致改完回不来）；顶栏 = 文件名 + 未保存圆点 + ▶/■ + 格式化 + 更多
- 编辑器底部**三档控制台**（隐藏 / 半屏 / 全屏），错误在 gutter 打红点，日志行可点击跳源码行
- 日志区分「实时控制台」（分级着色 + tag 过滤 + **暂停自动滚动** + 清空 + 搜索）与「崩溃文件」（分享/保存/按时间大小清理）
- 诊断页「一键修复」执行前**先列出将要做什么**
- **不做插件自动更新**（只提示可更新）
- 不要 Dashboard 卡片堆砌、玻璃拟态、大动效、环形仪表盘

---

### P4　在线市场 / API 文档 / 诊断

- **市场服务端**：WCX 仓库根目录已有 `plugin-market-server.zip` + `插件市场服务端说明.md`
  （Python 标准库 http.server + sqlite3，18 个文件，含 `deploy.sh` 和 9 个 SQL 迁移，可直接部署到自建服务器/Render/Koyeb）
  注意：GitHub Pages 跑不了动态后端。
- **插件 API 文档**：Hchat 上游 `app/src/main/assets/SCRIPT_PLUGIN_API.md`、`script_plugin_agent_guide.md`，可作原始素材。
- **诊断页**：参考 WCX `activity/settings/ScriptSubPages.kt` 的 `ScriptDiagnosticsScreen`（自检/修复/彻底重初始化三段式 + 逐项进度条 + 纯文本报告 + 复制按钮）。

---

### P5　从 WCX 主模块剥离脚本

WCX 侧要删除/改动的点：

- `com/Johnny/wcx/features/items/scripting_js/**`（10）
- `com/Johnny/wcx/features/items/scripting_java/**`（7）
- `com/Johnny/wcx/activity/settings/ScriptSubPages.kt`、`ScriptsPager.kt`
- `com/Johnny/wcx/features/hchat/HchatScriptBootstrap.kt`
- `app/src/main/java/h/Hchat/**`（162）、`me/hd/wauxv/**`（10）
- `SettingsActivity.kt` 里的 `SettingsNavTarget` 脚本相关项 + `MainPagerScreen` 里的 `ScriptsPager` 调用
- `FeaturesLoader.kt` / `WeLauncher.kt` 里的 bootstrap 调用
- `app/build.gradle.kts` 里 bsh/annotation-scanner 依赖（若仅脚本用）
- `proguard-rules.pro` 里只服务脚本的 keep 规则可保留（无害）或清理

**注意**：WCX 当前 dev 是 `v345 + 7d583a9d（已恢复 scripting_js/java + me.hd.wauxv）`，剥离时要从这个状态往回砍。

---

## 4. 技术坑清单（血泪，务必先看）

### 构建

1. **AGP 9.0+ 不能再应用 `org.jetbrains.kotlin.android`** → 直接 `InvalidUserCodeException`。只用 `com.android.application` + `org.jetbrains.kotlin.plugin.compose`，jvmTarget 用 `tasks.withType<KotlinCompile> { compilerOptions { ... } }`
2. Gradle wrapper 走腾讯镜像 `https://mirrors.cloud.tencent.com/gradle/gradle-9.6.1-bin.zip`（官方源慢）
3. 大文件依赖（rhino / xposed-api / ARSCLib）一律内置 `app/libs/`，不要依赖 JitPack 单点源
4. `h/Hchat/ui/miuix/MiuixSettingsPage.kt` 有 4.6 万行，Kotlin 编译需要 `kotlin.daemon.jvmargs=-Xmx8g`
5. release 的 lintVital 对超大源码会 GC OOM → `lint { checkReleaseBuilds = false }`

### R8 / 混淆（脚本类最容易在这里挂）

```proguard
-keep class bsh.** { *; }
-keep class me.hd.wauxv.** { *; }
-keep class me.hd.wauxv.plugin.api.callback.** { *; }
-keep class me.hd.wauxv.data.bean.** { *; }
-keep class me.hd.wauxv.data.bean.info.** { *; }
-keep class me.yun.silk.** { *; }
-keep class org.luckypray.dexkit.DexKitBridge { *; }
-keep class org.xmlpull.v1.** { *; }
-keep class androidx.lifecycle.viewmodel.compose.** { *; }
-dontwarn kotlin.metadata.**
-dontwarn org.mozilla.kotlin.**
```

**根因**：脚本（BeanShell 前置代码 + 用户脚本）是**按原始类名在运行时解析类**的，R8 静态分析看不到这些引用 → 会把只被字符串引用的类裁掉/改名 → 报 `Class: Xxx not found in namespace`。建议再补一个「ABI 锚点」类，在 `newInterpreter` 里真实引用一遍所有脚本可见类（WCX 里叫 `ScriptPluginAbi`）。

### DexKit / 微信 API

6. `DexInstallScheduler` 分 EARLY / BRIDGE / WARMUP 三阶段，`markDexBridgeReady()` / `markDexWarmupReady()` 不推进就永远不执行后续任务
7. `DexKitBridge` 必须长期持有，不要复用带 30s 空闲关闭的实现
8. `retryIndex` 达 `MAX_RETRY_ATTEMPTS=6` 会永久放弃，恢复入口是 `retryAllPending()`
9. **不要在主线程跑任何 DexKit 解析/重试**（会让微信 UI 卡死），一律 `HandlerThread`
10. `KavaReflector`（KavaRef）在模块进程里会**静默返回空** → 50 处受影响，已改原生反射优先
11. `reflekt` 的 `firstMethod{...}` / `toClass()` 在目标不存在时**抛异常**，只有 `OrNull` 版本返回 null → `onEnable` 里务必包 try-catch，否则整个 Feature 装挂失败

### 脚本运行时

12. 插件主文件是 `main.java`（BeanShell，Java 语法），元信息是 `info.prop`（Properties）
13. `ScriptPluginRuntime` 用 `FileObserver` 监听插件目录：`main.java` 变更 → 500ms 防抖重载；`info.prop`/README 变更 → 刷新列表
14. 插件齿轮（设置）点击 = `callOpenSettings(pluginId)`，需要插件实现 `openSettings` 回调；**失败才 Toast**
15. `VoiceForwardMiuixDialog` 这类「迁移空桩」必须检查，空方法体不报错但也没效果（WCX 曾因此排查很久）

### 微信 8.0.80 已确认的结构事实

- `ChattingUI` = `com.tencent.mm.ui.chatting.ChattingUI`（注意有 `.chatting` 一层！），26 个变体全部继承它
- `LauncherUI` = `com.tencent.mm.ui.LauncherUI`
- `NetSceneSendMsg` = `Lq61/s0;`，构造器 `(String,String,int,int,long|Object,String)` 6 参
- `FrostedContentView` 重写了 `dispatchDraw`（磨砂），挂壁纸类操作要小心被 `saveLayer` 裁掉
- 微信资源里 `res/raw/*.svg` 在 APK 中**无实体文件**（只有 arsc 路径记录），这类资源缺失会导致 voip 界面 `InflateException` 崩溃

### 沙箱/CI 环境

16. 沙箱 `/tmp` 每轮重置，大 APK（273MB）必须单轮内分析完
17. `github.com` 主站被封，只能走 `api.github.com`（Git Data API 推代码）
18. 推 `h/Hchat/**` 时真实路径是 `app/src/main/java/h/Hchat/**`，漏写前缀会推到仓库根新建 `h/` 目录
19. GitHub 空仓库先建 blob 会 409，必须先用 Contents API PUT 一个文件建初始 commit

---

## 5. 素材位置

| 素材 | 位置 |
|---|---|
| Hchat 上游源码 | `github.com/ljh520134/Hchat-alt-entry`（分支 `alt-entry`，990 文件，`app/src/main/java/h` 占 601） |
| WCX 里的 Hchat 移植版 | WCX `12f67bdb` 的父提交（未删前的 `h/Hchat/**`） |
| 脚本引擎原始版 | WCX `69244b07`（`scripting_java` / `scripting_js`） |
| 市场服务端 | WCX 仓库根 `plugin-market-server.zip`、`插件市场服务端说明.md` |
| 插件 API 文档 | Hchat `app/src/main/assets/SCRIPT_PLUGIN_API.md`、`script_plugin_agent_guide.md` |
| 脚本目录约定 | `<externalMediaDir>/WCX/脚本插件`（新模块建议改成 `<externalMediaDir>/WxScript/脚本插件`） |

---

## 6. 新窗口开场指令（直接复制粘贴）

```
这个窗口专门开发 WxScript（独立微信脚本模块），不再处理 WCX 主模块。

项目现状：
- 仓库 Johnny520/wxscript（main 分支），已完成 P1 构建骨架，最新 commit 47c22797，
  CI run 41368697495 构建成功。能编译、能安装、4 Tab 空壳可打开。
- 命名已定稿：应用名 WxScript / applicationId com.johnny.wxscript /
  微信设置入口「微信脚本」/ 数据目录 Android/data/com.tencent.mm/WxScript / 日志 wcxscript- *
- 构建：AGP 9.3.0 + Kotlin 2.4.10（AGP 内置，不要应用 kotlin.android）+ compileSdk 37 +
  minSdk 28 + JDK 21 + jvmTarget 17 + Gradle 9.6.1（腾讯镜像）
- Xposed 走 legacy API（app/libs/xposed-api-82.jar + assets/xposed_init）

下一步是 P2：从 WCX 历史 commit 迁移脚本运行时
（h/Hchat 162 文件 + me.hd.wauxv 10 + scripting_java 7 + scripting_js 10 + 脚本 UI 3 个文件），
并移植其依赖的公共层（HostInfo / WeLogger / ClassLoaders / WePrefs / dexkit / features.core / 微信 API 层）。

完整交接文档见 WxScript-交接文档.md（含待办清单、技术坑、素材位置）。
请先读文档，然后按 P2 开始干活。GitHub token 用环境变量 GITHUB_TOKEN。
```

---

## 7. 分工约定

- **WCX 主模块**：在原窗口继续（四 Tab 主题背景、抢红包、崩溃排查、发版等）
- **WxScript**：新窗口，按本文档 P2 → P5 推进
- 两边**不要交叉修改**：WxScript 不动 WCX 仓库；WCX 也不要在没通知的情况下删 `h/Hchat`（等 WxScript 迁移完成再执行 P5）
