/**
 * 全站导航只在这里定义一次：顶部主导航、页面上方的分区标签、手机底部导航、
 * 账户菜单、浏览器标题和搜索面板里的页面跳转都从这份数据生成。
 */
export interface NavigationItem {
  path: string
  label: string
  icon: string
  /** 搜索面板里用来匹配的别名，空格分隔。 */
  keywords?: string
  /** 仅管理员可见（面向开发者的功能）。 */
  adminOnly?: boolean
}

export interface NavigationSection {
  key: string
  label: string
  icon: string
  /** 点击主导航时进入的页面。 */
  path: string
  items: NavigationItem[]
  /** 只在手机底部导航和账户菜单中出现，不放进桌面主导航。 */
  accountOnly?: boolean
}

export const homeItem: NavigationItem = { path: '/dashboard', label: '首页', icon: 'home', keywords: '主页 概览' }

export const navigationSections: NavigationSection[] = [
  {
    key: 'library',
    label: '资料',
    icon: 'book-open',
    path: '/materials',
    items: [
      { path: '/materials', label: '资料库', icon: 'folder', keywords: '资料 文档 上传 pdf word 文本' },
      { path: '/summary', label: '学习总结', icon: 'summary', keywords: '总结 摘要 笔记 提纲 考试重点' }
    ]
  },
  {
    key: 'practice',
    label: '练习',
    icon: 'practice',
    path: '/quiz',
    items: [
      { path: '/quiz', label: '题集', icon: 'quiz', keywords: '题集 出题 生成 练习题' },
      { path: '/practice', label: '练习记录', icon: 'practice', keywords: '练习 作答 成绩 交卷' },
      { path: '/wrong-questions', label: '错题本', icon: 'wrong', keywords: '错题 复习 重做' }
    ]
  },
  {
    key: 'insight',
    label: '统计',
    icon: 'analytics',
    path: '/analytics',
    items: [
      { path: '/analytics', label: '学习统计', icon: 'analytics', keywords: '统计 趋势 正确率 时长' },
      { path: '/mastery', label: '知识掌握', icon: 'mastery', keywords: '知识点 掌握度 薄弱' }
    ]
  },
  {
    key: 'account',
    label: '我的',
    icon: 'user',
    path: '/profile',
    accountOnly: true,
    items: [
      { path: '/profile', label: '个人信息', icon: 'user', keywords: '账号 头像 昵称 密码' },
      { path: '/ai-config', label: 'AI 配置', icon: 'config', keywords: '模型 api key 向量 设置' },
      { path: '/ai-tasks', label: '任务中心', icon: 'tasks', keywords: '任务 进度 重试' },
      { path: '/rag-eval', label: '检索评测', icon: 'eval', keywords: 'rag 评测 召回', adminOnly: true }
    ]
  }
]

export const primarySections = navigationSections.filter((section) => !section.accountOnly)

export const allNavigationItems: NavigationItem[] = [
  homeItem,
  ...navigationSections.flatMap((section) => section.items)
]

export const findSection = (path: string) =>
  navigationSections.find((section) => section.items.some((item) => item.path === path))

export const findNavigationItem = (path: string) => allNavigationItems.find((item) => item.path === path)

export interface NavigationLink {
  key: string
  label: string
  icon: string
  path: string
}

const toLink = ({ key, label, icon, path }: NavigationSection): NavigationLink => ({ key, label, icon, path })

/** 桌面顶部主导航。 */
export const primaryLinks: NavigationLink[] = [
  { key: 'home', label: homeItem.label, icon: homeItem.icon, path: homeItem.path },
  ...primarySections.map(toLink)
]

/** 手机底部导航：主导航再加上“我的”。 */
export const mobileLinks: NavigationLink[] = [
  ...primaryLinks,
  ...navigationSections.filter((section) => section.accountOnly).map(toLink)
]

/** 当前路径对应的主导航项。 */
export const activeNavigationKey = (path: string) => (path === homeItem.path ? 'home' : findSection(path)?.key || '')
