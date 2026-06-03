import { ref, onMounted, onUnmounted } from 'vue'

/**
 * 移动端检测组合式函数
 * 检测当前设备是否为移动端，并提供响应式状态
 */
export function useMobileDetect() {
    const isMobile = ref(false)
    const isTablet = ref(false)
    const isDesktop = ref(false)

    const checkDevice = () => {
        const width = window.innerWidth

        // 移动端：< 768px
        isMobile.value = width < 768

        // 平板：768px - 1024px
        isTablet.value = width >= 768 && width < 1024

        // 桌面：>= 1024px
        isDesktop.value = width >= 1024
    }

    onMounted(() => {
        checkDevice()
        window.addEventListener('resize', checkDevice)
    })

    onUnmounted(() => {
        window.removeEventListener('resize', checkDevice)
    })

    return {
        isMobile,
        isTablet,
        isDesktop
    }
}

/**
 * 检测是否为移动设备（基于User Agent）
 */
export function isMobileDevice() {
    return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(
        navigator.userAgent
    )
}

/**
 * 检测是否为iOS设备
 */
export function isIOS() {
    return /iPhone|iPad|iPod/i.test(navigator.userAgent)
}

/**
 * 检测是否为Android设备
 */
export function isAndroid() {
    return /Android/i.test(navigator.userAgent)
}

/**
 * 检测是否为PWA模式运行
 */
export function isPWA() {
    return (
        window.matchMedia('(display-mode: standalone)').matches ||
        window.navigator.standalone === true
    )
}
