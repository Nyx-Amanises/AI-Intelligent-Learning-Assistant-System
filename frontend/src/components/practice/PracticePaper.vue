<template>
  <div class="practice-paper-main">
    <div class="page-card practice-paper-hero">
      <div class="practice-paper-hero__head">
        <div class="practice-paper-hero__intro">
          <h2 class="practice-paper-hero__title">{{ practiceDetail.sessionName }}</h2>
          <div class="practice-paper-hero__meta">
            <span>{{ practiceDetail.totalQuestions }} 道题</span>
            <span>满分 {{ practiceDetail.totalScore || 0 }} 分</span>
            <el-button link @click="emit('rename')">修改名称</el-button>
          </div>
          <p class="practice-paper-hero__time">
            {{
              submitted
                ? '提交于 ' + formatDateTime(practiceDetail.submitTime)
                : '开始于 ' + formatDateTime(practiceDetail.startTime)
            }}
          </p>
        </div>
        <div class="practice-paper-hero__score">
          <strong>{{ submitted ? practiceDetail.obtainedScore || 0 : answeredCount }}</strong>
          <span>{{
            submitted ? (hasPendingAiReview ? '当前得分' : '本次得分') : '/ ' + practiceDetail.totalQuestions + ' 已答'
          }}</span>
        </div>
      </div>
      <div v-if="!submitted" class="practice-paper-progress">
        <div>
          <span>作答进度</span><strong>{{ completionPercentage }}%</strong>
        </div>
        <el-progress :percentage="completionPercentage" :show-text="false" :stroke-width="6" />
        <p>答案会自动保存在此浏览器，离开后可继续作答。</p>
      </div>
      <div v-else class="practice-paper-note">
        {{
          hasPendingAiReview
            ? '客观题已批改，简答题评分完成后会自动更新。'
            : '练习已完成。回顾下方解析，巩固还不熟悉的知识点。'
        }}
      </div>
    </div>

    <el-alert v-if="hasPendingAiReview" :title="pendingReviewText" type="info" :closable="false" show-icon />

    <div v-for="(section, sectionIndex) in sections" :key="section.type" class="page-card practice-paper-section">
      <div class="practice-paper-section__title">
        <span>{{ toSectionLabel(sectionIndex) }}、{{ section.label }}</span
        ><small>{{ section.items.length }} 道题</small>
      </div>
      <article
        v-for="answer in section.items"
        :id="'question-' + answer.questionId"
        :key="answer.questionId"
        class="practice-paper-question"
      >
        <div class="practice-paper-question__head">
          <div class="practice-paper-question__index">第 {{ answer.globalIndex }} 题</div>
          <span class="practice-paper-question__type">{{ formatQuestionType(answer.questionType) }}</span>
        </div>
        <h3 :id="'stem-' + answer.questionId" class="practice-paper-question__stem">{{ answer.stemText }}</h3>

        <el-checkbox-group
          v-if="isMultiQuestion(answer)"
          :model-value="splitMultiAnswer(answerForm[answer.questionId])"
          class="choice-group practice-paper-choice-group"
          :disabled="submitted"
          :aria-labelledby="'stem-' + answer.questionId"
          @update:model-value="setAnswer(answer.questionId, joinMultiAnswer($event as string[]))"
        >
          <el-checkbox
            v-for="option in buildOptions(answer)"
            :key="option.value"
            :value="option.value"
            border
            class="choice-option practice-paper-choice"
            >{{ option.label }}</el-checkbox
          >
        </el-checkbox-group>
        <el-radio-group
          v-else-if="isChoiceQuestion(answer)"
          :model-value="answerForm[answer.questionId] || ''"
          class="choice-group practice-paper-choice-group"
          :disabled="submitted"
          :aria-labelledby="'stem-' + answer.questionId"
          @update:model-value="setAnswer(answer.questionId, String($event ?? ''))"
        >
          <el-radio
            v-for="option in buildOptions(answer)"
            :key="option.value"
            :value="option.value"
            border
            class="choice-option practice-paper-choice"
            >{{ option.label }}</el-radio
          >
        </el-radio-group>
        <el-input
          v-else
          :model-value="answerForm[answer.questionId] || ''"
          :disabled="submitted"
          type="textarea"
          :rows="5"
          :aria-labelledby="'stem-' + answer.questionId"
          placeholder="用自己的话写下理解，也可以列出关键步骤…"
          @update:model-value="setAnswer(answer.questionId, $event)"
        />

        <div
          v-if="submitted"
          class="practice-paper-result"
          :class="{
            'practice-paper-result--correct': answer.isCorrect,
            'practice-paper-result--pending': isPendingReview(answer)
          }"
        >
          <div class="practice-paper-result__heading">
            <span>{{ questionResultLabel(answer) }}</span
            ><strong>{{
              isPendingReview(answer) ? '等待评分' : (answer.obtainedScore ?? answer.aiScore ?? 0) + ' 分'
            }}</strong>
          </div>
          <div class="practice-paper-review">
            <span>我的答案</span>
            <p>{{ formatAnswer(answer, answer.userAnswer) }}</p>
          </div>
          <div class="practice-paper-review practice-paper-review--reference">
            <span>{{ isShortQuestion(answer) ? '参考答案' : '正确答案' }}</span>
            <p>{{ formatAnswer(answer, answer.referenceAnswer || answer.correctAnswer) }}</p>
          </div>
          <div v-if="isShortQuestion(answer) && answer.reviewComment" class="practice-paper-analysis">
            <strong>AI 评语</strong>
            <p>{{ answer.reviewComment }}</p>
          </div>
          <div class="practice-paper-analysis">
            <strong>答案解析</strong>
            <p>{{ answer.answerAnalysis || '这道题暂时没有解析。' }}</p>
          </div>
          <details v-if="isShortQuestion(answer) && answer.sourceSegments?.length" class="practice-paper-sources">
            <summary>查看资料依据</summary>
            <SourceSegmentList :segments="answer.sourceSegments" compact />
          </details>
        </div>
      </article>
    </div>

    <div class="page-card practice-paper-submit">
      <div>
        <h3>{{ submitted ? '让复习形成闭环' : '准备好提交了吗？' }}</h3>
        <p>
          {{
            submitted
              ? '回顾错题，或再练一次，看看自己进步了多少。'
              : unansweredCount
                ? '还有 ' + unansweredCount + ' 道题未作答，提交前再检查一下。'
                : '所有题目已作答，提交后即可查看成绩与解析。'
          }}
        </p>
      </div>
      <div class="practice-paper-submit__actions">
        <el-button @click="emit('back')">返回题集列表</el-button>
        <el-button v-if="!submitted" type="primary" :loading="submitting" @click="emit('submit')">提交练习</el-button>
        <template v-else>
          <el-button @click="router.push('/wrong-questions')">查看错题本</el-button>
          <el-button type="primary" :loading="restarting" @click="emit('restart')">再练一次</el-button>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import SourceSegmentList from '@/components/SourceSegmentList.vue'
