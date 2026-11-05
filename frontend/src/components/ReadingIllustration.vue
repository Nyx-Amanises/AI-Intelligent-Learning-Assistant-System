<template>
  <svg
    class="reading-illustration"
    :class="{ 'is-paused': paused }"
    viewBox="0 0 440 300"
    fill="none"
    aria-hidden="true"
    focusable="false"
  >
    <ellipse class="reading-illustration__wash" cx="239" cy="156" rx="152" ry="112" />
    <path class="reading-illustration__orbit" d="M117 114C139 64 193 44 243 55" />
    <circle class="reading-illustration__sun" cx="273" cy="59" r="13" />

    <g class="reading-illustration__plant">
      <path d="M346 199C347 174 337 158 339 132C341 111 348 94 346 77" />
      <path d="M342 155C357 141 369 126 373 109M340 140C325 126 317 114 313 98" />
      <path class="reading-illustration__leaf" d="M346 103C331 98 330 82 338 71C350 80 352 91 346 103Z" />
      <path class="reading-illustration__leaf" d="M341 124C345 107 361 100 373 103C369 117 357 126 341 124Z" />
      <path class="reading-illustration__leaf" d="M329 125C312 124 303 111 305 100C319 102 328 110 329 125Z" />
      <path class="reading-illustration__leaf" d="M354 143C352 128 364 119 376 120C374 133 367 141 354 143Z" />
      <path class="reading-illustration__leaf" d="M341 159C324 157 316 147 316 136C332 137 341 145 341 159Z" />
    </g>
    <path class="reading-illustration__pot" d="M331 194H359L356 224H335L331 194Z" />
    <path d="M330 194H360M336 201H355" />

    <ellipse class="reading-illustration__shadow" cx="209" cy="250" rx="151" ry="12" />
    <path class="reading-illustration__desk" d="M41 256H387M395 256H405" />

    <path
      class="reading-illustration__cover"
      d="M84 153L69 239C112 226 166 235 207 254C249 235 288 229 334 238L320 150L207 165L84 153Z"
    />
    <path
      class="reading-illustration__paper"
      d="M88 150L75 235C122 225 165 233 207 249C246 233 288 225 329 235L317 145L207 161L88 150Z"
    />
    <path d="M81 235C125 227 166 237 199 248M214 247C252 233 289 227 323 235" />

    <path class="reading-illustration__paper" d="M207 159C170 139 130 137 91 148L78 229C125 219 166 227 207 245V159Z" />
    <path
      class="reading-illustration__paper"
      d="M207 159C245 137 281 133 316 143L327 229C286 219 246 229 207 245V159Z"
    />
    <path class="reading-illustration__spine" d="M207 162V240" />
    <g class="reading-illustration__text">
      <path
        d="M108 161C130 157 150 159 168 163M105 175C135 170 162 175 186 185M103 185C133 181 158 185 184 195M101 195C131 191 157 195 183 205M99 206C121 203 142 205 159 210"
      />
      <path
        d="M230 170C253 159 274 154 299 157M231 181C253 171 275 167 300 168M232 191C254 181 276 177 301 179M233 202C255 192 276 188 302 190M234 212C250 205 264 202 280 201"
      />
    </g>
    <path class="reading-illustration__bookmark" d="M170 230L169 252L176 248L182 255L183 235" />

    <g class="reading-illustration__turning-page">
      <path
        class="reading-illustration__paper"
        d="M207 159C245 137 281 133 316 143L327 229C286 219 246 229 207 245V159Z"
      />
      <g class="reading-illustration__text">
        <path
          d="M230 170C253 159 274 154 299 157M231 181C253 171 275 167 300 168M232 191C254 181 276 177 301 179M233 202C255 192 276 188 302 190M234 212C250 205 264 202 280 201"
        />
      </g>
    </g>

    <g class="reading-illustration__pencil" transform="rotate(-14 99 263)">
      <path class="reading-illustration__pencil-body" d="M87 260H146V265H87Z" />
      <path class="reading-illustration__paper" d="M87 260L77 262.5L87 265Z" />
      <path d="M80 262L77 262.5L80 263M139 260V265" />
    </g>
  </svg>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ paused?: boolean }>(), { paused: false })
</script>

<style scoped>
.reading-illustration {
  --illustration-ink: var(--brand, #345c49);
  --illustration-paper: #fffef9;
  --illustration-leaf: #dce6d9;
  display: block;
  width: 100%;
  height: auto;
  overflow: visible;
  stroke: var(--illustration-ink);
  stroke-width: 1.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.reading-illustration__wash {
  fill: var(--brand-soft, #e9eee4);
  fill-opacity: 0.6;
  stroke: none;
}
.reading-illustration__orbit {
  stroke-opacity: 0.17;
}
.reading-illustration__sun {
  fill: #d9b46e;
  fill-opacity: 0.65;
  stroke: none;
}
.reading-illustration__plant {
  transform-origin: 346px 198px;
  animation: reading-leaves 9s ease-in-out infinite;
}
.reading-illustration__leaf,
.reading-illustration__cover {
  fill: var(--illustration-leaf);
}
.reading-illustration__pot {
  fill: var(--illustration-paper);
}
.reading-illustration__shadow {
  fill: var(--illustration-ink);
  fill-opacity: 0.055;
  stroke: none;
}
.reading-illustration__desk {
  stroke-opacity: 0.35;
}
.reading-illustration__paper {
  fill: var(--illustration-paper);
}
.reading-illustration__spine {
  stroke-opacity: 0.55;
}
.reading-illustration__text {
  stroke-opacity: 0.3;
  stroke-width: 1.1;
}
.reading-illustration__bookmark,
.reading-illustration__pencil-body {
  fill: #d9b46e;
}
.reading-illustration__turning-page {
  transform-origin: 207px 202px;
  animation: reading-page-turn 10s ease-in-out infinite;
  opacity: 0;
}
.reading-illustration.is-paused *,
.reading-illustration.is-paused {
  animation-play-state: paused;
}
@keyframes reading-leaves {
  0%,
  100% {
    transform: rotate(-1.4deg);
  }
  50% {
    transform: rotate(1.4deg);
  }
}
@keyframes reading-page-turn {
  0%,
  16% {
    opacity: 0;
    transform: scaleX(1) skewY(0deg);
  }
  20% {
    opacity: 1;
    transform: scaleX(1) skewY(0deg);
  }
  39% {
    opacity: 1;
    transform: scaleX(0.08) skewY(-12deg);
  }
  58%,
  64% {
    opacity: 1;
    transform: scaleX(-0.98) skewY(5deg);
  }
  72%,
  100% {
    opacity: 0;
    transform: scaleX(-0.98) skewY(5deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .reading-illustration *,
  .reading-illustration {
    animation: none;
  }
}
</style>
