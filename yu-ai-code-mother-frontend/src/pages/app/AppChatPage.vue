<template>
  <div id="appChatPage">
    <!-- 顶部栏 -->
    <div class="header-bar">
      <div class="header-left">
        <h1 class="app-name">{{ appInfo?.appName || '网站生成器' }}</h1>
        <a-tag v-if="appInfo?.codeGenType" color="blue" class="code-gen-type-tag">
          {{ formatCodeGenType(appInfo.codeGenType) }}
        </a-tag>
      </div>
      <div class="header-right">
        <a-button type="default" @click="showAppDetail">
          <template #icon>
            <InfoCircleOutlined />
          </template>
          应用详情
        </a-button>
        <a-button
          type="primary"
          ghost
          @click="downloadCode"
          :loading="downloading"
          :disabled="!isOwner"
        >
          <template #icon>
            <DownloadOutlined />
          </template>
          下载代码
        </a-button>
        <a-button
          type="primary"
          @click="deployApp"
          :loading="deploying"
          :disabled="!isOwner || isGenerating || isPreparingPreview || !previewUrl"
          :title="isOwner ? '将当前生成结果部署到服务器' : '只能部署自己的应用'"
        >
          <template #icon>
            <CloudUploadOutlined />
          </template>
          部署
        </a-button>
      </div>
    </div>

    <!-- 主要内容区域 -->
    <div class="main-content">
      <!-- 左侧对话区域 -->
      <div class="chat-section">
        <!-- 消息区域 -->
        <div class="messages-container" ref="messagesContainer">
          <div
            v-if="!isGenerating && messages.length === 0 && isOwner"
            class="empty-chat-guide"
          >
            <div class="empty-chat-icon">✨</div>
            <h3>应用还没有开始生成</h3>
            <p>可以直接使用创建应用时填写的描述开始生成。</p>
            <a-button type="primary" @click="startFromInitialPrompt">
              使用初始描述开始生成
            </a-button>
          </div>
          <!-- 加载更多按钮 -->
          <div
            v-if="BACKEND_FEATURES.chatHistory && canViewChatHistory && hasMoreHistory"
            class="load-more-container"
          >
            <a-button type="link" @click="loadMoreHistory" :loading="loadingHistory" size="small">
              加载更多历史消息
            </a-button>
          </div>
          <div v-for="(message, index) in messages" :key="index" class="message-item">
            <div v-if="message.type === 'user'" class="user-message">
              <div class="message-content">{{ message.content }}</div>
              <div class="message-avatar">
                <a-avatar :src="loginUserStore.loginUser.userAvatar" />
              </div>
            </div>
            <div v-else class="ai-message">
              <div class="message-avatar">
                <a-avatar :src="aiAvatar" />
              </div>
              <div class="message-content">
                <MarkdownRenderer v-if="message.content" :content="message.content" />
                <div v-if="message.loading" class="loading-indicator">
                  <a-spin size="small" />
                  <span>AI 正在思考...</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 选中元素信息展示 -->
        <a-alert
            v-if="selectedElementInfo"
            class="selected-element-alert"
            type="info"
            closable
            @close="clearSelectedElement"
        >
          <template #message>
            <div class="selected-element-info">
              <div class="element-header">
                <span class="element-tag">
                  选中元素：{{ selectedElementInfo.tagName.toLowerCase() }}
                </span>
                <span v-if="selectedElementInfo.id" class="element-id">
                  #{{ selectedElementInfo.id }}
                </span>
                <span v-if="selectedElementInfo.className" class="element-class">
                  .{{ selectedElementInfo.className.split(' ').join('.') }}
                </span>
              </div>
              <div class="element-details">
                <div v-if="selectedElementInfo.textContent" class="element-item">
                  内容: {{ selectedElementInfo.textContent.substring(0, 50) }}
                  {{ selectedElementInfo.textContent.length > 50 ? '...' : '' }}
                </div>
                <div v-if="selectedElementInfo.pagePath" class="element-item">
                  页面路径: {{ selectedElementInfo.pagePath }}
                </div>
                <div class="element-item">
                  选择器:
                  <code class="element-selector-code">{{ selectedElementInfo.selector }}</code>
                </div>
              </div>
            </div>
          </template>
        </a-alert>

        <!-- 用户消息输入框 -->
        <div class="input-container">
          <div class="input-wrapper">
            <a-tooltip v-if="!isOwner" title="无法在别人的作品下对话哦~" placement="top">
              <a-textarea
                  :key="inputRevision"
                  :value="userInput"
                  @update:value="handleUserInputChange"
                  :placeholder="getInputPlaceholder()"
                  :rows="4"
                  :maxlength="1000"
                  @keydown="handleInputKeydown"
                  @compositionstart="isInputComposing = true"
                  @compositionend="isInputComposing = false"
                  :disabled="isGenerating || !isOwner"
              />
            </a-tooltip>
            <a-textarea
                v-else
                :key="inputRevision"
                :value="userInput"
                @update:value="handleUserInputChange"
                :placeholder="getInputPlaceholder()"
                :rows="4"
                :maxlength="1000"
                @keydown="handleInputKeydown"
                @compositionstart="isInputComposing = true"
                @compositionend="isInputComposing = false"
                :disabled="isGenerating"
            />
            <div class="input-actions">
              <a-button
                :type="isEditMode ? 'primary' : 'default'"
                :aria-pressed="isEditMode"
                :disabled="!isOwner || isGenerating || isPreparingPreview || !previewReady"
                :title="isEditMode ? '退出编辑并清除选中元素' : '选择预览中的元素后，描述修改要求'"
                @click="toggleEditMode"
              >
                <template #icon>
                  <EditOutlined />
                </template>
                {{ isEditMode ? '退出编辑' : '编辑模式' }}
              </a-button>
              <a-button
                  type="primary"
                  @click="sendMessage"
                  :loading="isGenerating"
                  :disabled="!isOwner"
                  aria-label="发送消息"
              >
                <template #icon>
                  <SendOutlined />
                </template>
              </a-button>
            </div>
          </div>
        </div>
      </div>
      <!-- 右侧网页展示区域 -->
      <div class="preview-section">
        <div class="preview-header">
          <h3>生成后的网页展示</h3>
          <div class="preview-actions">
            <a-button
                v-if="appId && !isGenerating"
                type="link"
                :loading="isPreparingPreview"
                @click="refreshPreview"
            >
              <template #icon>
                <ReloadOutlined />
              </template>
              刷新预览
            </a-button>
            <a-button v-if="previewUrl" type="link" @click="openInNewTab">
              <template #icon>
                <ExportOutlined />
              </template>
              新窗口打开
            </a-button>
          </div>
        </div>
        <div class="preview-content">
          <div v-if="isGenerating || isPreparingPreview" class="preview-loading">
            <a-spin size="large" />
            <p>{{ previewLoadingText }}</p>
            <span v-if="isPreparingPreview && isVueProject" class="preview-loading-tip">
              首次安装依赖并构建可能需要几分钟，请耐心等待
            </span>
          </div>
          <iframe
              v-else-if="previewUrl"
              ref="previewIframe"
              :src="previewUrl"
              title="生成网站预览"
              class="preview-iframe"
              frameborder="0"
              @load="onIframeLoad"
          ></iframe>
          <div v-else class="preview-placeholder">
            <div class="placeholder-icon">🌐</div>
            <p>{{ previewStatus || '网站文件生成完成后将在这里展示' }}</p>
            <a-button
                v-if="appId && messages.length > 0"
                type="primary"
                ghost
                @click="refreshPreview"
            >
              重新检测预览
            </a-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 应用详情弹窗 -->
    <AppDetailModal
        v-model:open="appDetailVisible"
        :app="appInfo"
        :show-actions="isOwner || isAdmin"
        @edit="editApp"
        @delete="deleteApp"
    />

    <!-- 部署成功弹窗 -->
    <DeploySuccessModal
        v-model:open="deployModalVisible"
        :deploy-url="deployUrl"
        @open-site="openDeployedSite"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import {
  getAppVoById,
  deployApp as deployAppApi,
  deleteApp as deleteAppApi,
  downloadAppCode,
} from '@/api/appController'
import { listAppChatHistory } from '@/api/chatHistoryController'
import { CodeGenTypeEnum, formatCodeGenType } from '@/utils/codeGenTypes'
import request from '@/request'

