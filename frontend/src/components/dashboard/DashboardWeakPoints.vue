<template>
  <section class="weak-points" aria-labelledby="weak-points-title">
    <header class="section-heading">
      <div>
        <h2 id="weak-points-title">需要巩固的知识点</h2>
        <p>根据练习评分，掌握度低于 {{ MASTERY_REVIEW_THRESHOLD }}% 的知识点</p>
      </div>
      <RouterLink to="/mastery" class="text-link">知识掌握<AppIcon name="arrow-right" :size="15" /></RouterLink>
    </header>
    <div class="weak-points__grid">
      <article
        v-for="item in points"
        :key="String(item.materialId || 0) + '-' + item.knowledgePoint"
        class="weak-points__item surface"
      >
        <div class="weak-points__top">
          <div class="weak-points__name">
            <h3 :title="item.knowledgePoint">{{ item.knowledgePoint }}</h3>
            <p :title="item.materialTitle">{{ item.materialTitle || '未关联资料' }}</p>
          </div>
          <strong :style="{ color: masteryColorByPercent(item.masteryPercent) }">{{ item.masteryPercent }}%</strong>
        </div>
        <span class="weak-points__bar" aria-hidden="true">
          <span :style="{ width: item.masteryPercent + '%', background: masteryColorByPercent(item.masteryPercent) }" />
        </span>
        <p class="weak-points__meta">错 {{ item.wrongCount }} 次 · 共作答 {{ item.attemptCount }} 次</p>
        <div class="weak-points__actions">
          <el-button
            size="small"
            type="primary"
            plain
            :disabled="!canPractice(item)"
            @click="router.push(knowledgePracticeRoute(item.materialId!, item.knowledgePoint.trim()))"
            >专项练习</el-button
          >
          <el-button size="small" @click="router.push(wrongQuestionsRoute(item.knowledgePoint, item.materialId))"
            >相关错题</el-button
          >
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { RouterLink, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import type { KnowledgeMasteryItem } from '@/api/modules/knowledgeMastery'
import { knowledgePointPracticeReason } from '@/utils/knowledgePractice'
import { MASTERY_REVIEW_THRESHOLD, masteryColorByPercent } from '@/utils/mastery'
import { knowledgePracticeRoute, wrongQuestionsRoute } from './routes'

/** 首页“需要巩固的知识点”：最多三条，可直接生成专项练习或查看相关错题。 */
defineProps<{ points: KnowledgeMasteryItem[] }>()
const router = useRouter()

const canPractice = (item: KnowledgeMasteryItem) =>
  Number(item.materialId) > 0 && !knowledgePointPracticeReason(item.knowledgePoint)
</script>
<style scoped>
.weak-points__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 14px;
}
.weak-points__item {
  padding: 18px 20px 16px;
  transition:
    border-color var(--duration-fast) ease,
    box-shadow var(--duration) ease;
}
.weak-points__item:hover {
  border-color: var(--line-strong);
  box-shadow: var(--shadow);
}
.weak-points__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.weak-points__name {
  min-width: 0;
}
.weak-points__name h3 {
  margin: 0;
  overflow: hidden;
  color: var(--text);
  font-size: 15px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.weak-points__name p {
  margin: 2px 0 0;
  overflow: hidden;
  color: var(--muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.weak-points__top strong {
  font-size: 20px;
  font-weight: 600;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}
.weak-points__bar {
  display: block;
  height: 5px;
  margin-top: 12px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--bg-secondary);
}
.weak-points__bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  transform-origin: left;
  animation: weak-grow 0.7s var(--ease-out) 0.2s both;
}
.weak-points__meta {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 12px;
}
.weak-points__actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
.weak-points__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
@keyframes weak-grow {
  from {
    transform: scaleX(0);
  }
}
</style>
