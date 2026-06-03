import { ref, onMounted, onUnmounted } from 'vue'

/**
 * 响应式窗口尺寸监听
 */
export function useResponsive() {
    const windowWidth = ref(window.innerWidth)
    const windowHeight = ref(window.innerHeight)

    const updateWindowSize = () => {
        windowWidth.value = window.innerWidth
        windowHeight.value = window.innerHeight
    }

    onMounted(() => {
        window.addEventListener('resize', updateWindowSize)
    })

    onUnmounted(() => {
        window.removeEventListener('resize', updateWindowSize)
    })

    // 断点判断
    const breakpoints = {
        xs: 480,
        sm: 768,
        md: 1024,
        lg: 1280,
        xl: 1920
    }

    const isXs = () => windowWidth.value < breakpoints.xs
    const isSm = () => windowWidth.value >= breakpoints.xs && windowWidth.value < breakpoints.sm
    const isMd = () => windowWidth.value >= breakpoints.sm && windowWidth.value < breakpoints.md
    const isLg = () => windowWidth.value >= breakpoints.md && windowWidth.value < breakpoints.lg
    const isXl = () => windowWidth.value >= breakpoints.lg

    return {
        windowWidth,
        windowHeight,
        breakpoints,
        isXs,
        isSm,
        isMd,
        isLg,
        isXl
    }
}