import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import AppDetailModal from '@/components/AppDetailModal.vue'
import DeploySuccessModal from '@/components/DeploySuccessModal.vue'
import aiAvatar from '@/assets/aiAvatar.png'
import { API_BASE_URL, getStaticPreviewUrl } from '@/config/env'
import { BACKEND_FEATURES } from '@/config/features'
import { VisualEditor, buildVisualEditPrompt, type ElementInfo } from '@/utils/visualEditor'
import { isSameEntityId, normalizeEntityId } from '@/utils/entityId'

import {
  CloudUploadOutlined,
  SendOutlined,
  ExportOutlined,
  InfoCircleOutlined,
  DownloadOutlined,
  EditOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

// 应用信息
const appInfo = ref<API.AppVO>()
const appId = ref<string>()

// 对话相关
interface Message {
  type: 'user' | 'ai'
  content: string
  loading?: boolean
  createTime?: string
}

const messages = ref<Message[]>([])
const userInput = ref('')
const inputRevision = ref(0)
const isInputComposing = ref(false)
const isGenerating = ref(false)
const messagesContainer = ref<HTMLElement>()
let activeEventSource: EventSource | null = null

// 对话历史相关
const loadingHistory = ref(false)
const hasMoreHistory = ref(false)
const lastCreateTime = ref<string>()
const historyLoaded = ref(false)

// 预览相关
const previewUrl = ref('')
const previewReady = ref(false)
const previewIframe = ref<HTMLIFrameElement>()
const isPreparingPreview = ref(false)
const previewStatus = ref('')
let previewCheckId = 0

// 部署相关
const deploying = ref(false)
const deployModalVisible = ref(false)
const deployUrl = ref('')

// 下载相关
const downloading = ref(false)

// 可视化编辑相关
const isEditMode = ref(false)
const selectedElementInfo = ref<ElementInfo | null>(null)
const visualEditor = new VisualEditor({
  onElementSelected: (elementInfo: ElementInfo) => {
    if (!isOwner.value || !isEditMode.value || isGenerating.value) return
    selectedElementInfo.value = elementInfo
  },
  onError: (errorMessage) => message.warning(errorMessage),
})

// 权限相关
const isOwner = computed(() => {
  return isSameEntityId(appInfo.value?.userId, loginUserStore.loginUser.id)
})

const isAdmin = computed(() => {
  return loginUserStore.loginUser.userRole === 'admin'
})

const canViewChatHistory = computed(() => isOwner.value || isAdmin.value)

const isVueProject = computed(() => {
  return appInfo.value?.codeGenType === CodeGenTypeEnum.VUE_PROJECT
})

const previewLoadingText = computed(() => {
  if (isGenerating.value) {
    return 'AI 正在生成网站...'
  }
  return isVueProject.value ? '代码已生成，正在构建 Vue 项目...' : '正在准备网站预览...'
})

// 应用详情相关
const appDetailVisible = ref(false)

// 显示应用详情
const showAppDetail = () => {
  appDetailVisible.value = true
}

// 当首次跳转被中断时，允许使用已经保存的初始化提示词继续生成。
const startFromInitialPrompt = async () => {
  const prompt = appInfo.value?.initPrompt?.trim()
  if (!prompt) {
    message.warning('当前应用没有初始化描述，请在下方输入生成要求')
    return
  }
  await sendInitialMessage(prompt)
}

// 加载对话历史
const loadChatHistory = async (isLoadMore = false) => {
  if (!appId.value || loadingHistory.value) return
  if (!canViewChatHistory.value) {
    historyLoaded.value = true
    hasMoreHistory.value = false
    return
  }
  loadingHistory.value = true
  try {
    const params: API.listAppChatHistoryParams = {
      appId: appId.value,
      pageSize: 10,
    }
    // 如果是加载更多，传递最后一条消息的创建时间作为游标
    if (isLoadMore && lastCreateTime.value) {
      params.lastCreateTime = lastCreateTime.value
    }
    const res = await listAppChatHistory(params)
    if (res.data.code === 0 && res.data.data) {
      const chatHistories = res.data.data.records || []
      if (chatHistories.length > 0) {
        // 将对话历史转换为消息格式，并按时间正序排列（老消息在前）
        const historyMessages: Message[] = chatHistories
            .map((chat) => ({
              type: (chat.messageType === 'user' ? 'user' : 'ai') as 'user' | 'ai',
              content: chat.message || '',
              createTime: chat.createTime,
            }))
            .reverse() // 反转数组，让老消息在前
        if (isLoadMore) {
          // 加载更多时，将历史消息添加到开头
          messages.value.unshift(...historyMessages)
        } else {
          // 初始加载，直接设置消息列表
          messages.value = historyMessages
        }
        // 更新游标
        lastCreateTime.value = chatHistories[chatHistories.length - 1]?.createTime
        // 检查是否还有更多历史
        hasMoreHistory.value = chatHistories.length === 10
      } else {
        hasMoreHistory.value = false
      }
      historyLoaded.value = true
    }
  } catch (error) {
    console.error('加载对话历史失败：', error)
    message.error('加载对话历史失败')
  } finally {
    loadingHistory.value = false
  }
}

// 加载更多历史消息
const loadMoreHistory = async () => {
  await loadChatHistory(true)
}

// 获取应用信息
const fetchAppInfo = async () => {
  const routeId = Array.isArray(route.params.id) ? route.params.id[0] : route.params.id
  const id = normalizeEntityId(routeId)
  if (!id) {
    message.error('应用ID不存在')
    await router.push('/')
    return
  }

  appId.value = id

  try {
    const res = await getAppVoById({ id })
    if (res.data.code === 0 && res.data.data) {
      appInfo.value = res.data.data

      if (BACKEND_FEATURES.chatHistory && canViewChatHistory.value) {
        // 后端支持历史记录时，用历史消息判断生成状态。
        await loadChatHistory()
      } else {
        // 对话历史仅允许应用所有者或管理员查看，访客只加载公开预览。
        historyLoaded.value = true
      }

      const shouldTryExistingPreview =
        !canViewChatHistory.value || !BACKEND_FEATURES.chatHistory || messages.value.length >= 2
      if (shouldTryExistingPreview) {
        void preparePreview()
      }

      const autoGenerateRequested = route.query.autoGenerate === '1'
      // 有历史接口时通过空历史判断首次生成；接口被临时关闭时只响应新建应用标记，
      // 避免用户刷新页面时重复调用 AI。
      const shouldAutoGenerate = BACKEND_FEATURES.chatHistory || autoGenerateRequested
      if (
        appInfo.value.initPrompt &&
        isOwner.value &&
        messages.value.length === 0 &&
        historyLoaded.value &&
        shouldAutoGenerate
      ) {
        if (autoGenerateRequested) {
          const query = { ...route.query }
          delete query.autoGenerate
          await router.replace({ path: route.path, query })
        }
        await sendInitialMessage(appInfo.value.initPrompt)
      }
    } else {
      message.error('获取应用信息失败')
      router.push('/')
    }
  } catch (error) {
    console.error('获取应用信息失败：', error)
    message.error('获取应用信息失败')
    router.push('/')
  }
}

// 发送初始消息
const sendInitialMessage = async (prompt: string) => {
  // 添加用户消息
  messages.value.push({
    type: 'user',
    content: prompt,
  })

  // 添加AI消息占位符
  const aiMessageIndex = messages.value.length
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
  })

  await nextTick()
  scrollToBottom()

  // 开始生成
  isGenerating.value = true
  await generateCode(prompt, aiMessageIndex)
}

