<template>
  <header class="hero" data-motion-reveal style="--motion-delay: 80ms">
    <div class="hero-shell" data-motion-spotlight="soft">
      <div class="hero-topline">
        <span class="hero-kicker">控制台视图</span>
        <span class="hero-state">已接入演示模块导航</span>
      </div>

      <div class="hero-main">
        <div class="hero-copy">
          <h1>蓝牙耳机评论改进决策系统</h1>
          <p>V1.5 演示扩展：趋势图、词云、登录互动、流水线编排、可解释性分析与报告中心</p>
        </div>

        <span class="user-chip" data-motion-hover="lift">当前用户：{{ currentUser }}</span>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
defineProps<{
  currentUser: string
}>()
</script>

<style scoped>
.hero {
  position: relative;
}

.hero-shell {
  position: relative;
  overflow: hidden;
  display: grid;
  gap: var(--space-4);
  padding: var(--space-5);
  border-radius: var(--radius-xl);
  border: 1px solid var(--color-border-default);
  background:
    linear-gradient(135deg, rgba(122, 184, 255, 0.06), transparent 34%),
    linear-gradient(180deg, rgba(13, 25, 45, 0.94), rgba(8, 17, 31, 0.98));
  box-shadow: var(--shadow-panel);
}

.hero-shell::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(90deg, rgba(255, 255, 255, 0.05), transparent 22% 78%, rgba(255, 255, 255, 0.03));
  opacity: 0.75;
}

.hero-topline,
.hero-main {
  position: relative;
  z-index: var(--z-raised);
}

.hero-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--color-border-subtle);
}

.hero-kicker,
.hero-state {
  width: fit-content;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-pill);
  border: 1px solid var(--color-border-default);
  background: var(--color-surface-overlay);
  box-shadow: var(--shadow-inset-soft);
  font-size: var(--font-size-xs);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-kicker {
  color: var(--color-accent-primary);
}

.hero-state {
  color: var(--color-text-secondary);
}

.hero-main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.hero-copy {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.hero h1 {
  margin: 0;
  font-size: var(--font-size-3xl);
  line-height: var(--line-height-tight);
  letter-spacing: -0.02em;
  color: var(--color-text-primary);
}

.hero p {
  margin: 0;
  max-width: 72ch;
  color: var(--color-text-secondary);
  font-size: var(--font-size-md);
}

.user-chip {
  width: fit-content;
  flex-shrink: 0;
  border-radius: var(--radius-pill);
  border: 1px solid var(--color-border-default);
  background: var(--color-surface-overlay);
  color: var(--color-accent-primary);
  font-size: var(--font-size-xs);
  padding: var(--space-2) var(--space-3);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

@media (max-width: 980px) {
  .hero-shell {
    padding: var(--space-4);
  }

  .hero-topline,
  .hero-main {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero h1 {
    font-size: var(--font-size-2xl);
  }
}
</style>
