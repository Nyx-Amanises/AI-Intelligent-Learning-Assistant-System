<template>
  <span class="resource-cover" :class="`resource-cover--${artwork.key}`" aria-hidden="true">
    <img
      v-if="coverSrc && !imageFailed"
      :key="coverSrc"
      :src="coverSrc"
      class="resource-cover__image"
      :class="{ 'is-loaded': imageLoaded }"
      width="72"
      height="96"
      alt=""
      loading="lazy"
      decoding="async"
      referrerpolicy="no-referrer"
      @load="imageLoaded = true"
      @error="imageFailed = true"
    />
    <svg
      v-show="!imageLoaded || imageFailed"
      class="resource-cover__fallback"
      width="72"
      height="96"
      viewBox="0 0 72 96"
      fill="none"
      focusable="false"
    >
      <rect width="72" height="96" rx="5" class="resource-cover__paper" />
      <path d="M5 0H0v96h5Z" class="resource-cover__spine" />
      <path d="M8 1v94" class="resource-cover__binding" />
      <circle cx="65" cy="39" r="26" class="resource-cover__wash" />
      <text x="13" y="20" class="resource-cover__subject">{{ artwork.label }}</text>

      <g class="resource-cover__art" stroke-linecap="round" stroke-linejoin="round">
        <g v-if="artwork.key === 'calculus'">
          <path d="M16 69c9 0 8-24 19-22s14 17 24-13v38H16Z" fill="currentColor" opacity=".12" />
          <path d="M15 33v39h47M15 43h47M15 58h47M30 33v39M45 33v39" opacity=".22" stroke-width=".7" />
          <path d="M16 69c9 0 8-24 19-22s14 17 24-13" stroke-width="2" />
          <path d="M35 47v25" stroke-dasharray="2 3" opacity=".6" />
          <circle cx="35" cy="47" r="2.3" fill="currentColor" stroke="none" />
          <path d="m56 36 3-2 .3 4" stroke-width="1.3" />
        </g>

        <g v-else-if="artwork.key === 'algebra'">
          <path d="M21 34h35v36H21Z" fill="currentColor" opacity=".07" stroke="none" />
          <path d="M22 35h10v10H22zm11 12h10v10H33zm11 12h10v10H44Z" fill="currentColor" opacity=".17" stroke="none" />
          <path d="M19 34h-4v36h4m39-36h4v36h-4" stroke-width="1.8" />
          <g class="resource-cover__matrix" stroke="none" fill="currentColor">
            <text x="26" y="43">2</text>
            <text x="38" y="43">0</text>
            <text x="50" y="43">1</text>
            <text x="26" y="55">1</text>
            <text x="38" y="55">3</text>
            <text x="50" y="55">0</text>
            <text x="26" y="67">0</text>
            <text x="38" y="67">2</text>
            <text x="50" y="67">1</text>
          </g>
        </g>

        <g v-else-if="artwork.key === 'probability'">
          <path d="M14 70c14 0 12-32 24-32s10 32 24 32Z" fill="currentColor" opacity=".1" stroke="none" />
          <path d="M22 70v-9m8 9V46m8 24V39m8 31V46m8 24v-9" stroke-width="5" opacity=".19" />
          <path d="M14 70c14 0 12-32 24-32s10 32 24 32" stroke-width="1.8" />
          <path d="M13 72h50M38 32v42" stroke-width=".8" opacity=".4" />
          <circle cx="38" cy="38" r="2.2" fill="currentColor" stroke="none" />
        </g>

        <g v-else-if="artwork.key === 'physics'">
          <circle cx="38" cy="52" r="18" fill="currentColor" opacity=".06" stroke="none" />
          <ellipse cx="38" cy="52" rx="23" ry="9" stroke-width="1.1" />
          <ellipse cx="38" cy="52" rx="23" ry="9" transform="rotate(60 38 52)" stroke-width="1.1" />
          <ellipse cx="38" cy="52" rx="23" ry="9" transform="rotate(120 38 52)" stroke-width="1.1" />
          <g fill="currentColor" stroke="none">
            <circle cx="38" cy="52" r="3.5" />
            <circle cx="58" cy="47.5" r="2.2" />
            <circle cx="29" cy="32.5" r="2.2" />
            <circle cx="27" cy="69" r="2.2" />
          </g>
        </g>

        <g v-else-if="artwork.key === 'english'">
          <path
            d="M15 35q11-4 23 2 12-6 23-2v32q-11-4-23 2-12-6-23-2Z"
            fill="currentColor"
            opacity=".08"
            stroke="none"
          />
          <path d="M15 35q11-4 23 2 12-6 23-2v32q-11-4-23 2-12-6-23-2Z" stroke-width="1.2" />
          <path d="M38 38v30m-18-9h11m-11 5h11m13-4h11m-11 4h11" stroke-width=".9" opacity=".55" />
          <text x="19" y="53" class="resource-cover__letter" fill="currentColor" stroke="none">Aa</text>
          <path d="m46 52 7-14 3 1.5-7 14-4 3Z" fill="currentColor" stroke="none" />
        </g>

        <g v-else>
          <path d="M19 32h30l9 9v31H19Z" fill="currentColor" opacity=".08" stroke="none" />
          <path d="M19 32h30l9 9v31H19Zm30 0v9h9M26 49h24M26 56h24M26 63h16" stroke-width="1.4" />
        </g>
      </g>

      <path d="M13 81h47" class="resource-cover__rule" />
      <text x="13" y="90" class="resource-cover__kind">{{ kindLabel }}</text>
    </svg>
  </span>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  subject: string
  title: string
  resourceKind?: string
  coverUrl?: string | null
}>()

