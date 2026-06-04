import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const clientMocks = vi.hoisted(() => ({
  fetchTaxonomies: vi.fn(),
  saveTaxonomyDefinition: vi.fn(),
}))

vi.mock('../api/client', () => ({
  DEFAULT_PRODUCT_CODE: 'jd-100127936932',
  fetchTaxonomies: clientMocks.fetchTaxonomies,
  saveTaxonomyDefinition: clientMocks.saveTaxonomyDefinition,
}))

import TaxonomyManagerPanel from '../components/TaxonomyManagerPanel.vue'

describe('TaxonomyManagerPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    clientMocks.fetchTaxonomies.mockResolvedValue([
      {
        productCode: 'jd-100127936932',
        category: 'general-product',
        taxonomyId: 1,
        name: '通用电商 UX 标签',
        labels: [
          {
            id: 'quality-performance',
            uxPrimaryLabel: '产品体验',
            uxSecondaryLabel: '质量与性能',
            enabled: true,
          },
        ],
        state: 'success',
      },
    ])
    clientMocks.saveTaxonomyDefinition.mockImplementation((payload) =>
      Promise.resolve({
        ...payload,
        taxonomyId: payload.taxonomyId ?? 8,
        state: 'success',
        notice: 'taxonomy 已保存。',
      }),
    )
  })

  it('loads existing taxonomies and saves a new taxonomy option', async () => {
    const wrapper = mount(TaxonomyManagerPanel)
    await flushPromises()

    expect(clientMocks.fetchTaxonomies).toHaveBeenCalled()
    expect(wrapper.text()).toContain('通用电商 UX 标签')

    await wrapper.get('[data-testid="taxonomy-new"]').trigger('click')
    await wrapper.get('[data-testid="taxonomy-name"]').setValue('背囊顶品类')
    await wrapper.get('[data-testid="taxonomy-category"]').setValue('backpack-top-category')
    await wrapper.get('[data-testid="taxonomy-new-primary"]').setValue('产品结构')
    await wrapper.get('[data-testid="taxonomy-new-secondary"]').setValue('容量与分区')
    await wrapper.get('[data-testid="taxonomy-add-label"]').trigger('click')
    await wrapper.get('[data-testid="taxonomy-save"]').trigger('click')
    await flushPromises()

    expect(clientMocks.saveTaxonomyDefinition).toHaveBeenCalledWith(expect.objectContaining({
      productCode: 'jd-100127936932',
      name: '背囊顶品类',
      category: 'backpack-top-category',
      labels: expect.arrayContaining([
        expect.objectContaining({
          uxPrimaryLabel: '产品结构',
          uxSecondaryLabel: '容量与分区',
          enabled: true,
        }),
      ]),
    }))
    expect(wrapper.text()).toContain('背囊顶品类')
    expect(wrapper.text()).toContain('商品品类下拉框可以选择它')
  })
})
