# 大学学习资源与教材书目

## 定位与范围

当前在架资源为 **33 项**：**16 本已托管的完整开放教材**，以及 **17 项原有官方资料入口**。用户已撤销上一批 1000 本只有书目信息的教材，V26 将其精准下架；历史迁移保留以兼容已运行数据库。下文原有的 1000 本目录描述仅供追溯。

## 当前可直接阅读的完整教材

| 教材 | 作者 / 发布方 | 完整页数 | 正文许可 |
| --- | --- | ---: | --- |
| Hello 算法（Python 版） | 靳宇栋及贡献者 / Hello 算法开源项目 | 348 | CC BY-NC-SA 4.0 |
| Python 沉思录（Think Python 第 2 版简体译本） | Allen Downey、杜文斌译 / Green Tea Press（原版） | 210 | CC BY-NC 3.0 |
| Linear Algebra 第 4 版 | Jim Hefferon / 作者开放出版 | 525 | CC BY-SA 3.0 US |
| Think Stats 第 2 版 | Allen B. Downey / Green Tea Press | 264 | CC BY-NC-SA 4.0 |

以上首批 4 本共 1347 页，来源、版本、文件 SHA-256、正文许可依据见 `db/catalog/github-open-books.json`，保持原文件和 V28 不变。

第二批新增 12 本完整教材共 7295 页，清单见 `db/catalog/github-foundation-books.json`。这些书均为英文原版，以中文学科和书名索引；未将中文章节译稿冒充全本。全部 16 本合计 8642 页，覆盖高数、线代、概率统计、大学物理、英语写作、计算机、经济学和心理学。

| 新增教材 | 作者 / 发布方 | 页数 | 正文许可 |
| --- | --- | ---: | --- |
| CLP 微积分 1：微分学 | Joel Feldman、Andrew Rechnitzer、Elyse Yeager / UBC | 426 | CC BY-NC-SA 4.0 |
| CLP 微积分 2：积分学 | 同上 / UBC | 468 | CC BY-NC-SA 4.0 |
| CLP 微积分 3：多元微积分 | 同上 / UBC | 397 | CC BY-NC-SA 4.0 |
| CLP 微积分 4：向量微积分 | 同上 / UBC | 305 | CC BY-NC-SA 4.0 |
| Interactive Linear Algebra | Dan Margalit、Joseph Rabinoff / 作者开放出版 | 455 | GFDL 1.2 或后续版本 |
| IPSUR：使用 R 学习概率与统计 | G. Jay Kerns / 作者开放出版 | 384 | GFDL 1.3 或后续版本 |
| 大学物理，第 1 卷 | Samuel J. Ling、Jeff Sanny、William Moebs / OpenStax | 959 | CC BY-NC-SA 4.0 |
| 大学物理，第 2 卷 | 同上 / OpenStax | 781 | CC BY-NC-SA 4.0 |
| 大学物理，第 3 卷 | 同上 / OpenStax | 597 | CC BY-NC-SA 4.0 |
| Writing Guide with Handbook | Michelle Bachelor Robinson、Maria Jerskey、Toby Fulwiler / OpenStax | 773 | CC BY-NC-SA 4.0 |
| Psychology 2e | Rose M. Spielman、William J. Jenkins、Marilyn D. Lovett / OpenStax | 755 | CC BY-NC-SA 4.0 |
| Principles of Economics 3e | David Shapiro、Daniel MacDonald、Steven A. Greenlaw / OpenStax | 995 | CC BY-NC-SA 4.0 |

从 GitHub 核对书籍源码、作者和许可后，完整文件只取项目发布的 PDF 或原作者、出版社官网。OpenStax 的 2026 年文件以当前 PDF 版权页的 CC BY-NC-SA 4.0 为准，不套用旧版 CC BY 标记；每个阅读页面固定保留“Access for free at openstax.org.”链接。书封、商标不在其正文 CC 许可内，因此这些书的列表使用本站学科默认图；原始 PDF 内封面保持原样。数学 6 本使用已核验许可的原书封面，GFDL 教材同时保留完整许可附录及可编辑源码链接。

新书首次打开默认原版以完整显示封面、公式和图表，用户切换后按每本书记住阅读模式。CLP 的部分数学字体在 PDF.js 和 PyMuPDF 中都有错误文字映射，公式必须以原版为准，完整导入文本不意味着公式转换无误。重排时对依赖深色底图的浅色文字保留其原色并补充深色底，确保标题及图注可读。

V29 只新增这批教材，按本站文件路径和来源加书名去重；同一 GitHub 仓库的多卷教材可分别入库，已有资料、收藏和管理员修改保留。完整托管教材的正文上限为 500 万字符，其他资料保持原有 100 万上限，不截断长书。使用 `python db/catalog/build_open_books.py --foundation --check` 校验新批次 SQL、原文件和全文哈希；使用 `python db/catalog/build_pdf_reading_pages.py --manifest db/catalog/github-foundation-books.json --check` 校验颜色和重排数据。大于 100 MB 的化学、生物候选本轮未收录，未用节选或自行压缩文件代替完整原书。