// 同时重置受控值和 Textarea 内部状态，避免输入法/失焦事件回填已发送文本。
const resetUserInput = () => {
  userInput.value = ''
  isInputComposing.value = false
  inputRevision.value += 1
}

const handleUserInputChange = (value: string) => {
  if (isGenerating.value || !isOwner.value) {
    if (value) resetUserInput()
    return
  }
  userInput.value = value
}

const handleInputKeydown = (event: KeyboardEvent) => {
  if (event.key !== 'Enter' || event.shiftKey) return
  // 229 兼容部分浏览器在输入法确认时未正确设置 isComposing 的情况。
  if (event.isComposing || isInputComposing.value || event.keyCode === 229) return
  event.preventDefault()
  void sendMessage()
}

// 发送消息
const sendMessage = async () => {
  if (!userInput.value.trim() || isGenerating.value || !isOwner.value || isInputComposing.value) {
    return
  }

  const prompt = buildVisualEditPrompt(userInput.value, selectedElementInfo.value)
  // 在异步流程前锁定发送并退出编辑，即使没有选中元素也要清理。
  isGenerating.value = true
  exitEditMode()
  resetUserInput()
  // 添加用户消息（包含元素信息）
  messages.value.push({
    type: 'user',
    content: prompt,
  })

  // 添加AI消息占位符
  const aiMessageIndex = messages.value.length
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
  })

  await nextTick()
  scrollToBottom()

  // 开始生成
  await generateCode(prompt, aiMessageIndex)
}

