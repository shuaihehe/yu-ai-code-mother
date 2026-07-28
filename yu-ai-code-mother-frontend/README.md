# 鱼皮 AI 代码生成器 - 前端

这是一个基于 Vue 3、TypeScript 和 Ant Design Vue 的 AI 零代码应用生成平台前端。

目前已包含主页、用户登录与注册、用户管理、应用管理、对话生成、应用编辑、
生成结果预览、代码下载和部署结果展示。应用与对话相关接口会随着后端模块补充后可用。

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
