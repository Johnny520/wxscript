# JS 脚本参考资料

WxScript 的「JS 脚本」功能（Rhino 引擎，`features/items/scripting_js`）与 Java 脚本共用插件目录，
但写 JS 时有两个实际困难：没有类型提示、没有可照抄的完整示例。这里放两份参考：

| 文件 | 用途 |
|---|---|
| `globals.d.ts` | 脚本里所有全局 API 的类型声明（`log` / `http` / 事件回调 / 内置对象）。在 PC 上写脚本时放到脚本目录，IDE 会自动补全 |
| `script.js` | 一个可直接运行的示例：指令解析（`#天气`）+ 群聊前缀清理 + 定时任务 |
| `script_antimessagerecall3.js` | 真实案例：用 JS 监听撤回事件，把撤回信息打到日志 |

## 怎么用

1. 新建一个插件目录（与 Java 插件相同结构：`main.java` 或 JS 入口 + `info.prop`）；
2. 把 `script.js` 复制进去改成自己的逻辑，顶部保留 `/// <reference path="./globals.d.ts" />` 可获得补全；
3. 在脚本页启用插件，改文件后运行时会自动重载（约 500ms 防抖）。

## 已知限制

- JS 引擎（Rhino）**不能**直接调用 `We*Api` 这类 Kotlin 桥（`WeMessageApi` / `WeDatabaseApi`），
  需要发消息、改数据库时用 Java 脚本（BeanShell），或通过 `wa` 桥已暴露的方法；
- 因此 `script_antimessagerecall3.js` 只把撤回信息写日志，不会像 Java 版防撤回那样还原消息；
- 事件回调命名与 Java 一致：`onLoad` / `onUnload` / `onHandleMsg` 等，参数是 JS 对象（字段名见 `globals.d.ts`）。
