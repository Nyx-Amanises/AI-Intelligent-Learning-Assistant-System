export interface LearningQuote {
  readonly text: string
  readonly author: string
  readonly source: string
}

export const LEARNING_QUOTES: readonly LearningQuote[] = [
  { text: '温故而知新，可以为师矣。', author: '孔子', source: '论语·为政' },
  { text: '学而不思则罔，思而不学则殆。', author: '孔子', source: '论语·为政' },
  { text: '知之者不如好之者，好之者不如乐之者。', author: '孔子', source: '论语·雍也' },
  { text: '不积跬步，无以至千里；不积小流，无以成江海。', author: '荀子', source: '荀子·劝学' },
  { text: '吾尝终日而思矣，不如须臾之所学也。', author: '荀子', source: '荀子·劝学' },
  { text: '人非生而知之者，孰能无惑？', author: '韩愈', source: '师说' },
  { text: '闻道有先后，术业有专攻。', author: '韩愈', source: '师说' },
  { text: '纸上得来终觉浅，绝知此事要躬行。', author: '陆游', source: '冬夜读书示子聿' },
  { text: '问渠那得清如许？为有源头活水来。', author: '朱熹', source: '观书有感·其一' },
  { text: '旧书不厌百回读，熟读深思子自知。', author: '苏轼', source: '送安惇秀才失解西归' },
  { text: '博观而约取，厚积而薄发。', author: '苏轼', source: '稼说送张琥' },
  { text: '书卷多情似故人，晨昏忧乐每相亲。', author: '于谦', source: '观书' },
  { text: '读书切戒在慌忙，涵泳工夫兴味长。', author: '陆九渊', source: '读书' },
  { text: '读书破万卷，下笔如有神。', author: '杜甫', source: '奉赠韦左丞丈二十二韵' },
  { text: '操千曲而后晓声，观千剑而后识器。', author: '刘勰', source: '文心雕龙·知音' },
  { text: '非学无以广才，非志无以成学。', author: '诸葛亮', source: '诫子书' },
  { text: '千淘万漉虽辛苦，吹尽狂沙始到金。', author: '刘禹锡', source: '浪淘沙九首·其八' },
  { text: '欲穷千里目，更上一层楼。', author: '王之涣', source: '登鹳雀楼' }
]

const DAY_MS = 24 * 60 * 60 * 1000
// 北京时间为 UTC+8；将每天 06:00 作为新一天的起点，不受浏览器时区影响。
const QUOTE_DAY_OFFSET_MS = (8 - 6) * 60 * 60 * 1000

const getQuoteDay = (timestamp: number) => Math.floor((timestamp + QUOTE_DAY_OFFSET_MS) / DAY_MS)

export const getDailyQuote = (timestamp = Date.now()): LearningQuote => {
  const index = ((getQuoteDay(timestamp) % LEARNING_QUOTES.length) + LEARNING_QUOTES.length) % LEARNING_QUOTES.length
  return LEARNING_QUOTES[index]
}

export const getNextQuoteUpdateAt = (timestamp = Date.now()): number => {
  return (getQuoteDay(timestamp) + 1) * DAY_MS - QUOTE_DAY_OFFSET_MS
}
