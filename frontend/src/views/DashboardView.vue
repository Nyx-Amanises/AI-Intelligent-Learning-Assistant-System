<template>
  <section>
    <div class="page-header">
      <div>
        <h1 class="page-title">学习概览</h1>
        <p class="page-desc">
          这一版前端先聚焦项目展示面，方便你后续联调后端接口，并快速形成一个能讲给面试官听的完整产品。
        </p>
      </div>
    </div>

    <div class="stats-grid">
      <div class="stat-card" v-for="item in stats" :key="item.label">
        <div class="stat-label">{{ item.label }}</div>
        <div class="stat-value">{{ item.value }}</div>
      </div>
    </div>

    <div class="page-card">
      <h3>当前演示路径</h3>
      <p class="page-desc">
        先登录系统，在资料管理中录入文本资料或上传文件，再到 AI 总结页面选择资料生成总结结果。等后端题库和练习接口完成后，这里再扩成真正的数据面板。
      </p>
    </div>
  </section>
</template>

<script setup lang="ts">
const stats = [
  { label: '资料总数', value: '03' },
  { label: 'AI 总结', value: '01' },
  { label: '练习计划', value: '00' },
  { label: '待复习', value: '00' }
]
</script>
