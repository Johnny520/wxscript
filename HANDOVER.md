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

---

## 8. P2 完成状态（2026-11-02，commit 0bfd4b25，CI run 41389774826 success）

### 8.1 结果

`assembleDebug` + `assembleRelease`（R8）双通过，产物 `wxscript-apk` 32.4 MB。
P2 的目标「把 WCX 的脚本运行时迁移进来」在**编译层面完成**；运行时行为仍需真机验证（见 8.4）。

### 8.2 实际迁移了什么（分支 `p2-runtime` → 已并入 main）

| 内容 | 数量 | 说明 |
|---|---|---|
| `h/Hchat/**` | 161 | 剔除 `ui/miuix/ScriptPluginMiuixPages.kt`（232KB 市场/Agent UI，留 P4） |
| `me/hd/wauxv/**` + `me/yun/silk/**` | 13 | 脚本 ABI 包名**保持原样**，不能改 |
| `com.johnny.wxscript.**`（原 `com.Johnny.wcx`） | 132 | utils / dexkit / features.core / features.api / ui.content / ui.utils / loader.utils |
| `scripting_java`(7) + `scripting_js`(10) | 17 | 取自 WCX dev（最新 8.0.79/8.0.80 适配版） |
| `libs/common/{bsh,reflekt,stubs}` | 3 个 Gradle 模块 | bsh 的 javacc/jjtree 代码生成已删除（生成物本来就在库里） |
| `app/libs/rhino-45f29f6.jar` | 1.6MB | 内置，避免 JitPack 单点源 |

新增的装配层（本仓库自己写的）：

- `features/core/Feature.kt` + `features/core/WxFeatures.kt` —— 用**反射读注解**替代 KSP 生成的 `FeaturesProvider`
- `features/hchat/HchatScriptBootstrap.kt` —— 照抄 WCX：onInit → install → markDexBridgeReady → 看门狗 + 5s 自动初始化
- `features/hchat/ScriptFeaturesInstaller.kt` —— 精简版 `FeaturesLoader`：DexCacheManager.init → 逐项从缓存恢复/重新扫 dex → startup
- `features/items/scripting_java/ScriptSendButtonAnchor.kt` —— 单独拎出发送按钮 DexKit 锚点（原挂在 ChatInputBarEnhancements 上）
- `startup/WxScriptStartup.kt` —— HybridClassLoader 父链改写 + HostInfo + HiddenApiBypass + ActivityProxy/ActivityLauncher + 后台线程引导
- `entry/XposedEntry.kt` —— hook `Instrumentation.callApplicationOnCreate`
- `ui/script/ScriptPluginsTab.kt` —— material3 版脚本页：总开关 / 插件列表 / 每项开关 / README / 齿轮 / 删除 / 内置引擎开关
- `ui/HostLauncher.kt` —— 模块进程里「在微信中打开设置」（走 WeChatSplashActivity 代理）

### 8.3 主动砍掉的东西（不是漏，是决定不做）

- `ui/miuix/ScriptPluginMiuixPages.kt`（市场/Agent/管理器 miuix 页）→ P4 用新 UI 重做
- `ui/panel/**`、`features/items/chat/panel/**`、`VoicePanel.kt`、`ChatInputBarEnhancements.kt`、`EdgeTtsClient.kt`
  （语音面板 + FunBox + TTS，共 ~500 个编译错误，与脚本无关；发送按钮锚点已单独抽出）
- `ui/utils/theme/Theme.kt` 的 400 行调色板常量 → 换成 material3 默认 `lightColorScheme()/darkColorScheme()`
- KSP（annotation-scanner）、Room、Ktor、libsu、smali/jadx/ARSCLib 等 WCX 专有大依赖，一律没引

### 8.4 已知未验证 / 待办

1. **真机未验证**。P2 验收里「能看到插件列表 / 能加载插件 / 总开关能开」需要装机跑一遍，特别是：
   - `ActivityProxy` 让模块 Activity 跑在微信进程的机制（微信 8.0.80 是否仍允许未声明组件启动）
   - 脚本目录已改为 `<externalMediaDir>/WxScript/脚本插件`
2. `GeneratedMethodHashes` 是空表 → DexKit 缓存只按「委托完整性」判断，代码改动不会自动失效（微信版本变化会清）。
3. `ScriptSendButtonAnchor` 目前挂在 `WxFeatures.ALL` 里只做解析，不参与开关。
4. release 未签名（沿用 P1：没配 4 个 secrets）。

### 8.5 迁移中踩到的坑（新增，追加到第 4 节）

