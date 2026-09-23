import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import ElementPlus from 'unplugin-element-plus/vite'

export default defineConfig({
  plugins: [
    vue(),
    // 模板中的 el-* 组件按需注册并引入对应样式
    Components({ resolvers: [ElementPlusResolver()], dts: 'src/components.d.ts' }),
    // 代码里显式 import 的 ElMessage / ElMessageBox 自动附带样式
    ElementPlus({})
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8083',
        changeOrigin: true
      }
    }
  }
})
