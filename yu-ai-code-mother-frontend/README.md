# 鱼皮 AI 代码生成器 - 前端

这是一个基于 Vue 3、TypeScript 和 Ant Design Vue 的 AI 零代码应用生成平台前端。

目前已包含主页、用户登录与注册、用户管理、应用管理、对话历史管理、AI 流式生成、
Vue 工程构建状态等待、应用编辑、生成结果预览和应用部署，并已与当前后端接口保持一致。

当前后端已经提供对话历史和代码下载接口。对话历史功能可以通过环境变量覆盖：

```sh
VITE_ENABLE_CHAT_HISTORY=true
```

部署域名 `VITE_DEPLOY_DOMAIN` 需要与后端 `AppConstant.CODE_DEPLOY_HOST` 保持一致。

## 本地开发

```sh
npm install
npm run dev
```

开发服务器会把 `/api` 请求代理到 `http://localhost:8123`，需要先启动本地后端。

## 可视化编辑

应用对话页的“编辑模式”位于发送按钮左侧。预览加载完成后，应用所有者可开启编辑，
悬浮查看边框、点击选中元素，再输入修改要求发送。输入框上方会展示选中元素，
点击提示框的关闭按钮可重新选择；退出编辑、发送消息或刷新预览都会清除选中状态。

编辑器在运行时向预览 iframe 注入脚本和样式，不修改生成项目的源码。
父页面与预览必须同源（协议、域名、端口均一致）：本地保留 `VITE_API_BASE_URL=/api`
及 Vite 代理；线上需将同域名下的 `/api` 反向代理到后端，包含 `/api/static/`。
仅配置后端 CORS 不能允许跨域 iframe 注入。应用部署域名无需因此改动。

启动开发服务器后，可访问 `/tests/visualEditor.html` 运行不调用后端的浏览器回归测试。
`/tests/chatInput.html` 可验证发送清空、延迟输入回填和中文输入法回车行为，使用模拟后端。

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
