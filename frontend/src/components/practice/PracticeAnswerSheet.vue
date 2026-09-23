<template>
  <aside class="page-card practice-paper-sidebar" aria-label="题目导航">
    <div class="practice-paper-sidebar__title">答题卡 <span>{{ answeredCount }} / {{ totalQuestions }}</span></div>
    <p class="practice-paper-sidebar__hint">点击题号，快速定位题目。</p>
    <div v-for="section in sections" :key="section.type" class="practice-paper-sidebar__group">
      <div class="practice-paper-sidebar__group-title">{{ section.sidebarTitle }}</div>
      <div class="practice-paper-sidebar__grid">
        <button
          v-for="answer in section.items"
          :key="answer.questionId"
          type="button"
          class="practice-paper-sidebar__item"
          :class="{
            'practice-paper-sidebar__item--active': activeQuestionId === answer.questionId,
            'practice-paper-sidebar__item--done': hasAnswerValue(answerForm[answer.questionId]),
            'practice-paper-sidebar__item--correct': submitted && answer.isCorrect,
            'practice-paper-sidebar__item--wrong': submitted && !answer.isCorrect && !isPendingReview(answer)
          }"
          :aria-label="'第 ' + answer.globalIndex + ' 题，' + questionNavStatus(answer, answerForm, submitted)"
          :aria-current="activeQuestionId === answer.questionId ? 'step' : undefined"
          @click="emit('select', answer.questionId)"
        >{{ answer.globalIndex }}</button>
      </div>
    </div>
    <div class="practice-paper-sidebar__legend">
      <span><i></i>{{ submitted ? '待评分' : '未作答' }}</span>
      <span><i class="is-done"></i>{{ submitted ? '答对' : '已作答' }}</span>
      <span v-if="submitted"><i class="is-wrong"></i>待复习</span>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { hasAnswerValue, isPendingReview, questionNavStatus, type AnswerForm, type PracticeSection } from './practiceHelpers'

/** 答题卡：按题型分组的题号导航 + 图例。 */
withDefaults(defineProps<{
  sections: PracticeSection[]
  answerForm: AnswerForm
  submitted: boolean
  activeQuestionId?: number | null
  answeredCount: number
  totalQuestions: number
}>(), {
  activeQuestionId: null
})

const emit = defineEmits<{ select: [questionId: number] }>()
</script>

<style scoped>
.practice-paper-sidebar { top: 22px; padding: 20px; border-radius: 12px; box-shadow: none; max-height: calc(100vh - 60px); }
.practice-paper-sidebar__title { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 8px; color: var(--text); font-size: 15px; }
.practice-paper-sidebar__title span { font-size: 12px; font-weight: 400; color: var(--muted); }
.practice-paper-sidebar__hint { margin: 0 0 22px; color: var(--muted); font-size: 12px; line-height: 1.7; }
.practice-paper-sidebar__group-title { font-size: 12px; font-weight: 500; color: var(--muted); }
.practice-paper-sidebar__grid { grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; }
.practice-paper-sidebar__item { height: 40px; border: 1px solid var(--line); border-radius: 6px; background: var(--panel); color: var(--muted); font: inherit; font-size: 13px; }
.practice-paper-sidebar__item--done, .practice-paper-sidebar__item--correct { border-color: var(--brand); background: var(--brand-light); color: var(--brand); }
.practice-paper-sidebar__item--wrong { border-color: var(--el-color-warning-light-5); background: var(--el-color-warning-light-9); color: var(--el-color-warning-dark-2); }
.practice-paper-sidebar__item--active { box-shadow: 0 0 0 2px var(--brand); }
.practice-paper-sidebar__item:focus-visible { outline: 2px solid var(--brand); outline-offset: 3px; }
.practice-paper-sidebar__legend { display: flex; flex-wrap: wrap; gap: 14px; margin-top: 24px; padding-top: 18px; border-top: 1px solid var(--line); color: var(--muted); font-size: 11px; }
.practice-paper-sidebar__legend > span { display: flex; align-items: center; gap: 5px; }
.practice-paper-sidebar__legend i { width: 9px; height: 9px; border: 1px solid var(--line); border-radius: 2px; }
.practice-paper-sidebar__legend .is-done { border-color: var(--brand); background: var(--brand-light); }
.practice-paper-sidebar__legend .is-wrong { border-color: var(--el-color-warning-light-5); background: var(--el-color-warning-light-9); }
@media (max-width: 1150px) {
  .practice-paper-sidebar { position: static; max-height: none; }
  .practice-paper-sidebar__grid { display: flex; flex-wrap: wrap; }
  .practice-paper-sidebar__item { width: 42px; }
  .practice-paper-sidebar__group { display: inline-block; margin-right: 22px; vertical-align: top; }
  .practice-paper-sidebar__group + .practice-paper-sidebar__group { margin-top: 0; }
  .practice-paper-sidebar__legend { margin-top: 18px; }
}
@media (prefers-reduced-motion: reduce) {
  .practice-paper-sidebar__item { transition: none; }
}
</style>