// 生成代码 - 使用 EventSource 处理流式响应
const generateCode = async (userMessage: string, aiMessageIndex: number) => {
  let eventSource: EventSource | null = null
  let streamCompleted = false
  let previewBaselineSignature: string | null = null

  try {
    activeEventSource?.close()
    cancelPreviewCheck()
    previewStatus.value = ''

    // 记录生成前的预览内容。再次生成时，只有检测到构建产物确实更新后才刷新 iframe，
    // 避免 Vue 异步构建期间误加载上一次的旧页面。
    if (appId.value) {
      const codeGenType = appInfo.value?.codeGenType || CodeGenTypeEnum.HTML
      previewBaselineSignature = await fetchPreviewSignature(
        getStaticPreviewUrl(codeGenType, appId.value),
      )
    }
    // 获取 axios 配置的 baseURL
    const baseURL = request.defaults.baseURL || API_BASE_URL

    // 构建URL参数
    const params = new URLSearchParams({
      appId: String(appId.value ?? ''),
      message: userMessage,
    })

    const url = `${baseURL}/app/chat/gen/code?${params}`

    // 创建 EventSource 连接
    eventSource = new EventSource(url, {
      withCredentials: true,
    })
    activeEventSource = eventSource

    let fullContent = ''

    // 处理接收到的消息
    eventSource.onmessage = function (event) {
      if (streamCompleted) return

      try {
        // 解析JSON包装的数据
        const parsed = JSON.parse(event.data)
        const content = parsed.d

        // 拼接内容
        if (content !== undefined && content !== null) {
          fullContent += content
          const aiMessage = messages.value[aiMessageIndex]
          if (aiMessage) {
            aiMessage.content = fullContent
            aiMessage.loading = false
          }
          scrollToBottom()
        }
      } catch (error) {
        console.error('解析消息失败:', error)
        handleError(error, aiMessageIndex)
      }
    }

    // 处理done事件
    eventSource.addEventListener('done', function () {
      if (streamCompleted) return

      streamCompleted = true
      isGenerating.value = false
      eventSource?.close()
      activeEventSource = null

      void preparePreview({
        waitForBuild: true,
        baselineSignature: previewBaselineSignature,
        notifyOnFailure: true,
      })
    })

    // 处理business-error事件（后端限流等错误）
    eventSource.addEventListener('business-error', function (event: MessageEvent) {
      if (streamCompleted) return

      try {
        const errorData = JSON.parse(event.data)
        console.error('SSE业务错误事件:', errorData)

        // 显示具体的错误信息
        const errorMessage = errorData.message || '生成过程中出现错误'
        const aiMessage = messages.value[aiMessageIndex]
        if (aiMessage) {
          aiMessage.content = `❌ ${errorMessage}`
          aiMessage.loading = false
        }
        message.error(errorMessage)

        streamCompleted = true
        isGenerating.value = false
        eventSource?.close()
        activeEventSource = null
      } catch (parseError) {
        console.error('解析错误事件失败:', parseError, '原始数据:', event.data)
        handleError(new Error('服务器返回错误'), aiMessageIndex)
      }
    })

    // 处理错误
    eventSource.onerror = function () {
      if (streamCompleted || !isGenerating.value) return
      // EventSource 在连接中断时也会进入 CONNECTING。关闭自动重连，避免同一条生成请求
      // 被浏览器重复提交；只有已经收到过内容时，才把它作为缺少 done 事件的兼容性结束。
      if (eventSource?.readyState === EventSource.CONNECTING) {
        if (!fullContent) {
          handleError(new Error('SSE 连接在收到生成内容前中断'), aiMessageIndex)
          return
        }

        streamCompleted = true
        isGenerating.value = false
        eventSource?.close()
        activeEventSource = null

        void preparePreview({
          waitForBuild: true,
          baselineSignature: previewBaselineSignature,
          notifyOnFailure: true,
        })
      } else {
        handleError(new Error('SSE连接错误'), aiMessageIndex)
      }
    }
  } catch (error) {
    console.error('创建 EventSource 失败：', error)
    activeEventSource = null
    handleError(error, aiMessageIndex)
  }
}

