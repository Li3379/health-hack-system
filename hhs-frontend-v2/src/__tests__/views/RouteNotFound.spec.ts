import { describe, it, expect, vi, beforeAll } from 'vitest'
import { createRouter, createMemoryHistory, type RouteRecordRaw } from 'vue-router'

/**
 * 路由级 404 的钉子（Phase 14 / UI-SPEC Contract 3 §3.6）。
 *
 * 三件事必须同时成立，缺一条这个 catch-all 就是净损失：
 *   1. 路由表里确实有一条 catch-all，名字是 NotFound；
 *   2. 任意未匹配 URL 解析到 NotFound；
 *   3. **既有路由一个都没被遮蔽** —— 这是真正重要的那条。vue-router 对同级路由
 *      按声明顺序匹配，catch-all 若被放在前面，`/dashboard` 会解析成 404。
 *
 * 断言对象是「路由表」，不是浏览器历史，因此用 createMemoryHistory 在用例内自建
 * router，而不是 import 生产 router 单例（那会在 happy-dom 下触发 createWebHistory
 * 与 pinia 依赖）。路由表本身从模块里只读导出。
 */

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ isAuthenticated: true, user: { id: 1 } })
}))

let routes: RouteRecordRaw[]
let router: ReturnType<typeof createRouter>

beforeAll(async () => {
  const mod = await import('@/router')
  routes = mod.routes
  router = createRouter({ history: createMemoryHistory(), routes })
})

describe('router — 路由级 404 catch-all', () => {
  it('路由表末尾存在 pathMatch catch-all，且名为 NotFound', () => {
    const catchAll = router.getRoutes().filter(record => record.path.includes('pathMatch'))
    expect(catchAll).toHaveLength(1)
    expect(catchAll[0].name).toBe('NotFound')
  })

  it('catch-all 是布局 children 数组的最后一项（声明顺序即防遮蔽手段）', () => {
    const layout = routes.find(record => record.path === '/' && Array.isArray(record.children))
    expect(layout).toBeDefined()
    const children = layout?.children ?? []
    expect(children.length).toBeGreaterThan(0)

    const last = children[children.length - 1]
    expect(last.path).toBe('/:pathMatch(.*)*')
    expect(last.name).toBe('NotFound')
  })

  it('catch-all 合并父级 meta 后仍不带 requiresAuth / requiresAdmin —— 未知 URL 不得成为导航绕过', () => {
    // 读 getRoutes() 的 record 是不够的：那是**未合并**的记录，而守卫读的是
    // router.resolve() 合并后的 meta。NotFound 是父级 `/`（requiresAuth: true）的
    // 子项，只"不写" meta 会继承 true，未登录用户访问未知 URL 会被踢去登录页。
    // 因此这里必须断言 resolve() 的结果。
    const resolved = router.resolve('/no-such-page-at-all')
    expect(resolved.name).toBe('NotFound')
    expect(resolved.meta.requiresAuth).toBe(false)
    expect(resolved.meta.requiresAdmin).toBeUndefined()

    const record = router.getRoutes().find(r => r.name === 'NotFound')
    expect(record).toBeDefined()
    expect(record?.meta.requiresAuth).toBe(false)
    expect(record?.meta.requiresAdmin).toBeUndefined()
  })

  it('未匹配 URL 解析到 NotFound', () => {
    expect(router.resolve('/no-such-page-at-all').name).toBe('NotFound')
    expect(router.resolve('/a/b/c/d/e').name).toBe('NotFound')
  })

  it('既有路由未被遮蔽：/dashboard 仍解析到 Dashboard', () => {
    // 这条是遮蔽守卫里最关键的一条。
    expect(router.resolve('/dashboard').name).toBe('Dashboard')
  })

  it('既有路由未被遮蔽：嵌套布局子路由仍解析到自身', () => {
    expect(router.resolve('/health/metrics').name).toBe('HealthMetrics')
    expect(router.resolve('/prevention/profile').name).toBe('PreventionProfile')
    expect(router.resolve('/settings/device-platform').name).toBe('DevicePlatformConfig')
  })

  it('既有路由未被遮蔽：参数化路由仍解析到自身并带上参数', () => {
    const detail = router.resolve('/screening/detail/42')
    expect(detail.name).toBe('ScreeningDetail')
    expect(detail.params.id).toBe('42')

    const compare = router.resolve('/screening/compare/1/2')
    expect(compare.name).toBe('ScreeningCompare')
    expect(compare.params.idA).toBe('1')
    expect(compare.params.idB).toBe('2')
  })

  it('既有路由未被遮蔽：布局外路由仍解析到自身', () => {
    expect(router.resolve('/login').name).toBe('Login')
    expect(router.resolve('/oauth/callback/github').name).toBe('OAuthCallback')
  })
})
