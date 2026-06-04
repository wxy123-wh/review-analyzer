<template>
  <div class="shell">
    <div class="rail">
      <slot name="sidebar" />
    </div>

    <main class="content" data-motion-shell="enter">
      <div class="content-shell">
        <slot />
      </div>
    </main>
  </div>
</template>

<style scoped>
.shell {
  position: relative;
  isolation: isolate;
  height: 100vh;
  min-height: 680px;
  display: grid;
  grid-template-columns: 104px minmax(0, 1fr);
  background: var(--color-canvas);
  color: var(--color-text-primary);
  font-family: var(--font-family-sans);
  overflow: hidden;
}

 .rail {
  position: relative;
  z-index: var(--z-raised);
  min-width: 0;
  padding: var(--space-3) 0;
  border-right: 1px solid var(--color-border-default);
  background: var(--color-surface-1);
}

 .content {
  position: relative;
  z-index: var(--z-base);
  min-width: 0;
  min-height: 0;
  padding: var(--space-3);
  overflow: hidden;
}

 .content-shell {
  height: 100%;
  min-height: 0;
  display: grid;
  grid-template-rows: minmax(0, 1fr);
  padding: 0;
}

 @media (max-width: 980px) {
   .shell {
     grid-template-columns: 1fr;
     grid-template-rows: auto 1fr;
     min-height: 100vh;
     height: auto;
     overflow: visible;
   }

   .rail {
     padding: 0;
     border-right: 0;
     border-bottom: 1px solid var(--color-border-subtle);
   }

   .content {
     padding: var(--space-4);
     overflow: visible;
   }

   .content-shell {
     gap: var(--space-4);
     height: auto;
     padding: 0;
   }
  }
</style>