// 错误处理函数
const handleError = (error: unknown, aiMessageIndex: number) => {
  console.error('生成代码失败：', error)
  activeEventSource?.close()
  activeEventSource = null
  const aiMessage = messages.value[aiMessageIndex]
  if (aiMessage) {
    aiMessage.content = '抱歉，生成过程中出现了错误，请重试。'
    aiMessage.loading = false
  }
  message.error('生成失败，请重试')
  isGenerating.value = false
}

interface PreparePreviewOptions {
  waitForBuild?: boolean
  baselineSignature?: string | null
  notifyOnFailure?: boolean
}

const PREVIEW_POLL_INTERVAL = 2000
const VUE_BUILD_MAX_ATTEMPTS = 250
const NORMAL_BUILD_MAX_ATTEMPTS = 20
const EXISTING_PREVIEW_MAX_ATTEMPTS = 3

const delay = (milliseconds: number) => {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds))
}

const addCacheBuster = (url: string) => {
  const separator = url.includes('?') ? '&' : '?'
  return `${url}${separator}_preview=${Date.now()}`
}

const fetchPreviewSignature = async (url: string): Promise<string | null> => {
  try {
    const response = await fetch(addCacheBuster(url), {
      method: 'GET',
      credentials: 'include',
      cache: 'no-store',
    })
    if (!response.ok) {
      return null
    }
    const content = await response.text()
    return [response.headers.get('last-modified') || '', response.headers.get('etag') || '', content].join(
      '|',
    )
  } catch (error) {
    console.debug('预览文件暂不可用：', error)
    return null
  }
}

