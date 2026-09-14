export interface NavigationItem {
  path: string
  label: string
  icon: string
}

export interface PrimaryNavigationItem extends NavigationItem {
  activePaths: string[]
}

export const primaryNavigationItems: PrimaryNavigationItem[] = [
  { path: '/dashboard', label: '首页', icon: 'home', activePaths: ['/dashboard'] },
  { path: '/materials', label: '内容', icon: 'book-open', activePaths: ['/materials', '/summary'] },
  { path: '/quiz', label: '练习', icon: 'practice', activePaths: ['/quiz', '/practice', '/wrong-questions'] },
  { path: '/analytics', label: '统计', icon: 'analytics', activePaths: ['/analytics', '/mastery'] }
]

export const mobileNavigationItems: PrimaryNavigationItem[] = [
  primaryNavigationItems[0],
  {
    ...primaryNavigationItems[1],
    activePaths: [...primaryNavigationItems[1].activePaths, ...primaryNavigationItems[2].activePaths]
  },
  primaryNavigationItems[3],
  { path: '/profile', label: '我的', icon: 'user', activePaths: ['/profile', '/ai-config'] }
]

export const navigationGroups: { label: string; items: NavigationItem[] }[] = [
  {
    label: '我的学习',
    items: [
      { path: '/dashboard', label: '首页', icon: 'home' },
      { path: '/materials', label: '我的资料', icon: 'folder' },
      { path: '/summary', label: '学习笔记', icon: 'book-open' },
      { path: '/quiz', label: '练习题集', icon: 'quiz' }
    ]
  },
  {
    label: '回顾与成长',
    items: [
      { path: '/practice', label: '练习记录', icon: 'practice' },
      { path: '/wrong-questions', label: '错题复习', icon: 'wrong' },
      { path: '/mastery', label: '知识掌握', icon: 'mastery' },
      { path: '/analytics', label: '学习统计', icon: 'analytics' }
    ]
  },
  {
    label: '工具与设置',
    items: [
      { path: '/ai-tasks', label: '任务中心', icon: 'tasks' },
      { path: '/rag-eval', label: '检索评测', icon: 'eval' },
      { path: '/ai-config', label: '模型与设置', icon: 'config' },
      { path: '/profile', label: '个人信息', icon: 'user' }
    ]
  }
]

export const navigationItems = navigationGroups.flatMap((group) =>
  group.items.map((item) => ({ ...item, group: group.label }))
)
