import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import StatusCard from '../components/StatusCard.vue'

describe('StatusCard', () => {
  it.each([
    ['UP', 'up', '正常'],
    ['DOWN', 'down', '异常'],
    ['UNKNOWN', 'unknown', '未知'],
  ] as const)('renders %s status with the expected primitive state cues', (status, cssClass, label) => {
    const wrapper = mount(StatusCard, {
      props: {
        name: 'Backend API',
        status,
      },
    })

    expect(wrapper.classes()).toContain(cssClass)
    expect(wrapper.text()).toContain('状态总览')
    expect(wrapper.text()).toContain('Backend API')
    expect(wrapper.text()).toContain(`状态：${label}`)
    expect(wrapper.get('.status-card__badge').text()).toBe(label)
  })
})
