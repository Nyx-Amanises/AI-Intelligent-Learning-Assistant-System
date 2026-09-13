export const navigationGroups = [
  {
    label: '学习空间',
    items: [
      { path: '/dashboard', label: '学习概览', icon: 'home' },
      { path: '/materials', label: '我的资料', icon: 'folder' },
      { path: '/summary', label: '学习笔记', icon: 'book-open' },
      { path: '/quiz', label: '练习题集', icon: 'quiz' }
    ]
  },
  {
    label: '复习与成长',
    items: [
      { path: '/practice', label: '练习记录', icon: 'practice' },
      { path: '/wrong-questions', label: '错题复习', icon: 'wrong' },
      { path: '/mastery', label: '知识掌握', icon: 'mastery' },
      { path: '/analytics', label: '学习分析', icon: 'analytics' }
    ]
  },
  {
    label: '工具',
    items: [
      { path: '/ai-tasks', label: '任务中心', icon: 'tasks' },
      { path: '/rag-eval', label: '检索评测', icon: 'eval' }
    ]
  }
]

export const navigationItems = [
  ...navigationGroups.flatMap((group) => group.items.map((item) => ({ ...item, group: group.label }))),
  { path: '/ai-config', label: '模型与设置', icon: 'config', group: '工具' }
]
