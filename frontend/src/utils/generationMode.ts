/** 生成时保存的来源；缺失的历史记录必须视为 UNKNOWN，不能用当前 AI 配置推断。 */
export type GenerationMode = 'MOCK' | 'LIVE' | 'UNKNOWN'
