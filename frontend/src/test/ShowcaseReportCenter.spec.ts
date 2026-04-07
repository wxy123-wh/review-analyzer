import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ShowcaseReportCenter from '../components/ShowcaseReportCenter.vue'

describe('ShowcaseReportCenter', () => {
  it('renders report preview metrics and emits the currently selected module', async () => {
    const wrapper = mount(ShowcaseReportCenter, {
      props: {
        data: {
          status: 'LIVE',
          implemented: true,
          note: '当前预览由真实 issues/compare/trends/actions/validation 查询结果拼装。',
          previewSections: ['执行摘要：当前最高优先级问题是连接稳定性偶发断连。'],
        },
      },
    })

    expect(wrapper.text()).toContain('实时数据')
    expect(wrapper.text()).toContain('目标模块')
    expect(wrapper.text()).toContain('总览')
    expect(wrapper.text()).toContain('预览段落')
    expect(wrapper.text()).toContain('1')
    expect(wrapper.text()).toContain('已生成 1 个预览段落')
    expect(wrapper.findAll('.preview-card')).toHaveLength(1)

    await wrapper.get('select').setValue('showcase')
    expect(wrapper.text()).toContain('演示模块')

    await wrapper.get('.preview-button').trigger('click')
    expect(wrapper.emitted('preview')).toEqual([['showcase']])
  })

  it('shows ready and empty preview states before generation', () => {
    const wrapper = mount(ShowcaseReportCenter, {
      props: {
        data: null,
      },
    })

    expect(wrapper.text()).toContain('就绪')
    expect(wrapper.text()).toContain('等待触发预览动作')
    expect(wrapper.text()).toContain('暂未生成报告预览')
  })
})
