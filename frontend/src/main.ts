import { createApp } from 'vue'
import { createPinia } from 'pinia'
// Element Plus 组件按需引入：模板里的 el-* 组件由 unplugin-vue-components 自动注册并附带样式，
// ElMessage / ElMessageBox 这类函数式 API 的样式由 unplugin-element-plus 注入。
import 'element-plus/theme-chalk/base.css'
import App from './App.vue'
import router from './router'
import './styles.css'
import './workspace-theme.css'
import { initTheme } from './composables/useTheme'

// Initialize theme immediately
initTheme()

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
