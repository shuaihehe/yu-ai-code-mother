import { VisualEditor, buildVisualEditPrompt, type ElementInfo } from '../src/utils/visualEditor'

// 使用真实同源 iframe 验证注入及 postMessage，避免 DOM 模拟漏掉浏览器安全限制。
const results = document.querySelector('#results')!
const status = document.querySelector('#status')!
let passed = 0
let failed = 0
const check = (name: string, condition: boolean) => {
  const item = document.createElement('li')
  item.className = condition ? 'pass' : 'fail'
  item.textContent = `${condition ? '通过' : '失败'}：${name}`
  results.appendChild(item)
  if (condition) passed += 1
  else failed += 1
}
const tick = () => new Promise((resolve) => setTimeout(resolve, 30))
const frame = document.createElement('iframe')
const selected: ElementInfo[] = []
const errors: string[] = []
let envelope: Record<string, unknown> = {}
window.addEventListener('message', (event) => {
  if (event.source === frame.contentWindow && event.data?.type === 'ELEMENT_SELECTED') {
    envelope = event.data
  }
})
const editor = new VisualEditor({
  onElementSelected: (info) => selected.push(info),
  onError: (text) => errors.push(text),
})

try {
  const loaded = new Promise((resolve) => frame.addEventListener('load', resolve, { once: true }))
  frame.src = './visualEditor-fixture.html?_preview=123&view=article#section'
  document.body.appendChild(frame)
  await loaded
  editor.init(frame)
  const doc = frame.contentDocument!
  const win = frame.contentWindow!
  const heading = doc.querySelector('h1')!
  const link = doc.querySelector('a')!
  const hover = (element: Element) =>
    element.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }))
  const click = (element: Element) =>
    element.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }))

  check('开启同源编辑模式成功', editor.enableEditMode())
  hover(heading)
  check('悬浮元素显示浅蓝虚线', win.getComputedStyle(heading).outlineStyle === 'dashed')
  click(heading)
  await tick()
  check(
    '点击后固定深蓝实线，收到 iframe 元素信息',
    win.getComputedStyle(heading).outlineStyle === 'solid' &&
      selected.at(-1)?.textContent === '测试标题',
  )
  check('特殊 ID 的选择器可以准确定位', doc.querySelector(selected.at(-1)!.selector) === heading)
  check('元素上下文不混入高亮样式类', !selected.at(-1)!.className.includes('yu-visual-editor'))
  check(
    '保留页面文件名与路由，剔除缓存参数',
    selected.at(-1)!.pagePath.endsWith('visualEditor-fixture.html?view=article#section'),
  )
  hover(link)
  check('悬浮其他元素不丢失选中边框', win.getComputedStyle(heading).outlineStyle === 'solid')
  const initialHash = win.location.hash
  check('编辑点击链接会阻止默认行为', click(link) === false)
  await tick()
  check('选中链接未触发路由跳转', win.location.hash === initialHash)
  check('含冒号和斜杠的类名选择器可用', doc.querySelector(selected.at(-1)!.selector) === link)
  const rect = doc.querySelector('rect')!
  click(rect)
  await tick()
  check('SVG 元素支持选中和选择器定位', doc.querySelector(selected.at(-1)!.selector) === rect)
  const duplicate = doc.querySelectorAll('#duplicate')[1]!
  click(duplicate)
  await tick()
  check(
    '重复 ID 不会误定位到第一个元素',
    doc.querySelector(selected.at(-1)!.selector) === duplicate,
  )
  const fixed = doc.querySelector('#fixed')!
  hover(fixed)
  click(fixed)
  await tick()
  check('高亮不改变原元素定位布局', win.getComputedStyle(fixed).position === 'fixed')

  const prompt = buildVisualEditPrompt('把选中区域改为蓝色', selected.at(-1)!)
  check(
    '提示词包含要求、选择器、页面路径和文本',
    prompt.includes('把选中区域改为蓝色') &&
      prompt.includes('"selector": "#fixed"') &&
      prompt.includes('"pagePath"') &&
      prompt.includes('固定定位元素'),
  )
  check('未选中时保持普通对话提示词', buildVisualEditPrompt(' 普通对话 ', null) === '普通对话')

  const countBeforeInvalid = selected.length
  for (const init of [
    { data: envelope, source: window, origin: location.origin },
    { data: envelope, source: win, origin: 'https://untrusted.example' },
    { data: { ...envelope, channel: 'wrong-channel' }, source: win, origin: location.origin },
    { data: { ...envelope, elementInfo: { tagName: 123 } }, source: win, origin: location.origin },
    { data: null, source: win, origin: location.origin },
  ])
    window.dispatchEvent(new MessageEvent('message', init))
  check('拒绝错误来源、跨域、错误通道和畸形消息', selected.length === countBeforeInvalid)

  editor.clearSelection()
  check('主动移除选中后清除固定边框', !doc.querySelector('.yu-visual-editor-selected'))
  hover(link)
  check('移除选中后仍可继续悬浮选择', link.classList.contains('yu-visual-editor-hover'))
  editor.disableEditMode()
  check(
    '退出编辑清除全部边框',
    !doc.querySelector('.yu-visual-editor-hover, .yu-visual-editor-selected'),
  )
  window.dispatchEvent(
    new MessageEvent('message', { data: envelope, source: win, origin: location.origin }),
  )
  check('退出后忽略已排队的选中消息', selected.length === countBeforeInvalid)
  hover(heading)
  check('退出后悬浮不再生效', !heading.classList.contains('yu-visual-editor-hover'))
  click(link)
  await tick()
  check('退出后恢复网站链接行为', win.location.hash === '#navigated')

  const styleCount = doc.querySelectorAll('style').length
  editor.enableEditMode()
  editor.disableEditMode()
  editor.enableEditMode()
  check('反复开关不重复注入样式', doc.querySelectorAll('style').length === styleCount)
  const previousCount = selected.length
  click(heading)
  await tick()
  check('反复开关后一次点击只上报一次', selected.length === previousCount + 1)
  editor.init(frame)
  check('重新绑定预览会清除旧元素状态', !doc.querySelector('.yu-visual-editor-selected'))
  editor.enableEditMode()
  editor.destroy()
  hover(heading)
  check(
    '销毁后移除注入样式和事件监听器',
    doc.querySelectorAll('style').length === styleCount - 1 &&
      !heading.classList.contains('yu-visual-editor-hover'),
  )
  check('正常操作没有注入错误', errors.length === 0)

  const crossFrame = document.createElement('iframe')
  crossFrame.src = 'data:text/html,<h1>跨域预览</h1>'
  const crossLoaded = new Promise((resolve) =>
    crossFrame.addEventListener('load', resolve, { once: true }),
  )
  document.body.appendChild(crossFrame)
  await crossLoaded
  const crossEditor = new VisualEditor({ onError: (text) => errors.push(text) })
  crossEditor.init(crossFrame)
  check(
    '非同源预览拒绝编辑并提示原因',
    !crossEditor.enableEditMode() && errors.at(-1)!.includes('同源'),
  )
  crossEditor.destroy()
  crossFrame.remove()
} catch (error) {
  check('测试执行异常：' + String(error), false)
} finally {
  editor.destroy()
  status.textContent = `测试完成：${passed} 项通过，${failed} 项失败`
  status.className = failed ? 'fail' : 'pass'
  document.title = `${failed ? 'FAIL' : 'PASS'} - 可视化编辑回归测试`
}
