<template>
  <header class="hero" data-motion-reveal style="--motion-delay: 80ms">
    <div class="hero-shell">
      <div class="hero-main">
        <div class="hero-copy">
          <h1>评论 VOC 分析工作台</h1>
          <p>评论接入、问题聚合、卖点提炼、趋势和词云分析</p>
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
  display: flex;
  align-items: center;
  min-height: 72px;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border-default);
  background: var(--color-surface-1);
  box-shadow: var(--shadow-panel);
}

.hero-main {
  position: relative;
  z-index: var(--z-raised);
}

.hero-main {
  display: flex;
  width: 100%;
  align-items: center;
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
  font-size: var(--font-size-2xl);
  line-height: var(--line-height-tight);
  letter-spacing: 0;
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
  background: var(--color-canvas-alt);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  padding: var(--space-2) var(--space-3);
}

@media (max-width: 980px) {
  .hero-shell {
    padding: var(--space-4);
  }

  .hero-main {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero h1 {
    font-size: var(--font-size-2xl);
  }
}
</style>