const cancelPreviewCheck = () => {
  previewCheckId += 1
  isPreparingPreview.value = false
}

// Vue 工程由后端异步执行 npm install 和 npm run build，不能使用固定延迟判断完成。
const preparePreview = async (options: PreparePreviewOptions = {}) => {
  if (!appId.value) return
  exitEditMode()

  const codeGenType = appInfo.value?.codeGenType || CodeGenTypeEnum.HTML
  const targetUrl = getStaticPreviewUrl(codeGenType, appId.value)
  const requestId = ++previewCheckId
  const waitForBuild = options.waitForBuild === true
  const maxAttempts = waitForBuild
    ? codeGenType === CodeGenTypeEnum.VUE_PROJECT
      ? VUE_BUILD_MAX_ATTEMPTS
      : NORMAL_BUILD_MAX_ATTEMPTS
    : EXISTING_PREVIEW_MAX_ATTEMPTS

  isPreparingPreview.value = true
  previewReady.value = false
  previewStatus.value = ''

  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    if (requestId !== previewCheckId) return

    const signature = await fetchPreviewSignature(targetUrl)
    const previewExists = signature !== null
    const previewWasUpdated =
      options.baselineSignature === null ||
      options.baselineSignature === undefined ||
      signature !== options.baselineSignature

    if (previewExists && previewWasUpdated) {
      previewUrl.value = addCacheBuster(targetUrl)
      previewStatus.value = ''
      if (requestId === previewCheckId) {
        isPreparingPreview.value = false
      }
      return
    }

    if (attempt < maxAttempts - 1) {
      await delay(PREVIEW_POLL_INTERVAL)
    }
  }

  if (requestId !== previewCheckId) return

  isPreparingPreview.value = false
  previewUrl.value = ''
  previewStatus.value = isVueProject.value
    ? 'Vue 项目尚未构建完成，请检查后端构建日志后再刷新预览'
    : '预览文件尚未生成，请稍后重试'
  if (options.notifyOnFailure) {
    message.warning(previewStatus.value)
  }
}

const refreshPreview = () => {
  void preparePreview({ notifyOnFailure: true })
}

