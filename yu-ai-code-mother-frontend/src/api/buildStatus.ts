import request from '@/request'

export interface BuildStatus {
  appId: string | number
  buildId?: string
  status: 'not_found' | 'pending' | 'ready' | 'generating' | 'building' | 'completed' | 'failed'
  message: string
  isBuilding: boolean
  projectExists: boolean
  distExists: boolean
  buildTime?: number
  startedAt?: number
  finishedAt?: number
}

export async function getBuildStatus(appId: string, signal?: AbortSignal): Promise<BuildStatus> {
  const response = await request.get<{ code: number; data: BuildStatus; message?: string }>(
    `/app/build/status/${encodeURIComponent(appId)}`,
    { signal, timeout: 15000, headers: { 'Cache-Control': 'no-cache' }, params: { _t: Date.now() } },
  )
  if (response.data.code !== 0 || !response.data.data) {
    throw new Error(response.data.message || '查询构建状态失败')
  }
  return response.data.data
}