import { formatDateTime } from '@/utils/format'
import { formatQuestionType } from '@/utils/labels'
import {
  buildOptions,
  formatAnswer,
  isChoiceQuestion,
  isMultiQuestion,
  isPendingReview,
  isShortQuestion,
  joinMultiAnswer,
  questionResultLabel,
  splitMultiAnswer,
  toSectionLabel,
  type AnswerForm,
  type PracticeDetail,
  type PracticeSection
} from './practiceHelpers'

/**
 * 试卷主体：概览、逐题作答（单选/判断用单选框，多选用复选框，简答用文本域）、提交后的判分与解析。
 * 答案通过 v-model:answer-form 回传，组件内部不直接改 props。
 */
withDefaults(
  defineProps<{
    practiceDetail: PracticeDetail
    sections: PracticeSection[]
    submitted: boolean
    hasPendingAiReview?: boolean
    pendingReviewText?: string
    answeredCount: number
    unansweredCount: number
    completionPercentage: number
    submitting?: boolean
    restarting?: boolean
  }>(),
  {
    hasPendingAiReview: false,
    pendingReviewText: '',
    submitting: false,
    restarting: false
  }
)

const answerForm = defineModel<AnswerForm>('answerForm', { required: true })

const emit = defineEmits<{
  submit: []
  restart: []
  rename: []
  back: []
}>()

const router = useRouter()

const setAnswer = (questionId: number, value: string) => {
  answerForm.value = { ...answerForm.value, [questionId]: value }
}
</script>

<style scoped>
.practice-paper-main {
  min-width: 0;
}
.practice-paper-hero {
  padding: 0;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: none;
}
.practice-paper-hero__head {
  padding: 26px;
  gap: 20px;
}
.practice-paper-hero__intro {
  min-width: 0;
}
.practice-paper-hero__title {
  font-size: 22px;
  line-height: 1.5;
  font-weight: 650;
  color: var(--text);
  overflow-wrap: anywhere;
}
.practice-paper-hero__meta {
  align-items: center;
  gap: 16px;
  margin-top: 10px;
  color: var(--muted);
  font-size: 13px;
}
.practice-paper-hero__meta :deep(.el-button) {
  height: auto;
  padding: 0;
  font-size: 12px;
}
.practice-paper-hero__time {
  margin: 12px 0 0;
  color: var(--muted);
  font-size: 12px;
}
.practice-paper-hero__score {
  color: var(--brand);
  min-width: 82px;
  padding-top: 4px;
}
.practice-paper-hero__score strong {
  font-size: 38px;
  font-weight: 550;
  font-variant-numeric: tabular-nums;
}
.practice-paper-hero__score span {
  margin-top: 10px;
  color: var(--muted);
  font-size: 12px;
  white-space: nowrap;
}
.practice-paper-progress {
  padding: 0 26px 22px;
}
.practice-paper-progress > div:first-child {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  color: var(--muted);
  font-size: 12px;
}
.practice-paper-progress strong {
  color: var(--brand);
  font-weight: 600;
}
.practice-paper-progress p {
  margin: 10px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}
