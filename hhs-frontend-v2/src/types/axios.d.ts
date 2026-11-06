import 'axios'

// D-09: 静默由调用点显式标记，不存在 URL 模式自动判定表。
// 给 axios 请求配置增加 `silent`，调用点通过 `{ silent: true }` 声明"失败时不要打扰用户"。
// 注意：silent 只抑制展示，绝不改变 reject 语义（D-10）。
declare module 'axios' {
  export interface AxiosRequestConfig {
    silent?: boolean
  }
}
