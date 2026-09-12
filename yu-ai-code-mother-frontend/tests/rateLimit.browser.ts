import { createApp, nextTick } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import Antd from 'ant-design-vue'
import AppChatPage from '../src/pages/app/AppChatPage.vue'
import request from '../src/request'
import { useLoginUserStore } from '../src/stores/loginUser'

// 默认使用单独启动的最新后端；测试期间不要从同一 IP 提交其他生成请求。
const backend = 'http://127.0.0.1:8124/api'
const originalAdapter = request.defaults.adapter
const originalBaseURL = request.defaults.baseURL
const NativeEventSource = window.EventSource
let connection: EventSource | undefined
let connections = 0
let receivedError: { code?: number; message?: string } | undefined
const status = document.querySelector('#status')!
let passed = 0
let failed = 0
const check = (label: string, condition: boolean) => {
  const item = document.createElement('li')
  item.textContent = `${condition ? '通过' : '失败'}：${label}`
  document.querySelector('#results')!.appendChild(item)
  if (condition) passed++
  else failed++
}
const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))
const waitFor = async (predicate: () => boolean, label: string) => {
  const deadline = Date.now() + 10000
  while (!predicate()) {
    if (Date.now() > deadline) throw new Error(`等待超时：${label}`)
    await delay(50)
  }
}
const probe = async () => {
  const response = await fetch(`${backend}/app/chat/gen/code?appId=0&message=rate-limit-test`, {
    credentials: 'include',
    headers: { Accept: 'text/event-stream' },
    signal: AbortSignal.timeout(10000),
  })
  const body = await response.text()
  if (!response.ok || !response.headers.get('content-type')?.includes('text/event-stream')) {
    throw new Error(`后端未返回 SSE：HTTP ${response.status} ${body}`)
  }
  const data = body.match(/event: business-error\r?\ndata: (.+)/)?.[1]
  if (!data) throw new Error(`缺少 business-error 事件：${body}`)
  return { code: JSON.parse(data).code as number, body }
}

class ObservedEventSource extends NativeEventSource {
  constructor(url: string | URL, options?: EventSourceInit) {
    super(url, options)
    connection = this
    connections++
    this.addEventListener('business-error', (event: MessageEvent) => {
      receivedError = JSON.parse(event.data)
    })
  }
}
window.EventSource = ObservedEventSource
request.defaults.baseURL = backend
request.defaults.adapter = async (config) => ({
  config,
  status: 200,
  statusText: 'OK',
  headers: {},
  data: {
    code: 0,
    data: config.url?.includes('/app/get/vo')
      ? { id: '999999', userId: '1', appName: '限流测试（页面资料模拟，不调用 AI）', codeGenType: 'html', initPrompt: '' }
      : { records: [] },
  },
})
const pinia = createPinia()
useLoginUserStore(pinia).setLoginUser({ id: '1', userName: '页面测试用户（非后端登录）' })
const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/app/chat/:id', component: AppChatPage }],
})
await router.push('/app/chat/999999')
const app = createApp(AppChatPage).use(pinia).use(router).use(Antd)
app.mount('#app')

try {
  status.textContent = '等待 61 秒，让已有请求的限流窗口自然过期…'
  await delay(61000)
  status.textContent = '验证前 5 次请求和第 6 次页面请求…'
  for (let i = 1; i <= 5; i++) {
    const result = await probe()
    check(`第 ${i} 次通过限流，进入参数校验（40000）`, result.code === 40000)
    check(`第 ${i} 次业务错误之后包含 done 事件`, result.body.includes('event: done'))
  }
  const input = document.querySelector<HTMLTextAreaElement>('#app textarea')!
  input.value = '限流联调测试'
  input.dispatchEvent(new Event('input', { bubbles: true }))
  await nextTick()
  document.querySelector<HTMLButtonElement>('#app [aria-label="发送消息"]')!.click()
  await waitFor(() => !!receivedError, '页面收到业务错误')
  await nextTick()
  check('第 6 次返回业务错误码 42900', receivedError?.code === 42900)
  const expected = 'AI 对话请求过于频繁，请稍后再试'
  check('后端返回具体限流文案', receivedError?.message === expected)
  check('聊天消息显示具体限流文案', !!document.querySelector('.ai-message')?.textContent?.includes(`❌ ${expected}`))
  await waitFor(() => !!document.querySelector('.ant-message-error'), '错误提示弹窗')
  check('弹窗显示具体限流文案', !!document.querySelector('.ant-message-error')?.textContent?.includes(expected))
  check('AI 加载状态已清除', !document.querySelector('.loading-indicator'))
  check('原生 SSE 连接已关闭', connection?.readyState === NativeEventSource.CLOSED)
  input.value = '可再次发送'
  input.dispatchEvent(new Event('input', { bubbles: true }))
  await nextTick()
  check('发送按钮恢复可用', !document.querySelector<HTMLButtonElement>('#app [aria-label="发送消息"]')!.disabled)
  const blocked = await probe()
  check('窗口内继续请求仍返回 42900，并附带 done', blocked.code === 42900 && blocked.body.includes('event: done'))
  status.textContent = '前端限流提示已验证，等待 61 秒检查额度恢复与自动重连…'
  await delay(61000)
  check('失败结束后没有创建额外连接，原连接持续关闭', connections === 1 && connection?.readyState === NativeEventSource.CLOSED)
  check('迟到的 done 没有覆盖聊天错误消息', !!document.querySelector('.ai-message')?.textContent?.includes(`❌ ${expected}`))
  const recovered = await probe()
  check('60 秒窗口后恢复放行，重新进入参数校验（40000）', recovered.code === 40000)
} catch (error) {
  check(String(error), false)
} finally {
  connection?.close()
  request.defaults.adapter = originalAdapter
  request.defaults.baseURL = originalBaseURL
  window.EventSource = NativeEventSource
  status.textContent = `${passed} 项通过，${failed} 项失败。测试结束，未调用 AI。`
  document.title = `${failed ? 'FAIL' : 'PASS'} - 限流联调测试`
}