20. **reflekt 在 Android 库模块里编译不过**：`AccessibleObject.trySetAccessible()` 是 JDK9 API，Android 没有 → 已改 `isAccessible = true` 兜底。
21. **miuix-blur 的 minSdk 是 33**，引 `miuix-ui` 时不要连带 blur/shader，否则 manifest merge 直接失败。
22. **material3 必须锁 `1.5.0-alpha19`**：BOM 自带版本与 expressive API（`ListItemShapes` / `MaterialExpressiveTheme` / `CornerRadius`）不兼容，会报「Cannot access ... it is internal」。
23. **Git Data API 的 `base_tree` 只会新增不会删除**：本地删掉的文件必须用「全量 tree（不传 base_tree）」推送，否则仓库里残留旧文件，CI 仍按旧文件编译。
24. 依赖闭包要按**符号级**算：WCX 大量「同包不同文件」的扩展函数/常量（`CornerRadius`、`MkkovPrefsImpl`、`WePacketSigner`、`M3Shape`），只看 import 会漏。
25. R8 必补 `-dontwarn jdk.dynalink.** java.beans.** com.highcapable.kavaref.**`（rhino / kavaref 的 JDK-only 引用）。

### 8.6 P3 起点建议

- 先把「脚本」页做成 P3 设计的单列列表 + 搜索 + 右滑操作；当前是 material3 卡片版最小实现
- 补「导入插件」（zip / 目录）——`ScriptPluginManager.inspectImport` / `applyImport` 已经在，接 UI 即可

---

## 9. 固定签名（2026-11-02 已配置）

WxScript 有一套**长期不变**的签名密钥，后续所有版本都用它签，保证覆盖安装、不需要卸载重装。

| 项 | 值 |
|---|---|
| keystore | PKCS12，4096-bit RSA，有效期 36500 天（100 年） |
| keyAlias | `wxscript` |
| 证书 DN | `CN=WxScript, OU=Johnny520, O=WxScript, L=CN, ST=CN, C=CN` |
| **SHA-256 指纹** | `22:0B:62:05:67:1D:BC:DC:81:3F:86:A9:B6:6A:7A:EC:43:E4:80:FC:AA:E5:45:CF:D7:18:20:17:83:00:76:51` |
| SHA-1 指纹 | `67:0E:83:58:97:63:EF:74:23:BD:D0:D2:CF:04:15:CA:EB:B4:EA:E2` |
| 仓库 Secrets | `KEYSTORE_BASE64` / `KEY_ALIAS` / `KEY_PASSWORD` / `STORE_PASSWORD` |

- 构建时 `app/build.gradle.kts` 会从 `KEYSTORE_BASE64` 解出临时 jks 再签名；本地开发也可把 jks 放到仓库根 `keystore/wxscript-release.jks`；
- CI 有一步 `Verify release signature`，用 `apksigner verify --print-certs` 打印证书，指纹对不上就说明签名配错了；
- **密钥必须备份**：keystore 丢了就再也无法覆盖升级（只能换包名或让用户卸载重装）。

---

## 10. P3 进度（2026-11-02，分支 `p3-ui`）

已完成（按定稿设计）：

| 页面 | 说明 |
|---|---|
| 脚本 Tab | **单列列表**（不用卡片）+ 搜索（名称/描述/作者/ID）+ 分段「已启用 / 全部」+ sticky header 按来源分组（本地导入 / 市场安装）+ 每行 图标·名称·副标题·开关 + 左滑露出「编辑/日志/设置/卸载」+ 导入（SAF 选 zip，冲突可覆盖或跳过） |
| 内置编辑器 | 顶栏 = 文件名 + 未保存圆点 + ▶/■ + 格式化 + 更多（保存/重载/看日志）；底部三档控制台（隐藏/半屏/全屏），日志里「第 N 行 / line N / .java:N:」可点击跳转 |
| 首页 | 三段就绪度（脚本引擎 → 微信 API → DexKit）+ DexKit 线性进度条/百分比/当前任务（可折叠）+ 运行中脚本（可停止）+ 快捷入口 + 最近 3 条日志 |
| 全局细横幅 | 引擎未就绪时每个 Tab 顶部一条，点「诊断」直达 |
| 诊断页 | 重新检测 / 生成报告 / **一键修复（执行前先列出将要做什么）**，逐项 ✅/❌ |
| 日志页 | 实时控制台（V/D/I/W/E 分级着色 + 关键字过滤 + 暂停 + 清空 + 摘要）+ 崩溃文件（未捕获异常自动落盘 `<moduleData>/crash/*.txt`，可查看/删除） |
| 市场 Tab | P3 阶段只有服务可用性（已由 P4 接手，见第 11 节） |
| 我的 | 宿主信息 / 详细日志开关 / 诊断·日志·API 文档入口 / 导出日志摘要 / 关于 |