原 PDF、从 PDF 首页取得的真实封面、完整提取文本、署名说明分别存于 `frontend/public/open-books/<slug>/`。原 PDF 未修改，保留原有页次、署名和许可声明（如有），封面仅作缩略，文字提取可能改变排版与公式表现。每本的独立许可见 `ATTRIBUTION.md`，不能用应用代码许可证覆盖这些第三方内容。

V27 添加本站 PDF 路径及页数；V28 导入完整正文。运行 `python db/catalog/build_open_books.py --check` 可离线验证文件哈希、正文哈希、许可、长度、署名文件及生成的 SQL 是否一致。新迁移按来源 URL 幂等插入，不覆盖管理员修改。`INTERNAL` 表示本站托管方式，实际 `license_code` 保留真实 CC 许可，并不声称是本站原创。

用户列表优先显示已托管的书。点击卡片直接打开 PDF，支持目录、跳页、放大、本页文字和记住阅读位置；作者、出版方和许可可在信息按钮中查看。“加入我的资料”将完整提取文本和来源快照写入用户私有资料，并复用现有解析、总结与练习流程；重复导入复用已有资料。PDF 原文件继续在资源阅读器中阅读，私有资料为可检索的 TEXT，并未伪装成用户上传的 PDF。

阅读界面在小屏默认采用 18px 大字重排，保留段落、标题和代码块。A− / A+ 按 2px 调整，范围 14–26px；底部以小字显示当前页码占总页数的比例，并不表示已完成学习。图表、公式及复杂排版可在“原版”查看。原版按包含图形的整页像素范围安全裁白边，以至少 2 倍像素密度渲染（受画布内存上限约束），桌面阅读面板可扩展至 1100px。阅读时仅正文区域滚动，退出后恢复页面滚动；阅读模式偏好和页码保存在本地。

`reading/` 是从同目录原 PDF 生成的阅读排版数据，每 20 页一个 JSON，按需加载。数据保留原文字的 RGB、粗体、斜体及等宽字体属性，不以自动语法高亮猜测颜色。《Hello 算法》上述固定 SHA-256 版本的 JetBrainsMono-Regular 存在错误的连字文字映射；生成器仅依据已核验的字形编号及字符坐标修正，例如将错误提取的 `^^=` 还原为 `===`，保留真实的 `^` 和 `^=`。原 PDF、`body.txt` 及历史迁移保持原样。生成数据沿用各书独立许可和署名，文字重排仍可能改变原书的公式与复杂排版。

在根目录使用 PyMuPDF 1.27.2.3 执行 `python db/catalog/build_pdf_reading_pages.py` 可生成这些数据，追加 `--check` 可验证。生成器核验清单中的原 PDF 哈希；前端检查数据版本、页数、分块哈希与页面尺寸，加载失败时回退为 PDF.js 文字提取。发布时应同时更新 PDF 和对应的 `reading/`，避免不同版本混用。

部署时必须一起发布 `frontend/public/open-books/`，仅执行数据库迁移而遗漏前端静态教材文件将不能显示 PDF。新页面不再展示“教材书目 / 中文 / 查看书目”等卡片文案，改为作者和出版方。

原17项完整资料包括高等数学3项、线性代数3项、概率论与数理统计3项、大学物理5项、大学英语3项，合计7本完整教材（含完整单卷）、6套课程资料、4套复习资料。15项提供已核实的官方完整PDF或课程ZIP入口；2门中文物理课程按官方目录逐讲学习，没有将单讲笔记冒充合订资料包。

列表可按课程、教材/课程/复习和阅读条件筛选，显示来源和适用范围。新书目先展示高等数学、线性代数、概率论与数理统计、大学物理、大学英语，其次计算机，其余按出版年降序和ISBN升序稳定排列；这只是导航顺序，不是教材质量排名。数据库的 `estimated_minutes=0` 表示未估计。短知识卡片已从公开目录撤下，用户此前导入的个人资料保留。

## 原17项完整资料入口

核验日期：2026-11-07。所有链接来自大学、原出版社或考试机构；实际范围依据官方目录、书目前言、课程大纲或完整PDF目录核对，不仅依据网页返回200。

