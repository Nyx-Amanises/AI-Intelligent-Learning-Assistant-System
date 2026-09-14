# 首页摄影素材

摄影取代旧首页的书本插画，并为桌面和手机分别准备裁切。资源随前端部署，页面不会向图片网站发起请求。

| 文件 | 内容 | 来源 |
| --- | --- | --- |
| `quiet-lake.webp` | 群山、湖面与小舟，桌面横幅 2200 × 1250 | [Unsplash 原图](https://images.unsplash.com/photo-1501785888041-af3ef285b470) |
| `quiet-lake-mobile.webp` | 同一照片的手机裁切，900 × 1360 | 同上 |
| `forest-light.webp` | 林间山色，笔记入口配图，800 × 980 | [Unsplash 原图](https://images.unsplash.com/photo-1473448912268-2022ce9509d8) |

图片取自 Unsplash，使用条款见 [Unsplash License](https://unsplash.com/license)。只做尺寸、裁切与 WebP 编码处理，未使用参考 App 截图作为页面素材。

首页主图使用 `picture` 选择手机版本、显式尺寸和较高加载优先级；下方照片延迟加载。图片文字均为独立 HTML，适应屏幕大小，也能被辅助技术读取。
