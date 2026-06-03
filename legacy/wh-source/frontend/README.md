# Vue 3 + Vite

本项目基于 Vue 3 + Vite，用于快速开始前端开发。

- `<script setup>` 用法参考：https://v3.vuejs.org/api/sfc-script-setup.html#sfc-script-setup
- 工程化与编辑器支持参考：https://vuejs.org/guide/scaling-up/tooling.html#ide-support

## 此版本更新 (2026-01-14)

### 界面重构 - 侧边导航栏 (Sidebar)

**变更内容：**
1.  **布局重塑**：废弃了顶部的水平导航栏，改为左侧垂直导航栏 (`AppSidebar`)，以适应更多功能菜单的扩展。
2.  **组件新增**：创建了 `src/components/AppSidebar.vue` 组件，集成了品牌Logo、导航菜单、角色切换器和退出登录功能。
3.  **App.vue 更新**：重写了 `App.vue` 的布局结构，引入 `el-container` (Flex布局)，将 `GlobalFiltersBar` (全局过滤器) 移至右侧内容区域的顶部 Header。
4.  **样式调整**：侧边栏采用了新拟物 (Neu) 风格设计，保持了原有的 UI 美学。

**导航栏功能：**
- **Brand**：点击顶部Logo可返回当前角色的主页。
- **Menu**：根据当前登录角色 (PM/MARKET/OPS) 动态显示对应的功能入口。
- **User Footer**：底部包含角色身份卡片（支持点击切换角色）和通过图标退出的功能。

**响应式设计：**
- **桌面端**：显示完整的侧边栏 (宽 260px)。
- **平板端**：侧边栏自动收缩为图标模式 (宽 80px)。
- **移动端**：侧边栏隐藏，通过底部的 `MobileNav` 进行导航。
