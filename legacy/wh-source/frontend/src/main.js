import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'
import './style.css'

// 注册Service Worker (PWA)
import './registerServiceWorker'

const app = createApp(App)

app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 全局错误处理
app.config.errorHandler = (err, vm, info) => {
    console.error('[Global Error]:', err, info)
}

app.mount('#app')
