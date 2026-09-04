import { createApp, nextTick } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import Antd from 'ant-design-vue'
import AppChatPage from '../src/pages/app/AppChatPage.vue'
import request from '../src/request'
import { useLoginUserStore } from '../src/stores/loginUser'

// 挂载真实 AppChatPage 和 Ant Design Vue 输入组件，不连接后端或 AI。
const originalAdapter = request.defaults.adapter
const originalFetch = window.fetch
const originalEventSource = window.EventSource
let latestSource: TestEventSource | undefined
const sentPrompts: string[] = []
class TestEventSource extends EventTarget {
  static CONNECTING = 0
  readyState = 1
  onmessage: ((event: MessageEvent) => void) | null = null
  onerror: (() => void) | null = null
  constructor(url: string) {
    super()
    sentPrompts.push(new URL(url, location.href).searchParams.get('message') || '')
    latestSource = this
  }
  close() {
    this.readyState = 2
  }
}
window.EventSource = TestEventSource as unknown as typeof EventSource
window.fetch = async () => new Response('', { status: 404 })
request.defaults.adapter = async (config) => ({
  config,
  status: 200,
  statusText: 'OK',
  headers: {},
  data: {
    code: 0,
    data: config.url?.includes('/app/get/vo')
      ? { id: '999999', userId: '1', appName: '输入回归测试', codeGenType: 'html', initPrompt: '' }
      : { records: [] },
  },
})
const pinia = createPinia()
useLoginUserStore(pinia).setLoginUser({ id: '1', userName: '测试用户' })
const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/app/chat/:id', component: AppChatPage }],
})
await router.push('/app/chat/999999')
const app = createApp(AppChatPage).use(pinia).use(router).use(Antd)
app.mount('#app')
const tick = async () => {
  await nextTick()
  await new Promise((resolve) => setTimeout(resolve, 30))
}
const getInput = () => document.querySelector<HTMLTextAreaElement>('#app textarea')!
const send = () =>
  document.querySelector<HTMLButtonElement>('#app [aria-label="发送消息"]')!.click()
const setText = async (text: string) => {
  const input = getInput()
  input.value = text
  input.dispatchEvent(new Event('input', { bubbles: true }))
  await tick()
}
const finish = async () => {
  latestSource?.onmessage?.(
    new MessageEvent('message', { data: JSON.stringify({ d: '模拟生成完成' }) }),
  )
  latestSource?.dispatchEvent(new Event('done'))
  await tick()
}
let passed = 0
let failed = 0
const check = (name: string, condition: boolean) => {
  const item = document.createElement('li')
  item.textContent = `${condition ? '通过' : '失败'}：${name}`
  document.querySelector('#results')!.appendChild(item)
  if (condition) passed += 1
  else failed += 1
}
try {
  await tick()
  await setText('我是帅帅')
  send()
  await tick()
  check('点击发送后输入框立即清空', getInput().value === '')
  check('发送给后端的仍是完整提示词', sentPrompts.at(-1) === '我是帅帅')
  // 模拟清空后迟到的 input/change（输入法或失焦触发），不应重新写入受控值。
  const input = getInput()
  input.value = '我是帅帅'
  input.dispatchEvent(new Event('input', { bubbles: true }))
  input.dispatchEvent(new Event('change', { bubbles: true }))
  await tick()
  await finish()
  check('生成完成后不会被延迟输入事件回填', getInput().value === '')

  const beforeComposition = sentPrompts.length
  getInput().dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true }))
  await setText('新的中文内容')
  getInput().dispatchEvent(
    new KeyboardEvent('keydown', {
      key: 'Enter',
      code: 'Enter',
      keyCode: 229,
      isComposing: true,
      bubbles: true,
      cancelable: true,
    }),
  )
  await tick()
  check('输入法确认候选词时不提前发送', sentPrompts.length === beforeComposition)
  getInput().dispatchEvent(
    new CompositionEvent('compositionend', { data: '新的中文内容', bubbles: true }),
  )
  await tick()
  getInput().dispatchEvent(
    new KeyboardEvent('keydown', {
      key: 'Enter',
      code: 'Enter',
      keyCode: 13,
      bubbles: true,
      cancelable: true,
    }),
  )
  await tick()
  check(
    '输入法结束后回车正常发送且立即清空',
    sentPrompts.at(-1) === '新的中文内容' && getInput().value === '',
  )
  await finish()
  check('回车发送后生成结束输入仍为空', getInput().value === '')
  await setText('保留用于换行')
  const beforeShift = sentPrompts.length
  const shiftEnter = new KeyboardEvent('keydown', {
    key: 'Enter',
    shiftKey: true,
    bubbles: true,
    cancelable: true,
  })
  getInput().dispatchEvent(shiftEnter)
  await tick()
  check(
    'Shift+Enter 保留换行，不发送或清空',
    !shiftEnter.defaultPrevented &&
      sentPrompts.length === beforeShift &&
      getInput().value === '保留用于换行',
  )
} catch (error) {
  check(String(error), false)
} finally {
  app.unmount()
  request.defaults.adapter = originalAdapter
  window.fetch = originalFetch
  window.EventSource = originalEventSource
  document.querySelector('#status')!.textContent = `${passed} 项通过，${failed} 项失败`
  document.title = `${failed ? 'FAIL' : 'PASS'} - 对话输入清空回归测试`
}
