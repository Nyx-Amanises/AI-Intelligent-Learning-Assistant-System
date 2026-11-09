<template>
  <div class="practice-paper-main">
    <div class="page-card practice-paper-hero">
      <div class="practice-paper-mobile-overview">
        <h2>{{ practiceDetail.sessionName }}</h2>
        <div class="practice-paper-mobile-overview__status">
          <span v-if="submitted">
            {{ hasPendingAiReview ? '当前得分' : '本次得分' }}
            <strong>{{ practiceDetail.obtainedScore || 0 }}</strong> 分
          </span>
          <span v-else
            >已答 <strong>{{ answeredCount }}</strong> / {{ practiceDetail.totalQuestions }} 道</span
          >
          <GenerationModeBadge :mode="practiceDetail.generationMode" />
        </div>
        <el-progress v-if="!submitted" :percentage="completionPercentage" :show-text="false" :stroke-width="4" />
        <details class="practice-paper-mobile-info">
          <summary>
            <span>{{
              submitted ? (hasPendingAiReview ? '简答题评分中' : '查看下方答案解析') : '答案自动保存在本机'
            }}</span>
            <span class="practice-paper-mobile-info__toggle">详情</span>
          </summary>
          <div class="practice-paper-mobile-info__body">
            <p>
              {{ practiceDetail.totalQuestions }} 道题 · 满分 {{ practiceDetail.totalScore || 0 }} 分
              <span v-if="practiceDetail.questionIds?.length"> · 专项复习</span>
            </p>
            <p>
              {{
                submitted
                  ? '提交于 ' + formatDateTime(practiceDetail.submitTime)
                  : '开始于 ' + formatDateTime(practiceDetail.startTime)
              }}
            </p>
            <p v-if="practiceDetail.generationMode === 'MOCK'">本次为模拟题目，仅用于体验作答与复盘。</p>
            <p v-if="practiceDetail.questionIds?.length">专项复习单独记录成绩，原练习记录仍会保留。</p>
            <el-button link @click="emit('rename')">修改名称</el-button>
          </div>
        </details>
      </div>
      <div class="practice-paper-hero__head">
        <div class="practice-paper-hero__intro">
          <h2 class="practice-paper-hero__title">{{ practiceDetail.sessionName }}</h2>
          <div class="practice-paper-hero__meta">
            <GenerationModeBadge :mode="practiceDetail.generationMode" />
            <span>{{ practiceDetail.totalQuestions }} 道题</span>
            <span v-if="practiceDetail.questionIds?.length">专项复习</span>
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
      <p v-if="practiceDetail.generationMode === 'MOCK'" class="practice-paper-note">
        本次练习使用模拟题目，仅用于体验作答与复盘流程。
      </p>
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
    <el-alert
      v-if="practiceDetail.questionIds?.length"
      class="practice-paper-special-review-note"
      title="本次专项复习单独记录成绩，原练习记录仍会保留。"
      type="info"
      :closable="false"
      show-icon
    />

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
          <button
            type="button"
            class="practice-paper-question__note"
            :aria-label="`记录第 ${answer.globalIndex} 题笔记`"
            title="记笔记"
            @mousedown.prevent
            @click="recordQuestionNote(answer)"
          >
            <MobileIcon name="pencil" :size="19" />
          </button>
        </div>
        <h3 :id="'stem-' + answer.questionId" class="practice-paper-question__stem">{{ answer.stemText }}</h3>
        <p v-if="isMultiQuestion(answer)" class="practice-paper-question__hint">
          请选择所有正确选项。全部选对得分，少选、多选或错选不得分。
        </p>

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
            :class="{
              'practice-paper-choice--correct':
                submitted && answerIncludesOption(answer, answer.correctAnswer, option.value),
              'practice-paper-choice--incorrect':
                submitted &&
                answerIncludesOption(answer, answer.userAnswer, option.value) &&
                !answerIncludesOption(answer, answer.correctAnswer, option.value)
            }"
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
            :class="{
              'practice-paper-choice--correct':
                submitted && answerIncludesOption(answer, answer.correctAnswer, option.value),
              'practice-paper-choice--incorrect':
                submitted &&
                answerIncludesOption(answer, answer.userAnswer, option.value) &&
                !answerIncludesOption(answer, answer.correctAnswer, option.value)
            }"
            >{{ option.label }}</el-radio
          >
        </el-radio-group>
        <div v-else class="practice-short-answer">
          <el-input
            :model-value="answerForm[answer.questionId] || ''"
            :disabled="submitted"
            type="textarea"
            :rows="5"
            :aria-labelledby="'stem-' + answer.questionId"
            :aria-describedby="submitted ? undefined : 'answer-length-' + answer.questionId"
            :aria-invalid="!submitted && isAnswerTooLong(answer.questionId)"
            placeholder="用自己的话写下理解，也可以列出关键步骤…"
            @update:model-value="setAnswer(answer.questionId, $event)"
          />
          <p
            v-if="!submitted"
            :id="'answer-length-' + answer.questionId"
            class="practice-short-answer__length"
            :class="{ 'practice-short-answer__length--error': isAnswerTooLong(answer.questionId) }"
          >
            {{ (answerForm[answer.questionId] || '').length }} / {{ MAX_PRACTICE_ANSWER_LENGTH }} 字符
            <span v-if="isAnswerTooLong(answer.questionId)" role="alert">
              请精简至 {{ MAX_PRACTICE_ANSWER_LENGTH }} 字符以内后提交。
            </span>
          </p>
        </div>

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

    <div class="page-card practice-paper-submit" :class="{ 'practice-paper-submit--pending': !submitted }">
      <div class="practice-paper-submit__intro">
        <h3>{{ submitted ? '接着复习' : '提交练习' }}</h3>
        <p class="practice-paper-submit__description">
          {{
            submitted
              ? '去错题本回顾薄弱点，也可以重新练习这套题。'
              : unansweredCount
                ? '还有 ' + unansweredCount + ' 道题未作答，提交前再检查一下。'
                : '所有题目已作答，提交后即可查看成绩与解析。'
          }}
        </p>
        <p v-if="!submitted" class="practice-paper-submit__mobile-status">
          {{ unansweredCount ? '还有 ' + unansweredCount + ' 道题未答' : '全部题目已作答' }}
        </p>
      </div>
      <div class="practice-paper-submit__actions">
        <el-button @click="emit('back')">{{ backLabel }}</el-button>
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
import GenerationModeBadge from '@/components/GenerationModeBadge.vue'
import MobileIcon from '@/components/MobileIcon.vue'
import { useNoteWorkspace } from '@/composables/useNoteWorkspace'
import { formatDateTime } from '@/utils/format'
import { formatQuestionType } from '@/utils/labels'
import {
  answerIncludesOption,
  buildOptions,
  formatAnswer,
  isChoiceQuestion,
  isMultiQuestion,
  isPendingReview,
  isShortQuestion,
  joinMultiAnswer,
  MAX_PRACTICE_ANSWER_LENGTH,
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
const props = withDefaults(
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
    backLabel?: string
  }>(),
  {
    hasPendingAiReview: false,
    pendingReviewText: '',
    submitting: false,
    restarting: false,
    backLabel: '返回题集'
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
const { createNote } = useNoteWorkspace()
const recordQuestionNote = (answer: PracticeSection['items'][number]) => {
  createNote({
    sourceType: 'QUESTION',
    sourceId: answer.questionId,
    practiceSessionId: props.practiceDetail.sessionId,
    sourceTitle: `${props.practiceDetail.sessionName} · 第 ${answer.globalIndex} 题`,
    sourceExcerpt: (answer.stemText || '').trim().slice(0, 2000)
  })
}

const setAnswer = (questionId: number, value: string) => {
  answerForm.value = { ...answerForm.value, [questionId]: value }
}

const isAnswerTooLong = (questionId: number) => (answerForm.value[questionId] || '').length > MAX_PRACTICE_ANSWER_LENGTH
</script>

<style scoped>
.practice-paper-main {
  min-width: 0;
}
.practice-paper-mobile-overview,
.practice-paper-submit__mobile-status {
  display: none;
}
.practice-short-answer__length {
  margin: 8px 0 0;
  text-align: right;
  font-size: 12px;
  color: var(--muted);
}
.practice-short-answer__length--error {
  color: var(--el-color-danger);
}
.practice-short-answer__length span {
  display: block;
  margin-top: 4px;
}
.practice-paper-hero {
  padding: 0;
  border-radius: var(--radius);
  overflow: hidden;
  box-shadow: none;
}
.practice-paper-hero__head {
  padding: 24px;
  gap: 20px;
}
.practice-paper-hero__intro {
  min-width: 0;
}
.practice-paper-hero__title {
  font-size: 20px;
  line-height: 1.5;
  font-weight: 600;
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
  margin: 0;
  padding: 15px 26px;
  background: var(--brand-light);
  color: var(--brand);
  font-size: 13px;
  line-height: 1.8;
}
.practice-paper-section {
  padding: 24px;
  border-radius: var(--radius);
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
  scroll-margin-top: calc(var(--shell-header-height, 72px) + 20px);
}
.practice-paper-question + .practice-paper-question {
  margin-top: 30px;
  padding-top: 28px;
  border-top: 1px solid var(--line);
}
.practice-paper-question__head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.practice-paper-question__note {
  display: grid;
  place-items: center;
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
  margin-left: auto;
  padding: 0;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}
.practice-paper-question__note:hover {
  color: var(--brand);
  background: var(--brand-light);
}
.practice-paper-question__note:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
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
.practice-paper-question__hint {
  margin: -6px 0 16px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
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
.practice-paper-choice-group :deep(.el-radio.practice-paper-choice--correct),
.practice-paper-choice-group :deep(.el-checkbox.practice-paper-choice--correct) {
  border-color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}
.practice-paper-choice-group :deep(.el-radio.practice-paper-choice--incorrect),
.practice-paper-choice-group :deep(.el-checkbox.practice-paper-choice--incorrect) {
  border-color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
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
  .practice-paper-submit__actions {
    width: 100%;
  }
  .practice-paper-submit__actions :deep(.el-button) {
    flex: 1 1 auto;
  }
}
@media (max-width: 760px) {
  .practice-paper-main {
    display: contents;
  }
  .practice-paper-hero,
  .practice-paper-section,
  .practice-paper-submit {
    padding: 0;
    border: 0;
    border-radius: 0;
    background: transparent;
  }
  .practice-paper-hero__head,
  .practice-paper-hero > .practice-paper-note,
  .practice-paper-progress,
  .practice-paper-special-review-note,
  .practice-paper-section__title {
    display: none;
  }
  .practice-paper-mobile-overview {
    display: block;
  }
  .practice-paper-mobile-overview h2 {
    margin: 0;
    color: var(--text);
    font-size: 21px;
    font-weight: 650;
    line-height: 1.45;
    overflow-wrap: anywhere;
  }
  .practice-paper-mobile-overview__status {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 8px 12px;
    margin: 14px 0 12px;
    color: var(--muted);
    font-size: 13px;
  }
  .practice-paper-mobile-overview__status strong {
    color: var(--text);
    font-size: 16px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }
  .practice-paper-mobile-info {
    margin-top: 4px;
    color: var(--muted);
    font-size: 12px;
    line-height: 1.7;
  }
  .practice-paper-mobile-info summary {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    min-height: 44px;
    list-style: none;
    cursor: pointer;
  }
  .practice-paper-mobile-info summary::-webkit-details-marker {
    display: none;
  }
  .practice-paper-mobile-info summary:focus-visible {
    border-radius: 6px;
    outline: 2px solid var(--brand);
    outline-offset: 2px;
  }
  .practice-paper-mobile-info__toggle {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    flex-shrink: 0;
    padding-inline: 4px;
    color: var(--text-secondary);
  }
  .practice-paper-mobile-info__toggle::after {
    width: 6px;
    height: 6px;
    border-right: 1.5px solid currentColor;
    border-bottom: 1.5px solid currentColor;
    content: '';
    transform: translateY(-2px) rotate(45deg);
  }
  .practice-paper-mobile-info[open] .practice-paper-mobile-info__toggle::after {
    transform: translateY(2px) rotate(225deg);
  }
  .practice-paper-mobile-info__body {
    margin-top: 4px;
    padding: 12px 14px 4px;
    border-radius: 12px;
    background: var(--panel);
  }
  .practice-paper-mobile-info__body p {
    margin: 0 0 6px;
  }
  .practice-paper-mobile-info__body :deep(.el-button) {
    min-height: 44px;
    margin: 0;
    padding-inline: 0;
    font-size: 13px;
  }
  .practice-paper-question + .practice-paper-question {
    margin-top: 28px;
    padding-top: 24px;
  }
  .practice-paper-question__index,
  .practice-paper-question__type {
    font-size: 13px;
  }
  .practice-paper-question__stem {
    margin: 12px 0 18px;
    font-size: 18px;
    line-height: 1.7;
    font-weight: 600;
  }
  .practice-paper-question__hint {
    margin: -6px 0 16px;
    font-size: 13px;
  }
  .practice-paper-choice-group {
    gap: 10px;
  }
  .practice-paper-choice-group :deep(.el-radio.is-bordered),
  .practice-paper-choice-group :deep(.el-checkbox.is-bordered) {
    min-height: 54px;
    padding: 15px 14px;
    border-color: transparent;
    border-radius: 12px;
    background: var(--panel);
  }
  .practice-paper-choice-group :deep(.el-radio__label),
  .practice-paper-choice-group :deep(.el-checkbox__label) {
    padding-left: 12px;
    font-size: 16px;
    line-height: 1.65;
  }
  .practice-paper-choice-group :deep(.el-radio__input),
  .practice-paper-choice-group :deep(.el-checkbox__input) {
    margin-top: 5px;
  }
  .practice-paper-choice-group :deep(.el-radio.is-checked),
  .practice-paper-choice-group :deep(.el-checkbox.is-checked) {
    border-color: var(--brand);
    background: var(--brand-light);
  }
  .practice-paper-choice-group :deep(.el-radio.practice-paper-choice--correct),
  .practice-paper-choice-group :deep(.el-checkbox.practice-paper-choice--correct) {
    border-color: var(--green);
    background: var(--green-soft);
  }
  .practice-paper-choice-group :deep(.el-radio.practice-paper-choice--incorrect),
  .practice-paper-choice-group :deep(.el-checkbox.practice-paper-choice--incorrect) {
    border-color: var(--red);
    background: var(--red-soft);
  }
  .practice-short-answer :deep(.el-textarea__inner) {
    min-height: 160px;
    padding: 16px;
    border-radius: 12px;
    font-size: 16px;
    line-height: 1.8;
  }
  .practice-short-answer__length {
    margin-top: 12px;
    font-size: 13px;
  }
  .practice-paper-result {
    margin-top: 24px;
    padding: 18px;
    border: 0;
    border-left: 3px solid var(--accent);
    border-radius: 14px;
    background: var(--panel);
    font-size: 15px;
  }
  .practice-paper-result--correct {
    border-left-color: var(--green);
  }
  .practice-paper-result--pending {
    border-left-color: var(--muted);
  }
  .practice-paper-review {
    grid-template-columns: minmax(0, 1fr);
    gap: 5px;
    margin-top: 16px;
  }
  .practice-paper-review > span,
  .practice-paper-analysis strong {
    font-size: 13px;
  }
  .practice-paper-sources summary {
    padding-block: 12px;
    min-height: 48px;
  }
  .practice-paper-submit {
    gap: 12px;
    padding-top: 18px;
    border-top: 1px solid var(--line);
  }
  .practice-paper-submit h3,
  .practice-paper-submit__description {
    display: none;
  }
  .practice-paper-submit:not(.practice-paper-submit--pending) .practice-paper-submit__intro {
    display: none;
  }
  .practice-paper-submit .practice-paper-submit__mobile-status {
    display: block;
    margin: 0;
    font-size: 13px;
  }
  .practice-paper-submit__actions {
    width: 100%;
    gap: 12px;
  }
  .practice-paper-submit__actions :deep(.el-button) {
    flex: 1 1 auto;
    min-height: 52px;
    border-radius: 12px;
    font-size: 16px;
  }
  .practice-paper-submit--pending {
    padding-bottom: 12px;
  }
  .practice-paper-submit--pending .practice-paper-submit__actions :deep(.el-button--primary) {
    border: 0;
    background: var(--mobile-accent, var(--brand));
    color: var(--text);
    font-weight: 650;
  }
}
</style>
