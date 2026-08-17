/**
 * 后端使用 Long 类型的雪花 ID，并在 JSON 中序列化为字符串以避免精度丢失。
 */
export type EntityId = string | number

/** 将路由或接口中的实体 ID 安全转换为十进制字符串。 */
export const normalizeEntityId = (value: unknown): string | undefined => {
  const id = String(value ?? '').trim()
  return /^[1-9]\d*$/.test(id) ? id : undefined
}

/** 比较两个可能来自不同接口的实体 ID。 */
export const isSameEntityId = (left: unknown, right: unknown) => {
  const normalizedLeft = normalizeEntityId(left)
  const normalizedRight = normalizeEntityId(right)
  return normalizedLeft !== undefined && normalizedLeft === normalizedRight
}
