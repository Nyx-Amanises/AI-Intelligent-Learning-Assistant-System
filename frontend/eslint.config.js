import pluginVue from 'eslint-plugin-vue'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'
import skipFormatting from '@vue/eslint-config-prettier/skip-formatting'

export default defineConfigWithVueTs(
  {
    name: 'app/files-to-lint',
    files: ['**/*.{ts,mts,tsx,vue}']
  },
  {
    name: 'app/files-to-ignore',
    ignores: ['dist/**', 'node_modules/**', 'src/components.d.ts', '*.tsbuildinfo']
  },
  pluginVue.configs['flat/recommended'],
  vueTsConfigs.recommended,
  // 格式问题交给 Prettier，ESLint 只管代码质量
  skipFormatting,
  {
    name: 'app/rules',
    rules: {
      // 组件名沿用现有的单词命名（AppIcon、EmptyState 等已是多词；路由视图用 *View 后缀）
      'vue/multi-word-component-names': 'off',
      // 现有模板大量使用自闭合的自定义组件与非自闭合的原生标签，不强制统一
      'vue/html-self-closing': 'off',
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/multiline-html-element-content-newline': 'off',
      'vue/html-indent': 'off',
      'vue/attributes-order': 'off',
      'vue/require-default-prop': 'off',
      // 接口返回结构尚未全部建模，any 先降为警告，逐步收敛
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrors: 'none' }]
    }
  }
)
