import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import App from './App.vue'
import router from './router'
import './assets/styles/theme.css'
import './assets/styles/3d-enhance.css'
import './assets/styles/main.css'
import { useThemeStore } from './stores/theme'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 初始化主题
const themeStore = useThemeStore()
themeStore.initTheme()

// 注册 ECharts 自定义主题
import { registerHhsTheme } from './utils/echarts-theme'
registerHhsTheme()

app.mount('#app')