const imageLoaded = ref(false)
const imageFailed = ref(false)
const coverSrc = computed(() => {
  if (!props.coverUrl?.trim()) return ''
  if (/^\/open-books\/[a-z0-9-]+\/cover\.webp$/.test(props.coverUrl)) return props.coverUrl
  try {
    const url = new URL(props.coverUrl.trim())
    return ['https:', 'http:', 'blob:'].includes(url.protocol) ? url.href : ''
  } catch {
    return ''
  }
})

watch(
  coverSrc,
  () => {
    imageLoaded.value = false
    imageFailed.value = false
  },
  { flush: 'sync' }
)

const subjectArtworks = [
  { key: 'calculus', label: '高等数学', match: /高等数学|高数|微积分|calculus/i },
  { key: 'algebra', label: '线性代数', match: /线性代数|线代|linear\s*algebra/i },
  { key: 'probability', label: '概率统计', match: /概率|统计|probability|statistics/i },
  { key: 'physics', label: '大学物理', match: /物理|physics/i },
  { key: 'english', label: '大学英语', match: /英语|英文|english|academic\s*(writing|reading)/i }
]

const artwork = computed(
  () =>
    subjectArtworks.find((item) => item.match.test(props.subject)) ??
    subjectArtworks.find((item) => item.match.test(props.title)) ?? { key: 'general', label: '课程资料' }
)

const kindLabels: Record<string, string> = {
  TEXTBOOK: '教材',
  COURSE_PACK: '课程讲义',
  REVIEW_PACK: '复习资料'
}
const kindLabel = computed(() => kindLabels[props.resourceKind ?? ''] ?? '学习资料')
</script>

<style scoped>
.resource-cover {
  --cover-accent: #376e56;
  --cover-paper: #e8f0e8;
  --cover-ink: #284d3b;
  --cover-art: var(--cover-accent);
  position: relative;
  display: block;
  flex-shrink: 0;
  width: var(--resource-cover-width, 72px);
  height: auto;
  aspect-ratio: 3 / 4;
  overflow: hidden;
  border-radius: 5px;
  font-family: var(--font-sans, sans-serif);
}

.resource-cover__image,
.resource-cover__fallback {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
}

.resource-cover__image {
  object-fit: contain;
  opacity: 0;
}

.resource-cover__image.is-loaded {
  opacity: 1;
}

.resource-cover--algebra {
  --cover-accent: #496b9c;
  --cover-paper: #e8edf6;
  --cover-ink: #344f78;
}

.resource-cover--probability {
  --cover-accent: #94702f;
  --cover-paper: #f4edde;
  --cover-ink: #715020;
}

.resource-cover--physics {
  --cover-accent: #77609c;
  --cover-paper: #eee8f5;
  --cover-ink: #574572;
}

.resource-cover--english {
  --cover-accent: #a06353;
  --cover-paper: #f4e8e2;
  --cover-ink: #764639;
}

.resource-cover__paper {
  fill: var(--cover-paper);
}

.resource-cover__spine {
  fill: var(--cover-art);
  opacity: 0.28;
}

.resource-cover__binding,
.resource-cover__rule {
  stroke: var(--cover-art);
  stroke-width: 0.6;
  opacity: 0.2;
}

.resource-cover__wash {
  fill: var(--cover-art);
  opacity: 0.05;
}

.resource-cover__subject {
  fill: var(--cover-ink);
  font-size: 10.5px;
  font-weight: 650;
  letter-spacing: 0.3px;
}

.resource-cover__art {
  color: var(--cover-art);
  stroke: currentColor;
}

.resource-cover__matrix {
  font-family: var(--font-mono, monospace);
  font-size: 8px;
  text-anchor: middle;
}

.resource-cover__letter {
  font-family: var(--font-serif, serif);
  font-size: 15px;
  font-style: italic;
}

.resource-cover__kind {
  fill: var(--cover-ink);
  font-size: 7px;
  font-weight: 500;
  letter-spacing: 1px;
}

:global([data-theme='dark'] .resource-cover),
:global(.dark .resource-cover) {
  --cover-paper: color-mix(in srgb, var(--cover-accent) 24%, #1a211f);
  --cover-ink: #eef2ed;
  --cover-art: color-mix(in srgb, var(--cover-accent) 45%, #ffffff);
}
</style>
