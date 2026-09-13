# AI 智能学习助手：UI 参考调研

调研日期：2026-09-12。本轮交付为风格参考，未实施前端改版。截图来自公开官网的产品演示，并非登录后实际使用截图；对比图仅做局部裁切和缩放。

## 真实参考来源

| 产品 | 已访问页面 | 可借鉴的内容 | 对应本项目 |
| --- | --- | --- | --- |
| Notion | https://www.notion.com/product | 轻量侧栏、知识分组、留白、细边框、内容层次 | 全局导航、资料库、摘要阅读 |
| Gemini Notebook（原 NotebookLM） | https://notebook.google/ | 上传来源、学习指南与简报入口、回答与原文引用关联 | 资料详情、AI 总结、AI 学习助手 |
| Linear | https://linear.app/ | 紧凑列表、精细排版、状态标签、任务与对话的上下文关系 | 任务中心、系统设置、状态反馈 |
| Brilliant | https://brilliant.org/ | 互动练习、分步引导、掌握情况与学习进度反馈 | 做题页、错题复盘、学习概览 |

原地址 https://notebooklm.google/ 当前重定向到 https://notebook.google/。官网常见问题明确说明 NotebookLM 自 2026 年 7 月起更名为 Gemini Notebook。

## 针对当前项目的判断

项目已经具备资料上传、总结、出题、练习、错题、掌握度与学习分析的功能基础。前端为 Vue 3、TypeScript、Vite、Pinia、Element Plus。现阶段最值得改的是页面层级、学习流程与交互完整性。

只读源码检查发现：

- `frontend/src/views/layout/AppLayout.vue:156`：11 个同级导航入口混合了学习功能和 RAG 评测、模型配置；AI 配置与底部设置入口重复。
- `frontend/src/views/MaterialView.vue:6`：开发说明“资料页就按后台列表页来做”进入了面向用户的页面。
- `frontend/src/views/QuizView.vue:7`：页面介绍任务中心的内部实现，未突出用户目标。
- `frontend/src/views/layout/AppLayout.vue:60`：搜索、通知按钮缺少点击处理，通知数字固定为 3。
- `frontend/src/views/DashboardView.vue:285`、`:485`：资料/题集总数由最近 5 条记录的长度产生，应使用真实总量。
- `frontend/src/styles.css:2443`：统计脚注有统一上涨箭头，但未对应增长数据。
- 资料行操作较多，状态含英文枚举；新用户空状态缺少清晰的下一步。

README 中 `docs/images/home.png` 是旧布局，不能当作当前页面截图。本轮未运行项目，响应式问题仅为源码风险，未作为已验证缺陷。

## 推荐设计方向（待用户选择）

主方向：浅色、安静、精致的学习工作台。借鉴 Notion 的信息组织、Gemini Notebook 的资料学习流程，练习页面借鉴 Brilliant 的清晰反馈。Linear 作为间距、状态与组件细节的参考。

建议项目自身的配色为暖白背景、森林绿主色、深灰正文和少量琥珀强调；这是针对项目的配色提案，不是对任一参考网站配色的照搬。

首页聚焦“今天学什么、从哪里继续”，优先展示继续学习、待复习错题和最近资料；学习分析强调真实进度。导航可按学习、复盘、工具分组，把模型配置和 RAG 评测保留在工具区域。AI 相关结果应能回到对应资料。

第一批适合改造的范围为全局导航、首页、资料库。确定这三部分后，再扩展到 AI 总结/助手、练习、错题和分析页面。
