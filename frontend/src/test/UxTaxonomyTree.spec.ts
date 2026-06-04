import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import UxTaxonomyTree from '../components/UxTaxonomyTree.vue'
import type { UxLabelOption } from '../types/domain'

function makeLabels(): UxLabelOption[] {
  return [
    {
      id: 'hardware-connection',
      uxPrimaryLabel: '产品硬件',
      uxSecondaryLabel: '连接与稳定性',
      description: '蓝牙连接、断连、卡顿',
      enabled: true,
    },
    {
      id: 'hardware-battery',
      uxPrimaryLabel: '产品硬件',
      uxSecondaryLabel: '续航与充电',
      enabled: false,
    },
    {
      id: 'service-logistics',
      uxPrimaryLabel: '服务体验',
      uxSecondaryLabel: '物流与售后',
      enabled: true,
    },
  ]
}

function latestLabels(wrapper: ReturnType<typeof mount<typeof UxTaxonomyTree>>): UxLabelOption[] {
  const events = wrapper.emitted('update:labels')
  expect(events).toBeTruthy()
  return events?.at(-1)?.[0] as UxLabelOption[]
}

describe('UxTaxonomyTree', () => {
  it('groups secondary labels by primary label in a tree layout', () => {
    const wrapper = mount(UxTaxonomyTree, {
      props: {
        labels: makeLabels(),
      },
    })

    const primaryGroups = wrapper.findAll('[data-testid="ux-tree-primary-group"]')
    const secondaryNodes = wrapper.findAll('[data-testid="ux-tree-secondary-node"]')

    expect(primaryGroups).toHaveLength(2)
    expect(secondaryNodes).toHaveLength(3)
    expect(wrapper.text()).toContain('产品硬件')
    expect(wrapper.text()).toContain('连接与稳定性')
    expect(wrapper.text()).toContain('续航与充电')
    expect(wrapper.text()).toContain('服务体验')
    expect(wrapper.text()).toContain('物流与售后')
    expect(wrapper.text()).toContain('2 / 3 启用')
    expect(wrapper.text()).toContain('1 / 2')
  })

  it('emits updated labels when a secondary label is toggled', async () => {
    const wrapper = mount(UxTaxonomyTree, {
      props: {
        labels: makeLabels(),
      },
    })

    await wrapper.findAll('[data-testid="ux-tree-enabled-toggle"]')[0].setValue(false)

    const emittedLabels = latestLabels(wrapper)
    expect(emittedLabels).toHaveLength(3)
    expect(emittedLabels[0]).toEqual(
      expect.objectContaining({
        id: 'hardware-connection',
        enabled: false,
      }),
    )
    expect(emittedLabels[1]).toEqual(
      expect.objectContaining({
        id: 'hardware-battery',
        enabled: false,
      }),
    )
  })

  it('emits updated labels when adding a secondary label under an existing primary label', async () => {
    const wrapper = mount(UxTaxonomyTree, {
      props: {
        labels: makeLabels(),
      },
    })

    await wrapper.get('[data-testid="ux-tree-add-input-产品硬件"]').setValue('佩戴舒适度')
    await wrapper.get('form.ux-tree__add').trigger('submit')

    const emittedLabels = latestLabels(wrapper)
    expect(emittedLabels).toHaveLength(4)
    expect(emittedLabels.at(-1)).toEqual(
      expect.objectContaining({
        uxPrimaryLabel: '产品硬件',
        uxSecondaryLabel: '佩戴舒适度',
        enabled: true,
      }),
    )
  })

  it('emits updated labels when deleting a secondary label', async () => {
    const wrapper = mount(UxTaxonomyTree, {
      props: {
        labels: makeLabels(),
      },
    })

    await wrapper.findAll('[data-testid="ux-tree-delete"]')[1].trigger('click')

    const emittedLabels = latestLabels(wrapper)
    expect(emittedLabels).toHaveLength(2)
    expect(emittedLabels.map((label) => label.id)).toEqual(['hardware-connection', 'service-logistics'])
  })

  it('shows an empty fallback and emits a root label when no labels exist', async () => {
    const wrapper = mount(UxTaxonomyTree, {
      props: {
        labels: [],
      },
    })

    expect(wrapper.get('[data-testid="ux-tree-empty"]').text()).toContain('暂无 UX 标签')

    await wrapper.get('[data-testid="ux-tree-new-primary"]').setValue('服务体验')
    await wrapper.get('[data-testid="ux-tree-new-secondary"]').setValue('安装与客服')
    await wrapper.get('.ux-tree__new-root button').trigger('click')

    const emittedLabels = latestLabels(wrapper)
    expect(emittedLabels).toHaveLength(1)
    expect(emittedLabels[0]).toEqual(
      expect.objectContaining({
        uxPrimaryLabel: '服务体验',
        uxSecondaryLabel: '安装与客服',
        enabled: true,
      }),
    )
  })

  it('hides editing controls in readonly mode', () => {
    const wrapper = mount(UxTaxonomyTree, {
      props: {
        labels: makeLabels(),
        readonly: true,
      },
    })

    expect(wrapper.find('[data-testid="ux-tree-enabled-toggle"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="ux-tree-delete"]').exists()).toBe(false)
    expect(wrapper.find('form.ux-tree__add').exists()).toBe(false)
    expect(wrapper.text()).toContain('连接与稳定性')
    expect(wrapper.text()).toContain('停用')
  })
})
