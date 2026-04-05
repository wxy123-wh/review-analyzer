<template>
  <div class="shell">
    <div class="shell-backdrop" aria-hidden="true"></div>

    <div class="rail">
      <slot name="sidebar" />
    </div>

    <main class="content" data-motion-shell="enter">
      <div class="content-shell">
        <slot name="header" />
        <slot />
      </div>
    </main>
  </div>
</template>

<style scoped>
.shell {
   position: relative;
   isolation: isolate;
   min-height: 100vh;
   display: grid;
   grid-template-columns: 120px minmax(0, 1fr);
   background: var(--color-shell-gradient);
   color: var(--color-text-primary);
   font-family: var(--font-family-sans);
   overflow: hidden;
 }

 .shell-backdrop {
   position: absolute;
   inset: 0;
   z-index: var(--z-base);
   pointer-events: none;
   background:
     radial-gradient(circle at 18% 14%, rgba(122, 184, 255, 0.14) 0%, transparent 24%),
     radial-gradient(circle at 82% 8%, rgba(102, 224, 194, 0.08) 0%, transparent 20%),
     linear-gradient(180deg, rgba(255, 255, 255, 0.02), transparent 18%);
 }

 .rail {
   position: relative;
   z-index: var(--z-raised);
   min-width: 0;
   padding: var(--space-4) 0;
   border-right: 1px solid var(--color-border-subtle);
   background: linear-gradient(180deg, rgba(7, 16, 30, 0.72), rgba(7, 16, 30, 0.26));
   box-shadow: inset -1px 0 0 rgba(255, 255, 255, 0.03);
 }

 .content {
   position: relative;
   z-index: var(--z-base);
   min-width: 0;
   padding: var(--space-4);
 }

 .content-shell {
   min-height: 100%;
   display: grid;
   align-content: start;
   gap: var(--space-5);
   padding: var(--space-2) var(--space-2) var(--space-5);
 }

 @media (max-width: 980px) {
   .shell {
     grid-template-columns: 1fr;
     grid-template-rows: auto 1fr;
   }

   .rail {
     padding: 0;
     border-right: 0;
     border-bottom: 1px solid var(--color-border-subtle);
   }

   .content {
     padding: var(--space-4);
   }

   .content-shell {
     gap: var(--space-4);
     padding: 0;
   }
  }
</style>