// 滚动到底部
const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 下载代码
const getDownloadFileName = (contentDisposition: string | undefined) => {
  const fallbackFileName = `${appId.value}.zip`
  if (!contentDisposition) {
    return fallbackFileName
  }

  const encodedFileName = contentDisposition.match(/filename\*\s*=\s*(?:UTF-8'')?([^;]+)/i)?.[1]
  const plainFileName = contentDisposition.match(/filename\s*=\s*(?:"([^"]+)"|([^;]+))/i)
  const fileName = encodedFileName || plainFileName?.[1] || plainFileName?.[2]
  if (!fileName) {
    return fallbackFileName
  }

  const normalizedFileName = fileName.trim().replace(/^"|"$/g, '')
  try {
    return decodeURIComponent(normalizedFileName)
  } catch {
    return normalizedFileName
  }
}

const downloadCode = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }
  if (!isOwner.value) {
    message.warning('只能下载自己的应用代码')
    return
  }

  downloading.value = true
  try {
    const response = await downloadAppCode(
      { appId: appId.value },
      {
        responseType: 'blob',
      },
    )
    const blob = response.data as Blob
    const contentTypeHeader = response.headers['content-type']
    const contentType = typeof contentTypeHeader === 'string' ? contentTypeHeader : blob.type

    // 业务异常也可能以 Blob 形式返回，避免把 JSON 错误信息保存成 ZIP 文件。
    if (!contentType.toLowerCase().includes('application/zip')) {
      let errorMessage = '下载失败，请重试'
      try {
        const errorData = JSON.parse(await blob.text()) as { message?: string }
        errorMessage = errorData.message || errorMessage
      } catch {
        // 响应不是 JSON 时使用统一错误提示。
      }
      throw new Error(errorMessage)
    }

    const contentDispositionHeader = response.headers['content-disposition']
    const fileName = getDownloadFileName(
      typeof contentDispositionHeader === 'string' ? contentDispositionHeader : undefined,
    )
    const downloadUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = fileName
    link.style.display = 'none'
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.setTimeout(() => URL.revokeObjectURL(downloadUrl), 100)

    message.success('代码下载成功')
  } catch (error) {
    console.error('下载失败：', error)
    message.error(error instanceof Error ? error.message : '下载失败，请重试')
  } finally {
    downloading.value = false
  }
}

// 部署应用
const deployApp = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }
  if (!isOwner.value) {
    message.warning('只能部署自己的应用')
    return
  }

  deploying.value = true
  try {
    const res = await deployAppApi({
      appId: appId.value,
    })

    if (res.data.code === 0 && res.data.data) {
      deployUrl.value = res.data.data
      deployModalVisible.value = true
      await fetchAppInfo()
      message.success('部署成功')
    } else {
      message.error('部署失败：' + res.data.message)
    }
  } catch (error) {
    console.error('部署失败：', error)
    message.error('部署失败，请重试')
  } finally {
    deploying.value = false
  }
}

// 在新窗口打开预览
const openInNewTab = () => {
  if (previewUrl.value) {
    window.open(previewUrl.value, '_blank')
  }
}

// 打开部署的网站
const openDeployedSite = () => {
  if (deployUrl.value) {
    window.open(deployUrl.value, '_blank')
  }
}

// iframe加载完成
const onIframeLoad = () => {
  exitEditMode()
  previewReady.value = true
  const iframe = previewIframe.value
  if (iframe) {
    visualEditor.init(iframe)
  }
}

// 编辑应用
const editApp = () => {
  if (appInfo.value?.id) {
    router.push(`/app/edit/${appInfo.value.id}`)
  }
}

// 删除应用
const deleteApp = async () => {
  if (!appInfo.value?.id) return

  try {
    const res = await deleteAppApi({ id: appInfo.value.id })
    if (res.data.code === 0) {
      message.success('删除成功')
      appDetailVisible.value = false
      router.push('/')
    } else {
      message.error('删除失败：' + res.data.message)
    }
  } catch (error) {
    console.error('删除失败：', error)
    message.error('删除失败')
  }
}

// 可视化编辑相关函数
const exitEditMode = () => {
  visualEditor.disableEditMode()
  selectedElementInfo.value = null
  isEditMode.value = false
}

