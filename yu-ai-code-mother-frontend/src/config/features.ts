/**
 * 后端能力开关。
 *
 * 当前后端已经支持应用生成与部署，但尚未提供对话历史和代码下载接口。
 * 后续补齐对应接口后，只需在环境变量中开启，无需重新改页面结构。
 */
const isEnabled = (value: string | undefined) => value === 'true'

export const BACKEND_FEATURES = Object.freeze({
  chatHistory: isEnabled(import.meta.env.VITE_ENABLE_CHAT_HISTORY),
  codeDownload: isEnabled(import.meta.env.VITE_ENABLE_CODE_DOWNLOAD),
})
