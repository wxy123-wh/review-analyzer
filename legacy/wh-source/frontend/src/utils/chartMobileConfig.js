/**
 * ECharts 移动端配置工具
 * 根据设备类型返回优化的图表配置
 */

/**
 * 检测是否为移动设备
 */
export function isMobile() {
    return window.innerWidth < 768
}

/**
 * 获取移动端优化的基础配置
 */
export function getMobileBaseConfig() {
    const mobile = isMobile()

    return {
        // 网格配置
        grid: mobile ? {
            left: '12%',
            right: '12%',
            top: '15%',
            bottom: '15%',
            containLabel: true
        } : {
            left: '5%',
            right: '5%',
            top: '10%',
            bottom: '10%',
            containLabel: true
        },

        // 工具栏配置
        toolbox: {
            show: !mobile, // 移动端隐藏工具栏
            feature: {
                saveAsImage: {},
                dataView: { readOnly: false },
                restore: {}
            }
        },

        // 提示框配置
        tooltip: {
            trigger: 'axis',
            confine: true, // 限制在容器内
            textStyle: {
                fontSize: mobile ? 12 : 14
            }
        },

        // 图例配置
        legend: {
            textStyle: {
                fontSize: mobile ? 11 : 12
            },
            itemWidth: mobile ? 20 : 25,
            itemHeight: mobile ? 12 : 14
        },

        // 坐标轴配置
        xAxis: {
            axisLabel: {
                fontSize: mobile ? 10 : 12,
                rotate: mobile ? 45 : 0 // 移动端倾斜标签
            }
        },

        yAxis: {
            axisLabel: {
                fontSize: mobile ? 10 : 12
            }
        }
    }
}

/**
 * 获取词云图移动端配置
 */
export function getWordCloudMobileConfig() {
    const mobile = isMobile()

    return {
        series: [{
            type: 'wordCloud',
            sizeRange: mobile ? [12, 40] : [14, 60],
            rotationRange: mobile ? [0, 0] : [-90, 90], // 移动端不旋转
            rotationStep: 45,
            gridSize: mobile ? 12 : 8,
            drawOutOfBound: false,
            layoutAnimation: true,
            textStyle: {
                fontFamily: 'Inter, sans-serif',
                fontWeight: 'bold'
            },
            emphasis: {
                focus: 'self',
                textStyle: {
                    shadowBlur: 10,
                    shadowColor: '#333'
                }
            }
        }]
    }
}

/**
 * 获取雷达图移动端配置
 */
export function getRadarMobileConfig() {
    const mobile = isMobile()

    return {
        radar: {
            indicator: [],
            radius: mobile ? '55%' : '65%',
            name: {
                textStyle: {
                    fontSize: mobile ? 10 : 12
                }
            }
        },
        series: [{
            type: 'radar',
            symbol: mobile ? 'circle' : 'emptyCircle',
            symbolSize: mobile ? 4 : 6,
            lineStyle: {
                width: mobile ? 1.5 : 2
            }
        }]
    }
}

/**
 * 获取折线图/柱状图移动端配置
 */
export function getLineMobileConfig() {
    const mobile = isMobile()

    return {
        series: {
            type: 'line',
            smooth: true,
            symbolSize: mobile ? 4 : 6,
            lineStyle: {
                width: mobile ? 2 : 3
            }
        }
    }
}

/**
 * 合并移动端配置
 * @param {Object} baseConfig - 基础配置
 * @param {Object} customConfig - 自定义配置
 */
export function mergeMobileConfig(baseConfig, customConfig) {
    const mobileBase = getMobileBaseConfig()

    // 深度合并配置
    return {
        ...mobileBase,
        ...baseConfig,
        ...customConfig,
        grid: {
            ...mobileBase.grid,
            ...baseConfig.grid,
            ...customConfig.grid
        },
        tooltip: {
            ...mobileBase.tooltip,
            ...baseConfig.tooltip,
            ...customConfig.tooltip
        },
        legend: {
            ...mobileBase.legend,
            ...baseConfig.legend,
            ...customConfig.legend
        }
    }
}

/**
 * 为图表启用触摸优化
 * @param {Object} chartInstance - ECharts实例
 */
export function enableTouchOptimization(chartInstance) {
    if (!isMobile()) return

    // 启用触摸缩放
    chartInstance.setOption({
        dataZoom: [
            {
                type: 'inside', // 内置型数据区域缩放
                disabled: false,
                zoomOnMouseWheel: 'ctrl', // 需要按住ctrl才能缩放
                moveOnMouseMove: true,
                moveOnMouseWheel: false
            }
        ]
    })
}
