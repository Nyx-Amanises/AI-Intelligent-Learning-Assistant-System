<template>
  <div class="study-landscape" aria-hidden="true">
    <svg class="study-landscape__wide" viewBox="0 0 1200 500" preserveAspectRatio="xMidYMid slice">
      <defs>
        <linearGradient id="study-wide-sky" x2="0" y2="1">
          <stop stop-color="var(--scene-sky-top)" />
          <stop offset="1" stop-color="var(--scene-sky-bottom)" />
        </linearGradient>
        <linearGradient id="study-wide-water" x2="0.15" y2="1">
          <stop stop-color="var(--scene-water-top)" />
          <stop offset="1" stop-color="var(--scene-water-bottom)" />
        </linearGradient>
        <linearGradient id="study-wide-paper" x1="0" y1="0" x2="1" y2="1">
          <stop stop-color="#ffffff" />
          <stop offset="1" stop-color="#e4eeeb" />
        </linearGradient>
        <radialGradient id="study-wide-haze">
          <stop stop-color="#ffffff" stop-opacity=".8" />
          <stop offset="1" stop-color="#ffffff" stop-opacity="0" />
        </radialGradient>
      </defs>
      <path fill="url(#study-wide-sky)" d="M0 0h1200v500H0z" />
      <ellipse cx="400" cy="95" rx="520" ry="245" fill="url(#study-wide-haze)" />
      <circle cx="936" cy="100" r="35" fill="var(--scene-sun)" opacity=".17" />
      <circle cx="936" cy="100" r="24" fill="var(--scene-sun)" opacity=".48" />
      <g fill="none" stroke="#fff" stroke-linecap="round" opacity=".65">
        <path d="M88 120h122m-65 11h88M995 158h110m-57 10h98" stroke-width="1.5" />
      </g>
      <path
        d="M-40 255C62 242 116 176 212 207S369 258 451 218s135-32 219-2 157 25 242-17 187-14 330 30v271H-40Z"
        fill="var(--scene-hill-far)"
      />
      <path
        d="M-20 279c124-43 191-48 294-17s166-46 259-25 146 44 245 7 153-31 258 4 157 18 204 5v247H-20Z"
        fill="var(--scene-hill-mid)"
      />
      <path d="M-10 295c132-42 251 10 383-16s234 17 388-14 269 23 449 7v228H-10Z" fill="var(--scene-hill-near)" />
      <path d="M0 295c184 6 286-13 442-2s266-8 392 0 263-8 366-1v208H0Z" fill="url(#study-wide-water)" />
      <g fill="none" stroke="var(--scene-line)" stroke-linecap="round" opacity=".35">
        <path d="M60 307c102 5 174-7 263-2m522 0c117 6 224-4 328 0" />
        <path d="M104 347h60m837-20h70M175 426h70m742 21h103" />
      </g>
      <g transform="translate(400 -260)">
        <g class="study-landscape__ripples" fill="none" stroke="#fff" stroke-linecap="round">
          <path d="M98 588c50 9 120 9 177 0" opacity=".7" />
          <path d="M125 603c37 5 91 5 129 0" opacity=".5" />
        </g>
        <g class="study-landscape__boat">
          <ellipse cx="202" cy="581" rx="66" ry="7" fill="var(--scene-line)" opacity=".11" />
          <path d="m139 552 58 16 70-20-34 36h-62Z" fill="#f7faf5" />
          <path d="m197 568 36 16 34-36Z" fill="#d5e3df" />
          <path d="m139 552 32 32 26-16Z" fill="#e1ede6" />
          <path d="M198 564V475l-45 68Z" fill="url(#study-wide-paper)" />
          <path d="m198 475 49 66-49 23Z" fill="#fefdf5" />
          <path d="m198 475 11 63-11 26Z" fill="#d7e5df" />
          <path d="m160 540 29 11m26-19 20 7" fill="none" stroke="#b7ceca" stroke-width="1.2" opacity=".55" />
          <path d="M198 475v-13l15 7Z" fill="var(--scene-sun)" />
        </g>
      </g>
      <g fill="none" stroke="var(--scene-line)" stroke-width="1.3" stroke-linecap="round" opacity=".55">
        <path d="M315 172q5-5 10 0 5-5 10 0m15-19q4-4 8 0 4-4 8 0" />
      </g>
    </svg>
    <svg class="study-landscape__portrait" viewBox="0 0 400 860" preserveAspectRatio="xMidYMid slice">
      <defs>
        <linearGradient id="study-sky" x2="0" y2="1">
          <stop stop-color="var(--scene-sky-top)" />
          <stop offset="1" stop-color="var(--scene-sky-bottom)" />
        </linearGradient>
        <linearGradient id="study-water" x2="0.15" y2="1">
          <stop stop-color="var(--scene-water-top)" />
          <stop offset="1" stop-color="var(--scene-water-bottom)" />
        </linearGradient>
        <linearGradient id="study-paper" x1="0" y1="0" x2="1" y2="1">
          <stop stop-color="#ffffff" />
          <stop offset="1" stop-color="#e4eeeb" />
        </linearGradient>
        <radialGradient id="study-haze">
          <stop stop-color="#ffffff" stop-opacity=".8" />
          <stop offset="1" stop-color="#ffffff" stop-opacity="0" />
        </radialGradient>
      </defs>
      <path fill="url(#study-sky)" d="M0 0h400v860H0z" />
      <ellipse cx="115" cy="200" rx="270" ry="260" fill="url(#study-haze)" />
      <g class="study-landscape__sun">
        <circle cx="314" cy="175" r="34" fill="var(--scene-sun)" opacity=".17" />
        <circle cx="314" cy="175" r="23" fill="var(--scene-sun)" opacity=".48" />
      </g>
      <g class="study-landscape__cloud" fill="none" stroke="#fff" stroke-linecap="round" opacity=".7">
        <path d="M-40 146h112m-44 9h86" stroke-width="2" />
        <path d="M298 237h91m-50 11h83" stroke-width="1.5" />
      </g>
      <path d="M-50 419C14 408 66 338 118 367s77 41 115 2 99-70 209-40v269H-50Z" fill="var(--scene-hill-far)" />
      <path d="M-25 451c75-76 122-36 172-37s65-67 122-61 92 46 151 33v210H-25Z" fill="var(--scene-hill-mid)" />
      <path d="M-20 474c91-47 128-20 182-26s115-58 174-25 88 15 114 9v155H-20Z" fill="var(--scene-hill-near)" />
      <path d="M-10 485c95 8 151-18 217-4s118-11 203-2v381H-10Z" fill="url(#study-water)" />
      <g fill="none" stroke="var(--scene-line)" stroke-linecap="round" opacity=".4">
        <path d="M-9 497c54 6 102-9 161-4m106-2c51 4 107 0 152 4" />
        <path d="M14 521h49m258-9h54M-11 622h50m309 28h69" />
      </g>
      <g class="study-landscape__ripples" fill="none" stroke="#fff" stroke-linecap="round">
        <path d="M98 588c50 9 120 9 177 0" opacity=".7" />
        <path d="M125 603c37 5 91 5 129 0" opacity=".5" />
        <path d="M82 636h37m159-11h40M35 692h59m224 35h78" opacity=".48" />
      </g>
      <g class="study-landscape__boat">
        <ellipse cx="202" cy="581" rx="66" ry="7" fill="var(--scene-line)" opacity=".11" />
        <path d="m139 552 58 16 70-20-34 36h-62Z" fill="#f7faf5" />
        <path d="m197 568 36 16 34-36Z" fill="#d5e3df" />
        <path d="m139 552 32 32 26-16Z" fill="#e1ede6" />
        <path d="M198 564V475l-45 68Z" fill="url(#study-paper)" />
        <path d="m198 475 49 66-49 23Z" fill="#fefdf5" />
        <path d="m198 475 11 63-11 26Z" fill="#d7e5df" />
        <path d="m160 540 29 11m26-19 20 7" fill="none" stroke="#b7ceca" stroke-width="1.2" opacity=".55" />
        <path d="M198 475v-13l15 7Z" fill="var(--scene-sun)" />
      </g>
      <g
        class="study-landscape__birds"
        fill="none"
        stroke="var(--scene-line)"
        stroke-width="1.3"
        stroke-linecap="round"
        opacity=".55"
      >
        <path d="M76 318q5-5 10 0 5-5 10 0m15-19q4-4 8 0 4-4 8 0" />
      </g>
    </svg>
  </div>
