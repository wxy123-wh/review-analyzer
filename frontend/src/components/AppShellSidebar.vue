<template>
  <aside data-testid="narrow-sidebar" class="sidebar" data-motion-reveal>
    <div class="sidebar-head" data-motion-reveal style="--motion-delay: 60ms">
      <div class="brand-wrap">
        <div class="brand">WH</div>
        <div class="brand-copy">
          <strong>Workbench</strong>
          <span>Dashboard shell</span>
        </div>
      </div>
    </div>

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
            <span class="label">{{ module.label }}</span>
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
  const core = props.items.filter((module) => !module.id.startsWith('showcase-'))
  const showcase = props.items.filter((module) => module.id.startsWith('showcase-'))

  return [
    { label: '核心模块', items: core },
    { label: '演示场景', items: showcase },
  ].filter((group) => group.items.length > 0)
})
</script>

<style scoped>
.sidebar {
  position: sticky;
  top: 0;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  padding: var(--space-5) var(--space-3) var(--space-4);
  background: var(--gradient-sidebar);
  backdrop-filter: blur(16px);
  box-shadow: inset -1px 0 0 rgba(255, 255, 255, 0.03);
}

.sidebar-head {
  display: grid;
  gap: var(--space-3);
}

.brand-wrap {
  display: grid;
  justify-items: center;
  gap: var(--space-3);
}

.brand {
  width: 60px;
  height: 60px;
  border-radius: var(--radius-lg);
  display: grid;
  place-items: center;
  font-weight: 800;
  letter-spacing: 0.12em;
  background: var(--gradient-accent);
  color: var(--color-text-inverse);
  box-shadow: var(--shadow-glow);
}

.brand-copy {
  display: grid;
  gap: var(--space-1);
  text-align: center;
}

.brand-copy strong {
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
}

.brand-copy span {
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.nav {
  width: 100%;
  display: grid;
  gap: var(--space-4);
  min-height: 0;
}

.nav-group {
  display: grid;
  gap: var(--space-2);
}

.nav-group-label {
  margin: 0;
  padding: 0 var(--space-2);
  font-size: var(--font-size-2xs);
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.nav-group-items {
  display: grid;
  gap: var(--space-2);
}

.nav-item {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--color-border-subtle);
  background: rgba(8, 16, 29, 0.42);
  border-radius: var(--radius-md);
  padding: var(--space-3) var(--space-2);
  color: var(--color-text-secondary);
  cursor: pointer;
  display: grid;
  justify-items: center;
  gap: var(--space-2);
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
  background: linear-gradient(180deg, rgba(19, 34, 58, 0.92), rgba(12, 23, 41, 0.94));
  border-color: var(--color-border-default);
  color: var(--color-text-primary);
  box-shadow: var(--shadow-inset-soft);
  }
}

.nav-item.active {
   border-color: var(--color-border-strong);
   background:
     linear-gradient(90deg, rgba(122, 184, 255, 0.16), transparent 28%),
     var(--gradient-nav-active);
   color: var(--color-text-primary);
   box-shadow: var(--shadow-glow);
}

.dot {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-pill);
  display: grid;
  place-items: center;
  font-size: var(--font-size-xs);
  font-weight: 700;
  background: var(--color-accent-soft);
  color: var(--color-accent-primary);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
  transition:
    background-color var(--motion-medium) var(--easing-standard),
    color var(--motion-medium) var(--easing-standard),
    box-shadow var(--motion-medium) var(--easing-standard);
}

.label {
  font-size: var(--font-size-xs);
  line-height: var(--line-height-snug);
  text-align: center;
}

.nav-item.active .active-rail {
  background: var(--color-accent-secondary);
}

.nav-item.active .dot {
  background: var(--color-accent-strong);
  color: var(--color-text-primary);
  box-shadow: 0 0 0 1px rgba(102, 224, 194, 0.18);
}

@media (max-width: 980px) {
  .sidebar {
    position: static;
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
    min-width: 84px;
    min-height: 72px;
    padding: var(--space-3) var(--space-2);
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