.practice-paper-note {
  padding: 15px 26px;
  background: var(--brand-light);
  color: var(--brand);
  font-size: 13px;
  line-height: 1.8;
}
.practice-paper-section {
  padding: 26px;
  border-radius: 12px;
  box-shadow: none;
}
.practice-paper-section__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--line);
  color: var(--text);
  font-size: 16px;
}
.practice-paper-section__title small {
  color: var(--muted);
  font-size: 12px;
  font-weight: 400;
  white-space: nowrap;
}
.practice-paper-question {
  scroll-margin-top: 30px;
}
.practice-paper-question + .practice-paper-question {
  margin-top: 30px;
  padding-top: 28px;
  border-top: 1px solid var(--line);
}
.practice-paper-question__head {
  align-items: center;
}
.practice-paper-question__index {
  color: var(--brand);
  font-size: 12px;
  font-weight: 600;
}
.practice-paper-question__type {
  color: var(--muted);
  font-size: 12px;
}
.practice-paper-question__stem {
  margin: 12px 0 16px;
  color: var(--text);
  font-size: 16px;
  line-height: 1.85;
  font-weight: 550;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.practice-paper-choice-group {
  display: grid;
  gap: 10px;
  margin-top: 0;
}
.practice-paper-choice-group :deep(.el-radio.is-bordered),
.practice-paper-choice-group :deep(.el-checkbox.is-bordered) {
  width: 100%;
  height: auto;
  min-height: 48px;
  margin: 0;
  padding: 13px 15px;
  border-color: var(--line);
  border-radius: 8px;
  background: var(--panel);
  white-space: normal;
  align-items: flex-start;
}
.practice-paper-choice-group :deep(.el-radio__input),
.practice-paper-choice-group :deep(.el-checkbox__input) {
  margin-top: 4px;
}
.practice-paper-choice-group :deep(.el-radio__label),
.practice-paper-choice-group :deep(.el-checkbox__label) {
  line-height: 1.75;
  white-space: normal;
  overflow-wrap: anywhere;
  color: var(--text);
  font-size: 14px;
  padding-left: 11px;
}
.practice-paper-choice-group :deep(.el-radio.is-checked),
.practice-paper-choice-group :deep(.el-checkbox.is-checked) {
  border-color: var(--brand);
  background: var(--brand-light);
}
.practice-paper-choice-group :deep(.el-radio.is-disabled .el-radio__label),
.practice-paper-choice-group :deep(.el-checkbox.is-disabled .el-checkbox__label) {
  color: var(--text);
}
.practice-paper-result {
  margin-top: 20px;
  padding: 18px;
  border: 1px solid var(--line);
  border-left: 3px solid var(--el-color-warning);
  border-radius: 8px;
  background: var(--bg);
  font-size: 13px;
}
.practice-paper-result--correct {
  border-left-color: var(--brand);
}
.practice-paper-result--pending {
  border-left-color: var(--muted);
}
.practice-paper-result__heading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  color: var(--text);
  font-weight: 600;
}
.practice-paper-result--correct .practice-paper-result__heading {
  color: var(--brand);
}
.practice-paper-review {
  display: grid;
  grid-template-columns: 65px minmax(0, 1fr);
  gap: 14px;
  margin-top: 12px;
  line-height: 1.8;
}
.practice-paper-review > span {
  color: var(--muted);
}
.practice-paper-review p {
  margin: 0;
  color: var(--text);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.practice-paper-review--reference p {
  color: var(--brand);
}
.practice-paper-analysis {
  margin-top: 16px;
  padding-top: 15px;
  border-top: 1px solid var(--line);
  color: var(--text);
  line-height: 1.85;
}
.practice-paper-analysis strong {
  font-size: 12px;
}
.practice-paper-analysis p {
  margin: 7px 0 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.practice-paper-sources {
  margin-top: 16px;
}
.practice-paper-sources summary {
  color: var(--brand);
  cursor: pointer;
}
.practice-paper-sources > :deep(.source-segment-list) {
  margin-top: 12px;
}
.practice-paper-submit {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 20px;
  padding: 22px 26px;
  border-radius: 12px;
  box-shadow: none;
}
.practice-paper-submit h3 {
  margin: 0;
  color: var(--text);
  font-size: 15px;
}
.practice-paper-submit p {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
}
.practice-paper-submit__actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.practice-paper-submit__actions :deep(.el-button + .el-button) {
  margin: 0;
}
@media (max-width: 600px) {
  .practice-paper-section {
    padding: 19px 16px;
  }
  .practice-paper-hero__head {
    padding: 20px 16px;
    gap: 12px;
  }
  .practice-paper-hero__title {
    font-size: 18px;
  }
  .practice-paper-hero__meta {
    gap: 10px;
  }
  .practice-paper-hero__score {
    min-width: 60px;
  }
  .practice-paper-hero__score strong {
    font-size: 32px;
  }
  .practice-paper-progress {
    padding: 0 16px 18px;
  }
  .practice-paper-note {
    padding: 14px 16px;
  }
  .practice-paper-question__stem {
    font-size: 15px;
  }
  .practice-paper-result {
    padding: 14px;
  }
  .practice-paper-review {
    grid-template-columns: 1fr;
    gap: 4px;
  }
  .practice-paper-submit {
    padding: 20px 16px;
  }
}
</style>
