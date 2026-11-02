# WxScript

独立的微信脚本模块（LSPosed）。作者：**Johnny520**

WxScript 与 [WCX](https://github.com/Johnny520/wcx) 相互独立：不同的 APK、不同的 `applicationId`、不同的数据目录，两者可以在 LSPosed 中分别勾选、同时使用。

## 定位

一个专注于「脚本」的微信模块，不做其他增强功能：

- **Java 脚本（BeanShell）** 与 **JS 脚本（Rhino）** 双引擎
- 脚本插件：导入、启用/禁用、编辑、查看日志、插件设置
- 在线插件市场：浏览、下载、更新（不自动更新）
- 内置编辑器 + 可伸缩日志控制台
- 插件 API 文档
- 诊断中心：自检、一键修复、导出日志

## 与 WCX 的隔离

| 项目 | WCX | WxScript |
|---|---|---|
| applicationId | `com.Johnny.wcx` | `com.johnny.wxscript` |
| 应用名 | WCX | WxScript |
| 微信设置入口 | WCX | 微信脚本 |
| 数据目录 | `Android/data/com.tencent.mm/WCX` | `Android/data/com.tencent.mm/WxScript` |
| 日志 | `wcx-*.log` | `wxscript-*.log` |

## 开发进度

- [x] **P1** 构建骨架：可编译、可安装、4 Tab 空壳
- [ ] **P2** 迁移脚本运行时（Hchat 脚本子系统 + me.hd.wauxv + Java/JS 引擎）
- [ ] **P3** UI：首页就绪度、插件列表、编辑器 + 控制台、日志
- [ ] **P4** 在线市场、API 文档、诊断中心
- [ ] **P5** 从 WCX 主模块中剥离脚本相关代码

## 构建

```bash
./gradlew assembleDebug
```

需要 JDK 21。产物在 `app/build/outputs/apk/`。

## 许可

仅用于学习与研究。使用脚本自动化功能存在账号风险，请自行评估。
