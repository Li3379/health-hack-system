<template>
  <!--
    路由级 404：薄视图，不新建第三个页面级组件 —— 页级几何只存在于共享的错误块
    组件里（UI-SPEC Contract 3 §3.6）。与 403 页级块的三处差异只有：
    variant=notFound（DocumentDelete 图标）、标题「页面不存在」、
    动作回到单个「返回首页」（此时没有「列表」可回）。
  -->
  <ErrorState
    scope="page"
    variant="notFound"
    title="页面不存在"
    description="该页面可能已下线，或链接地址有误。"
    :actions="[{ label: '返回首页', onClick: goHome, variant: 'primary' }]"
  />
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import ErrorState from '@/components/common/ErrorState.vue'

const router = useRouter()

const goHome = () => {
  router.push('/dashboard')
}
</script>
