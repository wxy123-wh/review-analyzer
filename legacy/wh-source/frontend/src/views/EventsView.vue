<script setup>
import { ElMessage } from 'element-plus'
import { reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { createEvent, deleteEvent, fetchEvents } from '../api/events'
import { useGlobalFilters } from '../stores/globalFilters'
import { useMobileDetect } from '../composables/useMobileDetect'

const { isMobile } = useMobileDetect()

const router = useRouter()
const { productId } = useGlobalFilters()

const loading = ref(false)
const listLoading = ref(false)
const events = ref([])

const form = reactive({
  name: '',
  type: 'activity',
  startDate: '',
  endDate: '',
})

function fmtEventType(v) {
  const t = String(v || '').toLowerCase()
  if (t === 'activity') return '活动'
  if (t === 'version') return '版本'
  return t || '-'
}

async function loadEvents() {
  if (!productId.value) {
    events.value = []
    return
  }
  listLoading.value = true
  try {
    events.value = (await fetchEvents({ productId: productId.value })) || []
  } catch (e) {
    ElMessage.error(e?.message || '加载活动列表失败')
  } finally {
    listLoading.value = false
  }
}

async function submit() {
  if (!productId.value) {
    ElMessage.warning('请先选择产品')
    return
  }
  if (!form.name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  loading.value = true
  try {
    const res = await createEvent({
      productId: productId.value,
      name: form.name,
      type: form.type,
      startDate: form.startDate,
      endDate: form.endDate,
    })
    ElMessage.success(`创建成功，事件编号：${res?.id}`)
    await loadEvents()
    if (res?.id) {
      router.push({ path: '/before-after', query: { eventId: String(res.id) } })
    }
  } catch (e) {
    ElMessage.error(e?.message || '创建失败')
  } finally {
    loading.value = false
  }
}

function openBeforeAfter(id) {
  if (!id) return
  router.push({ path: '/before-after', query: { eventId: String(id) } })
}

async function removeEvent(id) {
  const eventId = Number(id)
  if (!Number.isFinite(eventId) || eventId <= 0) return
  try {
    await deleteEvent(eventId)
    ElMessage.success('删除成功')
    await loadEvents()
  } catch (e) {
    ElMessage.error(e?.message || '删除失败')
  }
}

watch(productId, loadEvents, { immediate: true })
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <el-row :gutter="12">
        <el-col :xs="24" :lg="10">
          <el-card shadow="never" v-loading="loading">
            <template #header>
              <div class="card__title">创建活动/版本</div>
            </template>

            <el-form label-width="90px" :class="{ 'mobile-form': isMobile }">
              <el-form-item label="名称">
                <el-input v-model="form.name" placeholder="例如：双11活动" />
              </el-form-item>
              <el-form-item label="类型">
                <el-select v-model="form.type" style="width: 100%">
                  <el-option label="活动" value="activity" />
                  <el-option label="版本" value="version" />
                </el-select>
              </el-form-item>
              <el-form-item label="开始日期">
                <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" />
              </el-form-item>
              <el-form-item label="结束日期">
                <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="loading" @click="submit" style="width: 100%">创建</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="14">
          <el-card shadow="never" v-loading="listLoading">
            <template #header>
              <div class="card__title">已创建事件</div>
            </template>

            <div v-if="isMobile" class="mobile-list">
              <div v-for="row in events" :key="row.id" class="mobile-card" @click="openBeforeAfter(row.id)">
                <div class="mobile-card__header">
                  <span class="event-name">{{ row.name }}</span>
                  <el-tag size="small" :type="row.type === 'version' ? 'success' : 'primary'">{{ fmtEventType(row.type) }}</el-tag>
                </div>
                
                <div class="mobile-card__body">
                  <div class="date-range">
                    {{ row.startDate }} ~ {{ row.endDate }}
                  </div>
                </div>
                
                <div class="mobile-card__footer">
                  <span class="event-id">#{{ row.id }}</span>
                  <div class="actions">
                     <el-button size="small" @click.stop="openBeforeAfter(row?.id)">查看对比</el-button>
                     <el-popconfirm title="确认删除该事件？" @confirm="() => removeEvent(row?.id)" width="200">
                        <template #reference>
                          <el-button size="small" type="danger" @click.stop>删除</el-button>
                        </template>
                      </el-popconfirm>
                  </div>
                </div>
              </div>
            </div>

            <el-table v-else :data="events" style="width: 100%" @row-click="(row) => openBeforeAfter(row?.id)">
              <el-table-column prop="id" label="编号" width="90" />
              <el-table-column prop="name" label="名称" min-width="200" />
              <el-table-column prop="type" label="类型" width="110">
                <template #default="{ row }">{{ fmtEventType(row?.type) }}</template>
              </el-table-column>
              <el-table-column prop="startDate" label="开始日期" width="130" />
              <el-table-column prop="endDate" label="结束日期" width="130" />
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" @click.stop="openBeforeAfter(row?.id)">查看对比</el-button>
                  <el-popconfirm title="确认删除该事件？" @confirm="() => removeEvent(row?.id)">
                    <template #reference>
                      <el-button size="small" type="danger" @click.stop>删除</el-button>
                    </template>
                  </el-popconfirm>
                </template>
              </el-table-column>
            </el-table>

            <div class="tip">点击行或「查看对比」跳转到「前后对比」页面。</div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.card__title {
  font-weight: 600;
}
.tip {
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.mobile-form .el-form-item {
  margin-right: 0;
  width: 100%;
}

/* 移动端卡片样式 */
.mobile-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.mobile-card {
  background: var(--neu-bg-light);
  border-radius: 8px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
}
.mobile-card:active {
  background: var(--neu-bg-base);
}
.mobile-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.event-name {
  font-weight: 600;
  font-size: 14px;
}
.mobile-card__body {
  margin-bottom: 12px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}
.mobile-card__footer {
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.event-id {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.actions {
  display: flex;
  gap: 8px;
}
</style>