| 课程             | 资源                                                                                                                            | 类别     | 语言     | 获取方式                                                                                                                                                                                                                                                              |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------- | -------- | -------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 高等数学         | [华东师大高数B：整学期习题课汇总](https://math.ecnu.edu.cn/videos/detail.php?id=324)                                            | 复习资料 | 中文     | [完整文件](https://math.ecnu.edu.cn/videos/files/2024%E4%B8%8B/%E9%AB%98%E7%AD%89%E6%95%B0%E5%AD%A6B/%E8%AE%B2%E4%B9%89_%E6%B1%87%E6%80%BB.pdf)                                                                                                                       |
| 高等数学         | [MIT 单变量微积分：完整课程与试卷](https://ocw.mit.edu/courses/18-01sc-single-variable-calculus-fall-2010/)                     | 课程资料 | 英文     | [完整文件](https://ocw.mit.edu/courses/18-01sc-single-variable-calculus-fall-2010/18.01sc-fall-2010.zip)                                                                                                                                                              |
| 高等数学         | [微积分 第1卷：OpenStax 完整教材](https://openstax.org/details/books/calculus-volume-1)                                         | 完整教材 | 英文     | [完整文件](https://assets.openstax.org/oscms-prodcms/media/documents/calculus-volume-1_-_WEB.pdf)                                                                                                                                                                     |
| 线性代数         | [华东师大线代A：15讲习题课完整讲义](https://math.ecnu.edu.cn/videos/detail.php?id=182)                                          | 复习资料 | 中文     | [完整文件](https://math.ecnu.edu.cn/videos/files/2024%E4%B8%8B/%E7%BA%BF%E6%80%A7%E4%BB%A3%E6%95%B0A/%E5%B9%B3%E8%A1%8C%E7%8F%AD/2024-2025%E7%A7%8B%E5%AD%A3%E5%AD%A6%E6%9C%9F%E7%BA%BF%E6%80%A7%E4%BB%A3%E6%95%B0A%E4%B9%A0%E9%A2%98%E8%AF%BE%E8%AE%B2%E4%B9%89.pdf) |
| 线性代数         | [MIT 线性代数：考试与期末复习合集](https://ocw.mit.edu/courses/18-06-linear-algebra-spring-2010/pages/exams/)                   | 复习资料 | 英文     | [完整文件](https://ocw.mit.edu/courses/18-06-linear-algebra-spring-2010/18.06-spring-2010.zip)                                                                                                                                                                        |
| 线性代数         | [线性代数：UC Davis 完整教材](https://www.math.ucdavis.edu/~linear/)                                                            | 完整教材 | 英文     | [完整文件](https://www.math.ucdavis.edu/~linear/linear-guest.pdf)                                                                                                                                                                                                     |
| 概率论与数理统计 | [北大概率统计：完整课堂讲义](https://math.pku.edu.cn/teachers/lidf/course/index.htm)                                            | 课程资料 | 中文     | [完整文件](https://math.pku.edu.cn/teachers/lidf/course/probstathsy/probstathsy.pdf)                                                                                                                                                                                  |
| 概率论与数理统计 | [MIT 概率与统计：完整课程与复习资料](https://ocw.mit.edu/courses/18-05-introduction-to-probability-and-statistics-spring-2022/) | 课程资料 | 英文     | [完整文件](https://ocw.mit.edu/courses/18-05-introduction-to-probability-and-statistics-spring-2022/18.05-spring-2022.zip)                                                                                                                                            |
| 概率论与数理统计 | [概率论导论：Dartmouth 完整教材](https://math.dartmouth.edu/~prob/prob/prob.pdf)                                                | 完整教材 | 英文     | [完整文件](https://math.dartmouth.edu/~prob/prob/prob.pdf)                                                                                                                                                                                                            |
| 大学物理         | [普通物理一：中文整学期课程](https://ocw.nthu.edu.tw/courses/96)                                                                | 课程资料 | 繁体中文 | 课程内分讲资料                                                                                                                                                                                                                                                        |
| 大学物理         | [普通物理二：中文整学期课程](https://ocw.nthu.edu.tw/courses/107)                                                               | 课程资料 | 繁体中文 | 课程内分讲资料                                                                                                                                                                                                                                                        |
| 大学物理         | [大学物理 第1卷：力学、振动与波](https://openstax.org/details/books/university-physics-volume-1)                                | 完整教材 | 英文     | [完整文件](https://assets.openstax.org/oscms-prodcms/media/documents/university-physics-volume-1_-_WEB.pdf)                                                                                                                                                           |
| 大学物理         | [大学物理 第2卷：热学、电与磁](https://openstax.org/details/books/university-physics-volume-2)                                  | 完整教材 | 英文     | [完整文件](https://assets.openstax.org/oscms-prodcms/media/documents/university-physics-volume-2_-_WEB.pdf)                                                                                                                                                           |
| 大学物理         | [大学物理 第3卷：光学与近代物理](https://openstax.org/details/books/university-physics-volume-3)                                | 完整教材 | 英文     | [完整文件](https://assets.openstax.org/oscms-prodcms/media/documents/university-physics-volume-3_-_WEB.pdf)                                                                                                                                                           |
| 大学英语         | [大学学术英语读写：完整教材与写作手册](https://openstax.org/details/books/writing-guide)                                        | 完整教材 | 英文     | [完整文件](https://assets.openstax.org/oscms-prodcms/media/documents/WritingGuide-WEB.pdf)                                                                                                                                                                            |
| 大学英语         | [MIT 学术英语写作：完整公开课程包](https://ocw.mit.edu/courses/21g-222-expository-writing-for-bilingual-students-fall-2002/)    | 课程资料 | 英文     | [完整文件](https://ocw.mit.edu/courses/21g-222-expository-writing-for-bilingual-students-fall-2002/21g.222-fall-2002.zip)                                                                                                                                             |
| 大学英语         | [四、六级官方备考：完整大纲与样卷](https://cet.neea.edu.cn/html1/folder/16113/1588-1.htm)                                       | 复习资料 | 中英双语 | [完整文件](https://cet.neea.edu.cn/res/Home/1704/55b02330ac17274664f06d9d3db8249d.pdf)                                                                                                                                                                                |

## 完整范围与版本

“完整教材”指所列版本的整本或完整单卷，并不保证一本覆盖中国高校该课程的全部教学大纲。“课程资料”指所列学期的整门课程或其完整公开资料包；MIT下载包不包含未公开的商业教材及全部音视频。

四、六级材料明确用于CET备考，不作为所有高校大学英语期末的通用复习包。该2016修订版仍由考试院考试大纲页面提供，215页PDF包含完整样卷、词表和评分样卷，本目录不承诺配套听力音频。学术英语读写教材和MIT写作课程也不标成四、六级专用教材。

### 华东师大高数B：整学期习题课汇总

42页整学期习题课讲义：函数、极限、导数、积分与无穷级数。2024—2025秋季高数B课程，覆盖一个学期。

作者：徐畅。来源：华东师范大学数学科学学院。

官方公开资料，阅读及下载按原站条款。未登记全文再分发授权，本站仅提供官方链接。

### MIT 单变量微积分：完整课程与试卷

5大单元、102节学习安排，配套4次阶段考试及期末试卷解答。整课ZIP含公开网页与PDF，不含音视频。

作者：David Jerison；课程内容由 MIT OCW 教学团队共同制作。来源：MIT OpenCourseWare。

原站标注 CC BY-NC-SA 4.0，须署名、非商业使用并按相同方式共享；第三方内容另看原站说明。本站提供官方链接，不镜像正文。

### 微积分 第1卷：OpenStax 完整教材

完整第1卷：函数与图像、极限、导数、积分及应用。属于三卷系列的第一册，可在线阅读或获取整本PDF。

作者：Gilbert Strang；Edwin “Jed” Herman。来源：OpenStax（Rice University）。

原站标注 CC BY-NC-SA 4.0，须署名、非商业使用并按相同方式共享；第三方内容另看原站说明。本站提供官方链接，不镜像正文。 [原站许可说明](https://creativecommons.org/licenses/by-nc-sa/4.0/)。

### 华东师大线代A：15讲习题课完整讲义

88页、完整15讲：线性方程组、向量空间、矩阵、行列式、特征值、正交与二次型，附习题讲解。

作者：杨墩坤。来源：华东师范大学数学科学学院。

官方公开资料，阅读及下载按原站条款。未登记全文再分发授权，本站仅提供官方链接。

### MIT 线性代数：考试与期末复习合集

2010年3次阶段考试与期末试卷，均含解答；页面另有历年试卷档案。ZIP为2010整课包，不是全部历年试卷合订。

作者：Gilbert Strang（2010春季）；历年教师见各学期页面。来源：MIT OpenCourseWare / MIT Mathematics。

原站标注 CC BY-NC-SA 4.0，须署名、非商业使用并按相同方式共享；第三方内容另看原站说明。本站提供官方链接，不镜像正文。

### 线性代数：UC Davis 完整教材

436页、17章：线性方程组、向量空间、矩阵、特征值、正交、最小二乘与奇异值，附样卷。

作者：David Cherney；Tom Denton；Rohit Thomas；Andrew Waldron。来源：University of California, Davis, Department of Mathematics。

原站标注 CC BY-NC-SA 3.0 Unported，须署名、非商业使用并按相同方式共享；第三方内容另看原站说明。本站提供官方链接，不镜像正文。 [原站许可说明](https://creativecommons.org/licenses/by-nc-sa/3.0/)。

### 北大概率统计：完整课堂讲义

261页中文讲义：概率空间、随机变量、随机向量、期望方差、极限定理、描述统计和参数估计。2016秋季课程。

作者：李东风（课件制作）；对应教材作者为何书元。来源：北京大学数学科学学院 · 李东风课程主页。

官方公开资料，阅读及下载按原站条款。未登记全文再分发授权，本站仅提供官方链接。

### MIT 概率与统计：完整课程与复习资料

概率与统计整门课程：条件概率、分布、贝叶斯推断、检验、置信区间、回归。附复习题、模拟期末、正式期末与解答。

作者：Jeremy Orloff；Jennifer French Kamrin。来源：MIT OpenCourseWare。

原站标注 CC BY-NC-SA 4.0，须署名、非商业使用并按相同方式共享；第三方内容另看原站说明。本站提供官方链接，不镜像正文。

### 概率论导论：Dartmouth 完整教材

518页、12章：离散与连续概率、极限定理、马尔可夫链、随机游走。2006版完整概率论教材，不含完整统计推断课程。

作者：Charles M. Grinstead；J. Laurie Snell；此2006版本由Peter G. Doyle整理。来源：Dartmouth College Mathematics / The CHANCE Project。

原文件声明 GNU Free Documentation License；具体版本及使用条件请查阅原书。本站仅提供官方完整PDF链接。

### 普通物理一：中文整学期课程

30讲、完整18周课程：力学、振动、波动与热学，配套分讲手写笔记。2013秋季；课程可逐讲阅读学习。

作者：林秀豪 教授。来源：清华大学（新竹）开放式课程。

原站声明仅供学术使用并限制商业使用；视频与讲义的具体使用遵守原站条款。本站提供官方链接。

### 普通物理二：中文整学期课程

32讲、完整18周课程：电磁学、光学、量子与近代物理，配套分讲笔记。2014春季。

作者：林秀豪 教授。来源：清华大学（新竹）开放式课程。

原站声明仅供学术使用并限制商业使用；视频与讲义的具体使用遵守原站条款。本站提供官方链接。

### 大学物理 第1卷：力学、振动与波

完整第1卷、17章：运动学、牛顿定律、能量、动量、转动、引力、流体、振动与波。三卷系列中的第一册。

作者：Samuel J. Ling；Jeff Sanny；William Moebs（主编/主要作者，其他贡献者见前言）。来源：OpenStax · Rice University。

原站标注 CC BY-NC-SA 4.0，须署名、非商业使用并按相同方式共享；第三方内容另看原站说明。本站提供官方链接，不镜像正文。 [原站许可说明](https://creativecommons.org/licenses/by-nc-sa/4.0/)。

### 大学物理 第2卷：热学、电与磁

完整第2卷、16章：热力学、电场、电势、电路、磁场、电磁感应与电磁波。三卷系列中的第二册。

作者：Samuel J. Ling；Jeff Sanny；William Moebs（主编/主要作者，其他贡献者见前言）。来源：OpenStax · Rice University。

原站标注 CC BY-NC-SA 4.0，须署名、非商业使用并按相同方式共享；第三方内容另看原站说明。本站提供官方链接，不镜像正文。 [原站许可说明](https://creativecommons.org/licenses/by-nc-sa/4.0/)。

### 大学物理 第3卷：光学与近代物理

完整第3卷、11章：光学、相对论、量子、原子、凝聚态、核与粒子物理。三卷系列中的第三册。

作者：Samuel J. Ling；Jeff Sanny；William Moebs（主编/主要作者，其他贡献者见前言）。来源：OpenStax · Rice University。

原站标注 CC BY-NC-SA 4.0，须署名、非商业使用并按相同方式共享；第三方内容另看原站说明。本站提供官方链接，不镜像正文。 [原站许可说明](https://creativecommons.org/licenses/by-nc-sa/4.0/)。

### 大学学术英语读写：完整教材与写作手册

完整 Writing Guide with Handbook：大学学术阅读、论证、研究写作、语法与引文手册。适合学术英语，不是四、六级专用教材。

作者：Michelle Bachelor Robinson；Maria Jerskey；featuring Toby Fulwiler。来源：OpenStax · Rice University。

原站标注 CC BY-NC-SA 4.0，须署名、非商业使用并按相同方式共享；第三方内容另看原站说明。本站提供官方链接，不镜像正文。 [原站许可说明](https://creativecommons.org/licenses/by-nc-sa/4.0/)。

### MIT 学术英语写作：完整公开课程包

本科双语学生学术英语写作课程：阅读、词汇、文章、书评和立场论文。ZIP包含该在线版全部公开资料，不含指定商业教材。

作者：Patricia Brennecke。来源：MIT OpenCourseWare。

原站标注 CC BY-NC-SA 4.0，须署名、非商业使用并按相同方式共享；第三方内容另看原站说明。本站提供官方链接，不镜像正文。 [原站许可说明](https://creativecommons.org/licenses/by-nc-sa/4.0/)。

### 四、六级官方备考：完整大纲与样卷

215页完整PDF：四、六级大纲、词表、笔试/口试样卷、答题卡与评分样卷。2016修订版；不含听力音频，非各校期末通用资料。

作者：全国大学英语四、六级考试委员会（考试大纲制定机构）；教育部教育考试院官网公开发布。来源：中国教育考试网 / 教育部教育考试院。

官方公开资料，阅读及下载按原站条款。未登记全文再分发授权，本站仅提供官方链接。

## 新增1000项国内本科教材书目

本批数据以2026-11-07公开目录为依据，正式快照位于 [`db/catalog/domestic-textbooks-1000.json`](../db/catalog/domestic-textbooks-1000.json)，顶层 `records` 为1000条书目。快照仅保留书名、作者、ISBN、出版社、官方详情与封面URL、教育层次、出版日期/年份和来源溯源；不复制原站简介、目录正文、教材全文或下载文件。

| 出版社         | 新增数量 | 官方来源与范围                                                                                                                                                                                                                  | 阅读条件                                                          |
| -------------- | -------: | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| 机械工业出版社 |      980 | [教材服务网“本科教材”目录](https://www.cmpedu.com/books/list.htm?SN=JC)，官方类别 `JC`；当时目录显示7939条。候选池读取84页、1000条，再按顺序选取980条。                                                                         | 官方书目和购买/服务入口；未验证免费完整全文。                     |
| 高等教育出版社 |       20 | [普通高等教育教材网](https://www.hep.com.cn/mgcjiaocai)及其[公开书目API](https://www.hep.com.cn/api/v1/books/ysBook?isYanshi=yes&limit=15&page=1)；该集合当时125条，采样4页60条，仅选官方分类明确包含“高等教育本科教材”的20条。 | 官方书目，部分原站标有试读；`hasFreeChapter` 不等于整本免费开放。 |

两家来源分别显示本科分类，而不是按出版社名称推断所有图书都是大学教材。高教社样本补充法学、教育、大学文化素质、管理、艺术、历史和哲学等内容。原目录中ISBN `9787040582710` 同时对应“民法学（第二版）上、下册”和“民法学（第二版）下册”，因范围歧义已排除，不把共用ISBN的拆册重复收录。

所有1000条均完成ISBN-13校验位检查，以及ISBN、官方详情URL、“书名＋作者”三项去重；后者统一全半角、大小写和排版空白。先选高教社20条，再按机械工业出版社原目录顺序补足980条，排序只改变展示顺序。不同作者或不同版本的同名教材可保留。快照记录 `sourceId`、实际列表/API地址 `metadataUrl`、核验日期 `observedAt`、原有出版日期与年份；未知值留空，未从印次推断出版版次或年份。

### 分类与默认值

高教社20条沿用官方学科；机械工业出版社980条仅根据书名中明确的课程词生成 `subject` 导航标签，例如高等数学、线性代数、概率论与数理统计、大学物理、大学英语、计算机、数学、经济管理和工程技术。无法明确归类的书目使用“大学教材”。每条的 `subjectOrigin` 记录标签来自官方分类还是标题编辑规则，规则本身也保存在JSON中。这些标签不代表已核查教材大纲或具体知识点。

1000条 `knowledge_points` 均为空，不编造知识点；`summary` 只写“出版社 · 本科教材；ISBN …”这一事实短句。`coverage_note` 明确写明是官方本科书目、不是本站可用全文。`difficulty_level=2` 是系统兼容默认值，没有做难度评估；`estimated_minutes=0` 表示时长未估计。`language_code` 用于书目展示，992条为中文，8条因标题明确标“英文版”等而记为英文；没有逐本审查正文语言。

### 来源、封面与核验边界

1000条均从官方列表获取书目与对应封面URL，未为缺失封面另配相似教材图，未下载或镜像书封文件。高教社封面采用同一书目记录的 `cover.front.downloadUrl`。机械工业出版社列表原图链接使用HTTP；对同一 `s.cmpedu.com` 主机的3张真实图片完成HTTPS GET及TLS校验后，仅将协议改为HTTPS，原始URL保留在 `coverUrlOriginal`。这不能保证所有图片一直可用；加载失败仍回退为学科插画。

在机械工业出版社1000条候选池中，另抽查5个官方详情页的书名、作者、ISBN、本科面包屑及3张书封响应；最终980条收录了其中4个详情样本和2个书封样本，具体证据保存在正式JSON的 `verification` 中。高教社选中条目核对了API事实字段和明确本科分类。**未逐一打开1000个详情页或逐张验证1000个封面，更没有做1000本教材内容审校。**

访问规则和版权分开记录：高教社 `robots.txt` 的通用禁用项为空，机械工业出版社该地址返回404；两站均保留版权，未发现授予教材正文或书封再分发的明确许可。公开目录可访问、提供试读或存在ISBN，不等于取得商业转载授权。本站只保存事实元数据和官方URL，`LINK_ONLY` 是访问方式，不是重新授权；对外阅读按原站条款。试读不能改标为免费全文，书目不能提供全文导入。

清华社公开目录/API和下载书目仅用于有限来源调查，因本批已有足量数据，没有并入正式1000条。北大社首次 `robots.txt` 请求即返回403，已停止，未登录、换入口或绕过限制。

## 来源与使用方式

- 原17项均为 EXTERNAL / LINK_ONLY，教学正文 content_text 为 NULL，保留原有官方完整资料入口；新增1000项另加 `CATALOG_ONLY`，`content_text` 和 `download_url` 均为 NULL。LINK_ONLY 是本站的访问方式，不是对原资料许可证的替代或重新授权。
- 书籍和资料使用已核实的原作者/机构署名，用户通过原站阅读或获取完整文件；本站没有抓取、镜像或全文导入这些外部教材。
- MIT、当前OpenStax书目及UC Davis教材包含非商业、署名和相同方式共享等条件，不能仅因可免费访问就标为任意商用。Dartmouth原书声明FDL，但本次未核定全部版本条件，因此同样只提供官方链接。
- 华东师大、北大、清华（新竹）和考试院公开资料也保留原站使用条件。存在来源与范围记录不等于已取得商业转载许可。
- 本次核验是目录与获取入口检查，不是完整的教学审校；未编造独立人工审核人，新增记录 reviewed_by / reviewed_at 为 NULL。

## 真实封面与来源

2026-11-07核对原17项资料，3本教材使用确切书名、卷号和ISBN一致的官方印刷版封面；其余14项使用本站的学科SVG插画占位。新增1000项的官方封面来源和抽查边界见上一节。插画不是原书封面。资料列表优先加载 `coverUrl`，图片缺失、地址不支持或加载失败时显示插画；加载前后占位尺寸一致，默认72×96像素，也可通过 `--resource-cover-width` 等比调整。完整显示图片，不裁切书名或卷号。外链图片请求使用 `referrerpolicy="no-referrer"`。

以下三个封面来自 OpenStax 官方书目直接链接的 Kendall Hunt 印刷版页面。逐项对照 OpenStax 书目API中的 `amazon_link`、书名、`print_softcover_isbn_13`、`print_isbn_13` 及原PDF入口，再核对出版社页面的 Book 元数据和可见书封。三个图片URL均返回200、`image/jpeg`，实际图片125×161像素，已目视确认书名与卷号。记录的是同一教材的官方印刷版封面，不声称图片是从数字PDF截取。

| 对应资源                                                                         | 官方元数据                                                         | 封面来源页面                                                                                                         | 原站封面图片                                                                                 | 平装ISBN      |
| -------------------------------------------------------------------------------- | ------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- | ------------- |
| [微积分 第1卷](https://openstax.org/details/books/calculus-volume-1)             | [OpenStax书目74](https://openstax.org/apps/cms/api/v2/pages/74/)   | [Kendall Hunt: Calculus Volume 1](https://he.kendallhunt.com/product/openstax-calculus-volume-1)                     | [官方JPG](https://he.kendallhunt.com/sites/default/files/product_images_small/1501419_0.jpg) | 9781506698069 |
| [大学物理 第3卷](https://openstax.org/details/books/university-physics-volume-3) | [OpenStax书目95](https://openstax.org/apps/cms/api/v2/pages/95/)   | [Kendall Hunt: University Physics Volume 3](https://he.kendallhunt.com/product/openstax-university-physics-volume-3) | [官方JPG](https://he.kendallhunt.com/sites/default/files/product_images_small/1501697.jpg)   | 9781506698250 |
| [Writing Guide with Handbook](https://openstax.org/details/books/writing-guide)  | [OpenStax书目555](https://openstax.org/apps/cms/api/v2/pages/555/) | [Kendall Hunt: Writing Guide with Handbook](https://he.kendallhunt.com/product/openstax-writing-guide-handbook)      | [官方JPG](https://he.kendallhunt.com/sites/default/files/product_images_small/1501337.jpg)   | 9781711494869 |

封面来源署名：**OpenStax / Kendall Hunt Publishing**；教材作者仍按上文各书目署名。页面未提供可单独核实的封面设计者署名，本站不编造设计作者。本站仅记录和引用原站图片URL，没有复制或镜像图片文件；没有查到独立封面作品的明确再分发许可，不将整本教材的许可或图片可访问性解释为封面获准任意使用。是否允许商业转载仍须按原站及权利人的条件另行核实。

未配置真实封面的14项记录如下：

- **大学物理第1、2卷**：OpenStax所链接的官方印刷版页面书名和ISBN相符，但现有书封分别写有 `Part 1 of 2`、`Part 2 of 2`。没有核清印刷拆册与本站完整数字卷之间的对应关系，因此不使用这两张图，也不互换卷号。
- **UC Davis Linear Algebra**：已核查作者的[官方教材页](https://www.math.ucdavis.edu/~linear/)，该页提供整书PDF和LaTeX源码，唯一图片为许可标识，没有可确认的独立书封图片。许可标识不充当书封。
- **Dartmouth概率论导论2006版**：已核查[原PDF所在的官方目录](https://math.dartmouth.edu/~prob/prob/)，未找到同版独立书封。此书作者为Grinstead与Snell，不使用Blitzstein等其他作者的概率教材封面。
- **10项课程/复习资料**：华东师大高数B、MIT单变量微积分、华东师大线代A、MIT线代试卷合集、北大概率统计、MIT概率统计、清华（新竹）普通物理一/二、MIT学术英语写作及四/六级大纲样卷，均不强配其课程引用教材的封面。

OpenStax CMS的 `cover_url` 字段当前提供方形书目标题卡；本站本次没有将这些标题卡当作完整印刷书封。后续新增封面必须逐项核对原书版本，不按学科相近、文件名相似或搜索结果排序自动配图。

## 迁移与接口

保留V18/V19历史迁移。V20增加 resource_kind、language_code、coverage_note、download_url，并定向下架V19的30份短卡片，保留用户资料和收藏/导入关联。V21更新原有MIT微积分、OpenStax第一卷入口并新增15项完整资料，不按本地预览ID写数据库。已经迁移的环境按Flyway顺序升级，不手工重跑种子SQL。

V22增加可选 `cover_url` 字段，接口对应可选 `coverUrl`。V23仅按上述3条准确的 `source_url` 为完整外链教材填入已核实封面URL；已有管理员封面不被覆盖，课程资料、用户资料以及历史迁移不被修改。缺少封面不影响阅读、下载、收藏或练习入口。

V24增加 `access_mode`、`isbn`、`publisher`、`education_level`、`publication_year`，原有记录保留默认 `OPEN_RESOURCE`。V25由正式JSON生成1000条 `INSERT ... SELECT ... WHERE NOT EXISTS`；如果相同 `source_url` **或** ISBN 已存在，就跳过该书目，不覆盖已有标题、封面、状态或用户关联。V18—V24保持原样。全新库迁移后的公开种子数为1017；已有同来源/ISBN记录的数据库会保留原记录，所以实际新插入数可能少于1000。

V25新记录均为 `PUBLISHED / EXTERNAL / LINK_ONLY / TEXTBOOK / CATALOG_ONLY`，教育层次为“本科”；`review_note` 统一为“官方目录元数据核验；不是全文/内容审校”，`reviewed_by`、`reviewed_at` 均为空。这里的发布状态仅表示书目已入目录，不表示其全文、教学内容或商业再分发权限已被审核通过。

GET /api/resources/page 支持 keyword、subject、resourceKind、accessMode、bookmarkedOnly、current、size；resourceKind 可选 TEXTBOOK / COURSE_PACK / REVIEW_PACK / KNOWLEDGE_CARD，accessMode 区分 OPEN_RESOURCE / CATALOG_ONLY。ISBN、作者和出版社可用于关键词检索。管理员列表同样支持类别与阅读条件筛选。发布校验要求完整资料填写语言与范围，文件链接必须是公开HTTPS地址；书目模式允许空知识点。外部条目仍由后端禁止全文导入。

从知识掌握进入时继续携带知识点和原资料ID；整课资料在新标签中打开，返回后可通过“继续练习”进入原知识点的出题页。切换其他课程或清除补强条件会清掉旧练习上下文。学习进度不会仅因打开一个外链就被标为已完成。

后续新增完整资料先以DRAFT登记到新的迁移中，核验课程范围、原作者、版本、官方来源和完整文件后再发布。新增书目按书目模式核对ISBN、作者、官方本科来源及阅读条件，不将缺少全文的书目标成完整开放资料。不要用单个章节PDF、失效下载地址、网盘转载或课程引用书目代替完整教材。

## 本地生成与复现

[`db/catalog/build_domestic_catalog.py`](../db/catalog/build_domestic_catalog.py) 仅使用Python标准库，读取审核后的JSON，不访问网络、不连接数据库。它校验数量、20/980出版社分布、官方来源、教育层次、字段长度、ISBN及三项去重，再生成V25；SQL头部记录输入JSON的SHA-256。

在仓库根目录执行：

```powershell
python db/catalog/build_domestic_catalog.py
python db/catalog/build_domestic_catalog.py --check
```

`--check` 不写文件，要求保存的SQL与JSON重新生成的结果完全一致。将来更新正式JSON，应先复核事实和阅读条件，再重新生成尚未部署的迁移；已部署环境需要新增迁移，不能修改历史版本来绕过Flyway校验。

## 验证

后端在 backend 目录使用兼容JDK运行：

```powershell
mvn "-Dtest=StudyResourceServiceTest,StudyResourceMapperTest,StudyResourceDatabaseTest,StudyResourceControllerTest,StudyResourceMigrationTest,StudyResourceCatalogMigrationTest" test
```

相关测试覆盖连续迁移、类别与阅读条件筛选、语言/范围/文件链接校验、权限、来源快照、事务与重复导入。新增目录迁移测试检查1000条书目、出版社分布、ISBN/URL唯一性、现有记录保留、重复执行幂等及分页末页。数据库测试使用隔离的H2 MySQL模式，不连接真实开发或生产MySQL。

前端使用 npm run build 和定向 ESLint 验证。V29 在本地隔离 MySQL 演示库运行，真实 API 返回 33 项资源、16 本完整教材；新增教材进行了手机端搜索、目录、原版/大字、页码和来源展示检查，并实际导入了完整 CLP 积分学教材到个人资料。后端资源服务及目录迁移测试共 136 项通过。AI 服务仍采用演示配置，不将教材阅读和导入检查等同于真实模型的总结或出题质量验证。
