const DARK_BACKGROUND = '#243746'
const BLACK_BACKGROUND = '#000000'
const MIN_CONTRAST = 4.5

function luminance(color: string): number {
  const channels = [1, 3, 5].map((start) => {
    const value = parseInt(color.slice(start, start + 2), 16) / 255
    return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
  })
  return channels[0]! * 0.2126 + channels[1]! * 0.7152 + channels[2]! * 0.0722
}

const darkLuminance = luminance(DARK_BACKGROUND)

/** Keep source text colours readable when their original PDF backdrop is absent. */
export function getPdfReadingColorStyle(color?: string): { color?: string; backgroundColor?: string } {
  if (typeof color !== 'string' || !/^#[0-9a-f]{6}$/i.test(color)) return {}
  const textLuminance = luminance(color)
  if (1.05 / (textLuminance + 0.05) >= MIN_CONTRAST) return { color }
  const backgroundColor = (textLuminance + 0.05) / (darkLuminance + 0.05) >= MIN_CONTRAST
    ? DARK_BACKGROUND
    : BLACK_BACKGROUND
  return { color, backgroundColor }
}
