# 鱼皮 AI 代码生成器 - 前端

这是一个基于 Vue 3、TypeScript 和 Ant Design Vue 的 AI 零代码应用生成平台前端。

目前已包含主页、用户登录与注册、用户管理、应用管理、对话历史管理、AI 流式生成、
Vue 工程构建状态等待、应用编辑、生成结果预览和应用部署，并已与当前后端接口保持一致。

当前后端已经提供对话历史接口，因此该功能默认开启；代码下载接口尚未提供，下载入口
仍然默认关闭。可以通过环境变量覆盖：

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