P3 的两项遗留（市场完整化、API 文档完整版）已在 P4 完成，见下一节。

新增的坑：
26. **`Modifier.matchParentSize()` 只能在 BoxScope 里用**；自己写左滑行时注意内容层要盖在操作层之上，否则点不到。
27. **`WePrefs` 本身就是 SharedPreferences + Editor**：写值用 `WePrefs.default.putBoolean(k, v)`，没有 `putBool`。
28. `WeLogger` 的日志文件名前缀是硬编码的 `wcx-`，已改成 `wcxscript-`（同时改了清理用的正则，否则旧日志永远清不掉）。

## 11. P4 进度（2026-11-03，已合并 main `286cf35a`）

| 内容 | 说明 |
|---|---|
| 市场列表 | `MarketData.load()` 拉 `GET /v1/plugins`（q / sort=latest|downloads / limit），搜索防抖 400ms，全部在 IO 线程；三种筛选 chip（全部/已安装/可更新）+ 两种排序 chip |
| 已安装/可更新 | 用 `PluginMarketInstaller.defaultLocalPluginId()` + `existingLocalPluginId()` 把远程条目对齐到本地插件目录，`MarketData.isNewer()` 做版本比较（解析失败按「不同即可更新」） |
| 详情页 | `MarketDetailScreen`：README（取 files 里的 README.md，回落 description）、文件清单与大小、版本历史（`GET /v1/plugins/{id}/snapshots`）、下载/点赞/评论数、安装与更新按钮 |
| 安装/更新 | `PluginMarketInstaller.install(overwrite=true)` 覆盖前必须二次确认；装完自动 `reloadPlugin` 让新代码立即生效；`recordDownload` 埋点失败不影响安装 |
| 服务器切换 | 市场页右上角「服务器」：官方 / 备用 / 自定义地址（校验 http(s) 前缀），保存后立即重拉列表；诊断页新增「探测市场」按钮（`/health`） |
| 插件 API 文档 | `ApiDocData` 由 `tools/gen_apidoc.py` 从桥接层源码自动生成（wa 144 / bridge 67 / audio 42 条真实签名），文档页分「指南 / wa / bridge / audio」四页，支持搜索 + 按前缀分组；指南里的变量名与回调名取自 `ScriptPluginRuntime` 的 prelude 注入 |
| 诊断页 | 新增「本地插件」（数量/启用/运行中/市场安装）与「市场服务器」两项，修复项与原来一致 |
| 编辑器 | 语法高亮（`JavaHighlightTransformation`，Identity 偏移映射，光标不乱跳）+ 行号 gutter（只渲染可见行）+ 错误行红点（3 秒轮询日志里的 E/A 行并解析行号）+ 当前行高亮 |
| 市场服务端 | `server/plugin-market/`（18 文件：app.py / 9 个迁移 / admin.html / deploy.sh / examples / tests），附 `server/README.md` 说明最小部署与 App 侧接法 |

### P4 新增的坑

29. **`Result` 双重包装**：`MarketData.load/detail/history` 本身返回 `Result`，界面层**不要再套 `runCatching`**，否则 `onSuccess` 拿到的是 `Result` 而不是数据（编译报 `Assignment type mismatch`）。
30. **Compose 里 `LaunchedEffect` 不等于后台线程**：网络/文件必须 `withContext(Dispatchers.IO)`，否则 `NetworkOnMainThreadException` 直接崩在宿主进程里。
31. 语法高亮只改颜色不改字符时，`OffsetMapping.Identity` 是唯一安全选择；一旦替换字符（如补空格），选区/光标/IME 全会错位。
32. 行号栏不要和编辑器共享同一个 `ScrollState`（两个 scrollable 会抢滚动），读 `scroll.value` 自己算位移更稳，且只渲染可见行。

### 尚未完成

- **P5 剥离 WCX**：需仓库 owner 确认时机（WCX 侧删 `h/Hchat` 162 文件、`me/hd` 10、`scripting_*`、`ScriptSubPages/ScriptsPager`、`HchatScriptBootstrap`、相关 nav/loader/proguard）。本窗口按约定**不动 WCX**。
- **真机验证**：ActivityProxy 在微信进程拉起模块 Activity 是否可用；脚本 Tab 能否列插件、总开关能否打开；`[WxScript:DexInstall]` 是否全部 installed。
