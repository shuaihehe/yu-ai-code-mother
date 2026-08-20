/**
 * 后端能力开关。
 *
 * 对话历史接口已经接入，默认开启；代码下载接口仍在开发中，默认关闭。
 * 环境变量可用于临时覆盖默认值，方便前后端分阶段联调。
 */
const isEnabled = (value: string | undefined, defaultValue: boolean) => {
  if (value === undefined || value === '') {
    return defaultValue
  }
  return value === 'true'
}

export const BACKEND_FEATURES = Object.freeze({
  chatHistory: isEnabled(import.meta.env.VITE_ENABLE_CHAT_HISTORY, true),
  codeDownload: isEnabled(import.meta.env.VITE_ENABLE_CODE_DOWNLOAD, false),
})
