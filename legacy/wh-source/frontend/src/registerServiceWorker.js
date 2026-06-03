import { registerSW } from 'virtual:pwa-register'

// 注册Service Worker
const updateSW = registerSW({
    onNeedRefresh() {
        // 当有新版本可用时
        console.log('发现新版本，准备更新...')
        // 可以显示提示让用户刷新
        if (confirm('发现新版本，是否立即更新？')) {
            updateSW(true)
        }
    },
    onOfflineReady() {
        // 当应用准备好离线使用时（虽然我们不需要离线功能，但这是PWA的标准回调）
        console.log('应用已准备就绪')
    },
    onRegistered(registration) {
        // Service Worker注册成功
        console.log('Service Worker 已注册')

        // 每小时检查一次更新
        if (registration) {
            setInterval(() => {
                registration.update()
            }, 60 * 60 * 1000) // 1小时
        }
    },
    onRegisterError(error) {
        console.error('Service Worker 注册失败:', error)
    }
})

export { updateSW }