const toggleEditMode = () => {
  if (isEditMode.value) {
    exitEditMode()
    return
  }
  if (!isOwner.value || isGenerating.value || isPreparingPreview.value) return
  if (!previewIframe.value || !previewReady.value) {
    message.warning('请等待页面加载完成')
    return
  }
  isEditMode.value = visualEditor.enableEditMode()
}

const clearSelectedElement = () => {
  selectedElementInfo.value = null
  visualEditor.clearSelection()
}

const getInputPlaceholder = () => {
  if (selectedElementInfo.value) {
    return `正在编辑 ${selectedElementInfo.value.tagName.toLowerCase()} 元素，描述您想要的修改...`
  }
  return '请描述你想生成的网站，越详细效果越好哦'
}

// 页面加载时获取应用信息
onMounted(() => {
  void fetchAppInfo()
})

// 清理资源
onUnmounted(() => {
  activeEventSource?.close()
  activeEventSource = null
  cancelPreviewCheck()
  visualEditor.destroy()
})
</script>

<style scoped>
#appChatPage {
  height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 16px;
  background: #fdfdfd;
}

/* 顶部栏 */
.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.code-gen-type-tag {
  font-size: 12px;
}

.app-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
}

.header-right {
  display: flex;
  gap: 12px;
}

/* 主要内容区域 */
.main-content {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 8px;
  overflow: hidden;
}

/* 左侧对话区域 */
.chat-section {
  flex: 2;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.messages-container {
  flex: 0.9;
  padding: 16px;
  overflow-y: auto;
  scroll-behavior: smooth;
}

.empty-chat-guide {
  min-height: 260px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  color: #64748b;
  text-align: center;
}

.empty-chat-guide h3 {
  margin: 8px 0;
  color: #1e293b;
}

.empty-chat-guide p {
  margin-bottom: 20px;
}

.empty-chat-icon {
  font-size: 36px;
}

.message-item {
  margin-bottom: 12px;
}

.user-message {
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  gap: 8px;
}

.ai-message {
  display: flex;
  justify-content: flex-start;
  align-items: flex-start;
  gap: 8px;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.5;
  word-wrap: break-word;
}

.user-message .message-content {
  background: #1890ff;
  color: white;
}

.ai-message .message-content {
  background: #f5f5f5;
  color: #1a1a1a;
  padding: 8px 12px;
}

.message-avatar {
  flex-shrink: 0;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #666;
}

/* 加载更多按钮 */
.load-more-container {
  text-align: center;
  padding: 8px 0;
  margin-bottom: 16px;
}

/* 输入区域 */
.input-container {
  padding: 16px;
  background: white;
}

.input-wrapper {
  position: relative;
}

.input-wrapper .ant-input {
  padding-bottom: 48px;
}

.input-actions {
  position: absolute;
  bottom: 8px;
  right: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 右侧预览区域 */
.preview-section {
  flex: 3;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #e8e8e8;
}

.preview-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.preview-actions {
  display: flex;
  gap: 8px;
}

.preview-content {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
}

.placeholder-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
}

.preview-loading p {
  margin-top: 16px;
}

.preview-loading-tip {
  margin-top: 4px;
  color: #8c8c8c;
  font-size: 13px;
}

.preview-placeholder .ant-btn {
  margin-top: 12px;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

.selected-element-alert {
  margin: 0 16px;
  flex-shrink: 0;
  max-height: 180px;
  overflow-y: auto;
}

.selected-element-info {
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.element-header {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 8px;
}

.element-item {
  margin-bottom: 4px;
  font-size: 13px;
}

.element-tag {
  font-weight: 600;
  color: #1677ff;
}

.element-id,
.element-class {
  color: #666;
}

.element-selector-code {
  font-family: 'Monaco', 'Menlo', monospace;
  background: #f6f8fa;
  padding: 2px 4px;
  border-radius: 3px;
  font-size: 12px;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .main-content {
    flex-direction: column;
  }

  .chat-section,
  .preview-section {
    flex: none;
    height: 50vh;
  }
}

@media (max-width: 768px) {
  .header-bar {
    padding: 12px 16px;
  }

  .app-name {
    font-size: 16px;
  }

  .main-content {
    padding: 8px;
    gap: 8px;
  }

  .message-content {
    max-width: 85%;
  }
}
</style>
