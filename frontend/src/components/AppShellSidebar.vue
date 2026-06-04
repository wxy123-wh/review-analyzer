<template>
  <aside data-testid="narrow-sidebar" class="sidebar" data-motion-reveal>
    <nav class="nav">
      <section v-for="group in navGroups" :key="group.label" class="nav-group">
        <p class="nav-group-label">{{ group.label }}</p>

        <div class="nav-group-items">
          <button
            v-for="module in group.items"
            :key="module.id"
            :data-testid="`nav-${module.id}`"
            type="button"
            class="nav-item"
            data-motion-hover="lift"
            :class="{ active: activeModule === module.id }"
            :aria-current="activeModule === module.id ? 'page' : undefined"
            @click="emit('select', module.id)"
          >
            <span class="active-rail" aria-hidden="true"></span>
            <span class="dot">{{ module.icon }}</span>
            <span class="label-wrap">
              <span class="label-row">
                <span class="label">{{ module.label }}</span>
              </span>
            </span>
          </button>
        </div>
      </section>
    </nav>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type ShellModuleItem = {
  id: string
  label: string
  icon: string
}

const props = defineProps<{
  items: ShellModuleItem[]
  activeModule: string
}>()

const emit = defineEmits<{
  (event: 'select', moduleId: string): void
}>()

const navGroups = computed(() => {
  return [{ label: '业务模块', items: props.items }]
})
</script>

<style scoped>
.sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-2);
  background: var(--gradient-sidebar);
}

.nav {
  width: 100%;
  display: grid;
  gap: var(--space-3);
  min-height: 0;
  overflow: hidden;
}

.nav-group {
  display: grid;
  gap: var(--space-2);
}

.nav-group-label {
  margin: 0;
  padding: 0 var(--space-2);
  font-size: var(--font-size-2xs);
  letter-spacing: 0;
  color: var(--color-text-muted);
}

.nav-group-items {
  display: grid;
  gap: var(--space-1);
}

.nav-item {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--color-border-subtle);
  background: var(--color-surface-1);
  border-radius: var(--radius-md);
  padding: var(--space-2);
  color: var(--color-text-secondary);
  cursor: pointer;
  display: grid;
  justify-items: center;
  gap: var(--space-1);
  transition:
    border-color var(--motion-medium) var(--easing-standard),
    background-color var(--motion-medium) var(--easing-standard),
    box-shadow var(--motion-medium) var(--easing-standard),
    color var(--motion-medium) var(--easing-standard),
    transform var(--motion-fast) var(--easing-standard);
}

.nav-item:focus-visible {
  border-color: var(--color-border-strong);
  color: var(--color-text-primary);
  box-shadow: var(--shadow-focus), var(--shadow-inset-soft);
}

.active-rail {
  position: absolute;
  top: var(--space-2);
  bottom: var(--space-2);
  left: 0;
  width: 3px;
  border-radius: 0 var(--radius-pill) var(--radius-pill) 0;
  background: transparent;
  transition: background-color var(--motion-medium) var(--easing-standard);
}

@media (hover: hover) {
  .nav-item:hover {
    background: var(--color-canvas-alt);
    border-color: var(--color-border-default);
    color: var(--color-text-primary);
  }
}

.nav-item.active {
  border-color: var(--color-border-strong);
  background: var(--color-accent-soft);
  color: var(--color-text-primary);
}

.dot {
  width: 30px;
  height: 30px;
  border-radius: var(--radius-pill);
  display: grid;
  place-items: center;
  font-size: var(--font-size-xs);
  font-weight: 700;
  background: var(--color-accent-soft);
  color: var(--color-accent-primary);
  transition:
    background-color var(--motion-medium) var(--easing-standard),
    color var(--motion-medium) var(--easing-standard),
    box-shadow var(--motion-medium) var(--easing-standard);
}

.label-wrap {
  display: grid;
  gap: 4px;
  width: 100%;
}

.label-row {
  display: flex;
  align-items: center;
  justify-content: center;
}

.label {
  font-size: var(--font-size-xs);
  line-height: var(--line-height-snug);
  text-align: center;
}

.nav-item.active .active-rail {
  background: var(--color-accent-primary);
}

.nav-item.active .dot {
  background: var(--color-accent-strong);
  color: var(--color-text-primary);
}

@media (max-width: 980px) {
  .sidebar {
    position: static;
    height: auto;
    min-height: auto;
    gap: var(--space-3);
    padding: var(--space-3);
  }

  .sidebar-head {
    display: none;
  }

  .nav {
    gap: var(--space-3);
    overflow-x: auto;
    padding-bottom: var(--space-1);
    scrollbar-width: thin;
    scroll-snap-type: x proximity;
  }

  .nav-group {
    gap: var(--space-2);
  }

  .nav-group-label {
    padding: 0;
  }

  .nav-group-items {
    display: flex;
    gap: var(--space-2);
    flex-wrap: nowrap;
  }

  .nav-item {
    min-width: 78px;
    min-height: 64px;
    padding: var(--space-2);
    touch-action: manipulation;
    scroll-snap-align: start;
  }

  .label {
    font-size: var(--font-size-2xs);
  }
}

html[data-motion='reduce'] .sidebar,
html[data-motion='none'] .sidebar {
  backdrop-filter: none;
}
</style>
