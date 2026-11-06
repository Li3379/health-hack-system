// 401 闩锁状态与复位。必须保持零 import：
// request.ts 需要读取/置位闩锁，stores/auth.ts 的 login() 需要复位闩锁，
// 而 stores/auth.ts 若 import request.ts 会与 request.ts -> stores/auth 形成循环。
// 把状态放在这个零依赖模块里，两个方向都能安全引用。

let handling401 = false

export function isHandling401(): boolean {
  return handling401
}

export function markHandling401(): void {
  handling401 = true
}

export function resetAuthLatch(): void {
  handling401 = false
}