</template>

<style scoped>
.study-landscape {
  --scene-sky-top: #e6eee9;
  --scene-sky-bottom: #dcebed;
  --scene-water-top: #c9e2e5;
  --scene-water-bottom: #b8dbe5;
  --scene-hill-far: #d4e2e0;
  --scene-hill-mid: #c5d8d8;
  --scene-hill-near: #b3ced1;
  --scene-sun: #efb779;
  --scene-line: #709a9f;
  display: block;
  width: 100%;
  height: 100%;
}
.study-landscape__portrait,
.study-landscape__wide {
  width: 100%;
  height: 100%;
}
.study-landscape__portrait {
  display: block;
}
.study-landscape__wide {
  display: none;
}
@media (min-width: 761px) {
  .study-landscape__portrait {
    display: none;
  }
  .study-landscape__wide {
    display: block;
  }
}
:global([data-theme='eye-care'] .study-landscape) {
  --scene-sky-top: #ece8d9;
  --scene-sky-bottom: #e6e8d5;
  --scene-water-top: #d4dfcb;
  --scene-water-bottom: #c2d8c1;
  --scene-hill-far: #dee1d0;
  --scene-hill-mid: #cdd8c4;
  --scene-hill-near: #b9cdb8;
  --scene-line: #7f9d87;
}
.study-landscape__boat {
  transform-origin: 201px 564px;
  animation: study-boat 8s ease-in-out infinite;
}
.study-landscape__cloud {
  animation: study-cloud 26s ease-in-out infinite alternate;
}
.study-landscape__ripples {
  transform-origin: 200px 605px;
  animation: study-ripple 8s ease-in-out infinite;
}
.study-landscape__birds {
  animation: study-cloud 20s ease-in-out infinite alternate-reverse;
}
@keyframes study-boat {
  0%,
  100% {
    transform: translateY(0) rotate(-1.5deg);
  }
  50% {
    transform: translateY(-7px) rotate(1.5deg);
  }
}
@keyframes study-cloud {
  from {
    transform: translateX(-10px);
  }
  to {
    transform: translateX(16px);
  }
}
@keyframes study-ripple {
  0%,
  100% {
    transform: scaleX(0.94);
    opacity: 0.65;
  }
  50% {
    transform: scaleX(1.04);
    opacity: 1;
  }
}
@media (prefers-reduced-motion: reduce) {
  .study-landscape__boat,
  .study-landscape__cloud,
  .study-landscape__ripples,
  .study-landscape__birds {
    animation: none;
  }
}
</style>
