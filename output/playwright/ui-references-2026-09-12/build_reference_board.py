from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps


ROOT = Path(__file__).resolve().parent
WIDTH, HEIGHT = 1800, 1500
BACKGROUND = '#F6F5F1'
INK = '#202B26'
MUTED = '#66726B'
GREEN = '#237454'


def font(size, bold=False):
    path = 'C:/Windows/Fonts/msyhbd.ttc' if bold else 'C:/Windows/Fonts/msyh.ttc'
    return ImageFont.truetype(path, size)


canvas = Image.new('RGB', (WIDTH, HEIGHT), BACKGROUND)
draw = ImageDraw.Draw(canvas)
draw.text((64, 40), 'AI 学习助手 · UI 参考', font=font(52, True), fill=INK)
draw.text((66, 113), '从真实产品中，选出适合你的学习工作台。', font=font(27), fill=MUTED)
draw.rounded_rectangle((1425, 58, 1736, 109), radius=25, fill='#E5EDE5')
draw.text((1451, 68), '官网公开产品演示', font=font(23), fill=GREEN)

cards = [
    {
        'name': 'Notion', 'style': '清爽知识工作台', 'letter': 'A',
        'file': 'notion-product.png', 'crop': (230, 126, 1220, 640),
        'takeaway': '资料分组 · 轻侧栏 · 清晰的内容层次',
        'source': 'notion.com/product', 'color': '#2A6C52', 'bg': '#FFFFFF',
    },
    {
        'name': 'Gemini Notebook', 'style': '资料驱动的 AI 学习', 'letter': 'B',
        'file': 'notebook-product.png', 'crop': (534, 146, 1271, 560),
        'takeaway': '学习指南 · 成果入口 · 回答引用来源',
        'source': 'notebook.google  /  原 NotebookLM', 'color': '#6552A6', 'bg': '#080A08',
    },
    {
        'name': 'Linear', 'style': '精致专业工具', 'letter': 'C',
        'file': 'linear-product.png', 'crop': (53, 76, 1388, 753),
        'takeaway': '细边框 · 状态标签 · 紧凑的任务界面',
        'source': 'linear.app', 'color': '#55566C', 'bg': '#111212',
    },
    {
        'name': 'Brilliant', 'style': '轻快的学习体验', 'letter': 'D',
        'file': 'brilliant-clean.png', 'crop': (75, 140, 1360, 802),
        'takeaway': '互动练习 · 清晰反馈 · 学习进度',
        'source': 'brilliant.org', 'color': '#287D49', 'bg': '#F5F4F1',
    },
]

for index, card in enumerate(cards):
    x = 64 + (index % 2) * 852
    y = 195 + (index // 2) * 603
    width, height = 820, 573
    draw.rounded_rectangle((x, y, x + width, y + height), radius=22, fill='#FFFFFF', outline='#E3E6DF', width=2)
    draw.rounded_rectangle((x + 24, y + 23, x + 66, y + 65), radius=11, fill=card['color'])
    draw.text((x + 35, y + 27), card['letter'], font=font(24, True), fill='#FFFFFF')
    draw.text((x + 82, y + 22), card['name'], font=font(29, True), fill=INK)
    style_width = draw.textlength(card['style'], font=font(23))
    draw.text((x + width - 25 - style_width, y + 28), card['style'], font=font(23), fill=MUTED)

    screenshot = Image.open(ROOT / card['file']).convert('RGB').crop(card['crop'])
    # Preserve the screenshot's proportions; all edits are cropping and resizing.
    screenshot = ImageOps.contain(screenshot, (772, 390), Image.Resampling.LANCZOS)
    panel = Image.new('RGB', (772, 390), card['bg'])
    panel.paste(screenshot, ((772 - screenshot.width) // 2, (390 - screenshot.height) // 2))
    mask = Image.new('L', (772, 390), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 771, 389), radius=12, fill=255)
    canvas.paste(panel, (x + 24, y + 89), mask)
    draw.text((x + 26, y + 493), card['takeaway'], font=font(24, True), fill=INK)
    draw.text((x + 26, y + 534), card['source'], font=font(18), fill=MUTED)

draw.line((65, 1411, 1735, 1411), fill='#DDE2DA', width=2)
draw.text((66, 1436), '推荐组合：A 的信息组织 + B 的学习流程；练习页借鉴 D 的反馈。', font=font(25, True), fill=GREEN)
draw.text((65, 1475), '2026.09.12 实际访问 · 官网演示截图局部裁切 · 不是本项目改版效果图', font=font(16), fill=MUTED)
canvas.save(ROOT / 'ui-reference-board.png', optimize=True)
print(ROOT / 'ui-reference-board.png')
