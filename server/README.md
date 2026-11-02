# 插件市场服务端

WxScript 的「市场」Tab 走的就是这套后端的 HTTP 接口（`GET /v1/plugins`、`GET /v1/plugins/{id}`、
版本历史、下载埋点、点赞评论、管理端等）。服务端只有 Python 3 标准库依赖（`http.server` + `sqlite3`），
不需要 FastAPI / Node / 第三方包，因此一台 1C1G 的小机器就能跑。

## 目录

```
plugin-market/
├── app.py                 服务主体（路由 / SQLite / 鉴权 / 限频 / 管理端）
├── migrations/            9 个 SQL 迁移，首次启动自动执行
├── static/admin.html      网页管理端（发布、审核、删除、黑名单）
├── deploy.sh              systemd + Nginx 的一键部署脚本
├── examples/              systemd unit / Nginx 反代 / 删除插件脚本
├── tests/test_app.py      基础自测
├── README.md              接口清单与参数说明
└── DEPLOYMENT.md          部署细节
```

## 最小部署

```bash
# 1. 上传本目录到服务器，例如 /opt/wxscript-market
# 2. 直接前台跑一遍确认能起来（默认监听 127.0.0.1:8765）
python3 app.py
curl -s http://127.0.0.1:8765/health

# 3. 交给 systemd（examples/ 里有 unit 模板）
sudo cp examples/hchat-plugin-market.service /etc/systemd/system/wxscript-market.service
sudo systemctl enable --now wxscript-market

# 4. Nginx 反代 + HTTPS（examples/nginx.conf 是模板）
#    不要把 Python 端口直接暴露到公网
```

## 接到 App 上

1. 打开 WxScript → 「市场」Tab → 右上角「服务器」；
2. 选择「自定义地址」，填入 `https://你的域名`（必须是 http/https 开头，末尾斜杠会被去掉）；
3. 保存后列表会立刻重新拉取；连不上时诊断页的「探测市场」会给出具体失败原因。

> 默认的「官方服务器 / 备用服务器」是上游 Hchat 的公共实例，随时可能下线。
> 想长期使用请自建，并在 App 里切到自己的地址。

## 注意

- 数据只有 SQLite 一个文件（默认 `data/market.db`），备份直接拷这个文件即可；
- 插件包体大小、文件名、SHA-256 都做校验，客户端安装前还会再校验一次；
- 管理员密码走管理端 `/admin/` 设置，不要用默认值。
