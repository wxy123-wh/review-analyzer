import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import UxChangeComparisonPanel from '../components/UxChangeComparisonPanel.vue'
import type { UxChangeComparisonRecord } from '../types/domain'

const record: UxChangeComparisonRecord = {
  id: 'ux-change-1',
  productCode: 'jd-100127936932',
  productName: '小米 Buds 5 Pro',
  changeDate: '2026-06-03',
  windowPreset: 'ONE_MONTH',
  beforeWindowStart: '2026-05-03',
  beforeWindowEnd: '2026-06-02',
  afterWindowStart: '2026-06-04',
  afterWindowEnd: '2026-07-03',
  state: 'success',
  summary: '连接与稳定性负面率下降。',
  items: [
    {
      uxPrimaryLabel: '产品体验',
      uxSecondaryLabel: '连接与稳定性',
      beforeMentionCount: 42,
      afterMentionCount: 38,
      beforeNegativeRate: 0.48,
      afterNegativeRate: 0.31,
      improvementRate: 0.17,
    },
  ],
}

describe('UxChangeComparisonPanel', () => {
  it('renders history and per-UX-label change metrics', () => {
    const wrapper = mount(UxChangeComparisonPanel, {
      props: {
        productCode: 'jd-100127936932',
        historyItems: [record],
        activeRecord: record,
        state: 'success',
      },
    })

    expect(wrapper.text()).toContain('前后对比')
    expect(wrapper.text()).toContain('小米 Buds 5 Pro')
    expect(wrapper.text()).toContain('jd-100127936932')
    expect(wrapper.text()).toContain('连接与稳定性负面率下降。')
    expect(wrapper.text()).toContain('前后一个月')
    expect(wrapper.text()).toContain('连接与稳定性')
    expect(wrapper.text()).toContain('48.0%')
    expect(wrapper.text()).toContain('+17.0%')
  })

  it('emits create payload with custom windows', async () => {
    const onCreate = vi.fn()
    const wrapper = mount(UxChangeComparisonPanel, {
      props: {
        productCode: 'jd-100127936932',
        historyItems: [],
        activeRecord: null,
        state: 'idle',
        onCreate,
      },
    })

    await wrapper.get('[data-testid="ux-change-date"]').setValue('2026-06-05')
    await wrapper.get('[data-testid="ux-change-window-preset"]').setValue('CUSTOM')
    await wrapper.get('[data-testid="ux-change-before-days"]').setValue(45)
    await wrapper.get('[data-testid="ux-change-after-days"]').setValue(20)
    await wrapper.get('[data-testid="ux-change-form"]').trigger('submit')

    expect(onCreate).toHaveBeenCalledWith({
      productCode: 'jd-100127936932',
      changeDate: '2026-06-05',
      windowPreset: 'CUSTOM',
      customBeforeDays: 45,
      customAfterDays: 20,
    })
  })
})
