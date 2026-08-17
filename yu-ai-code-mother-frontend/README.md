# 鱼皮 AI 代码生成器 - 前端

这是一个基于 Vue 3、TypeScript 和 Ant Design Vue 的 AI 零代码应用生成平台前端。

目前已包含主页、用户登录与注册、用户管理、应用管理、AI 流式生成、应用编辑、
生成结果预览和应用部署，并已与当前后端接口保持一致。

当前后端尚未提供“对话历史”和“代码下载”接口，因此对应入口默认关闭，避免页面产生
404 请求。后续补齐接口后，在环境文件中开启即可：

```sh
VITE_ENABLE_CHAT_HISTORY=true
VITE_ENABLE_CODE_DOWNLOAD=true
```

部署域名 `VITE_DEPLOY_DOMAIN` 需要与后端 `AppConstant.CODE_DEPLOY_HOST` 保持一致。

## 本地开发

```sh
npm install
npm run dev
```

开发服务器会把 `/api` 请求代理到 `http://localhost:8123`，需要先启动本地后端。

## 构建检查

```sh
npm run build
```

## 接口代码生成

后端启动并可以访问 OpenAPI 文档后，可运行：

```sh
npm run openapi2ts
```

生成配置位于 `openapi2ts.config.ts`。
