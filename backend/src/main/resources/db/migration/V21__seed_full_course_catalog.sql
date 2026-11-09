-- 完整大学课程资源目录：17项公开官方资源，覆盖五门公共基础课。
-- 更新V19已有的2个官方入口，新增15项；不复制正文，不修改个人资料。
-- estimated_minutes=0表示未估计；所有文件链接均来自官方完整PDF或课程ZIP。

INSERT INTO study_resource (title, summary, subject, knowledge_points, difficulty_level, estimated_minutes, resource_type, resource_kind, language_code, coverage_note, download_url, source_name, source_url, author, license_code, license_url, license_note, content_text, status, review_note, reviewed_by, reviewed_at)
VALUES ('华东师大高数B：整学期习题课汇总', '中文高数一学期习题课汇总，涵盖从函数、极限到积分、无穷级数的七章内容。', '高等数学', '高等数学,函数与极限,微积分,导数,定积分,无穷级数,函数的定义域,函数定义域,定义域,左右极限,极限,连续,积分', 2, 0, 'EXTERNAL', 'REVIEW_PACK', 'zh', '42页整学期习题课讲义：函数、极限、导数、积分与无穷级数。2024—2025秋季高数B课程，覆盖一个学期。', 'https://math.ecnu.edu.cn/videos/files/2024%E4%B8%8B/%E9%AB%98%E7%AD%89%E6%95%B0%E5%AD%A6B/%E8%AE%B2%E4%B9%89_%E6%B1%87%E6%80%BB.pdf', '华东师范大学数学科学学院', 'https://math.ecnu.edu.cn/videos/detail.php?id=324', '徐畅', 'LINK_ONLY', NULL, '官方公开资料，阅读及下载按原站条款。未登记全文再分发授权，本站仅提供官方链接。', NULL, 'PUBLISHED', '2026-11-07核验官方来源、完整范围及文件入口；仅链接导航，未作教学内容审校。', NULL, NULL);

UPDATE study_resource
SET title = 'MIT 单变量微积分：完整课程与试卷',
    summary = '18.01SC Single Variable Calculus, Fall 2010。完整单变量微积分自学课程，附全套讲义、习题与解答、四次阶段考试及期末试卷解答。',
    subject = '高等数学',
    knowledge_points = '高等数学,单变量微积分,导数,积分,泰勒级数,期末复习,函数的定义域,函数定义域,定义域,左右极限,极限,连续',
    difficulty_level = 2,
    estimated_minutes = 0,
    resource_type = 'EXTERNAL',
    resource_kind = 'COURSE_PACK',
    language_code = 'en',
    coverage_note = '5大单元、102节学习安排，配套4次阶段考试及期末试卷解答。整课ZIP含公开网页与PDF，不含音视频。',
    download_url = 'https://ocw.mit.edu/courses/18-01sc-single-variable-calculus-fall-2010/18.01sc-fall-2010.zip',
    source_name = 'MIT OpenCourseWare',
    author = 'David Jerison；课程内容由 MIT OCW 教学团队共同制作',
    license_code = 'LINK_ONLY',
    license_url = NULL,
    license_note = '原站标注 CC BY-NC-SA 4.0，须署名、非商业使用并按相同方式共享；第三方内容另看原站说明。本站提供官方链接，不镜像正文。',
    content_text = NULL
WHERE source_url = 'https://ocw.mit.edu/courses/18-01sc-single-variable-calculus-fall-2010/' AND resource_type = 'EXTERNAL' AND license_code = 'LINK_ONLY';

UPDATE study_resource
SET title = '微积分 第1卷：OpenStax 完整教材',
    summary = 'Calculus Volume 1。官方完整微积分教材第一卷，系统学习函数、极限、导数与积分。',
    subject = '高等数学',
    knowledge_points = '高等数学,微积分教材,函数,极限,导数,积分,函数的定义域,函数定义域,定义域,左右极限,连续',
    difficulty_level = 2,
    estimated_minutes = 0,
    resource_type = 'EXTERNAL',
    resource_kind = 'TEXTBOOK',
    language_code = 'en',
    coverage_note = '完整第1卷：函数与图像、极限、导数、积分及应用。属于三卷系列的第一册，可在线阅读或获取整本PDF。',
    download_url = 'https://assets.openstax.org/oscms-prodcms/media/documents/calculus-volume-1_-_WEB.pdf',
    source_name = 'OpenStax（Rice University）',
    author = 'Gilbert Strang；Edwin “Jed” Herman',
    license_code = 'LINK_ONLY',
    license_url = 'https://creativecommons.org/licenses/by-nc-sa/4.0/',
    license_note = '原站标注 CC BY-NC-SA 4.0，须署名、非商业使用并按相同方式共享；第三方内容另看原站说明。本站提供官方链接，不镜像正文。',
    content_text = NULL
WHERE source_url = 'https://openstax.org/details/books/calculus-volume-1' AND resource_type = 'EXTERNAL' AND license_code = 'LINK_ONLY';

INSERT INTO study_resource (title, summary, subject, knowledge_points, difficulty_level, estimated_minutes, resource_type, resource_kind, language_code, coverage_note, download_url, source_name, source_url, author, license_code, license_url, license_note, content_text, status, review_note, reviewed_by, reviewed_at)
VALUES ('华东师大线代A：15讲习题课完整讲义', '15讲中文线代习题课讲义，含典型题讲解、错题选讲与阶段评价讲解。', '线性代数', '线性代数,线性方程组,矩阵,行列式,向量空间,特征值,二次型,期末复习', 2, 0, 'EXTERNAL', 'REVIEW_PACK', 'zh', '88页、完整15讲：线性方程组、向量空间、矩阵、行列式、特征值、正交与二次型，附习题讲解。', 'https://math.ecnu.edu.cn/videos/files/2024%E4%B8%8B/%E7%BA%BF%E6%80%A7%E4%BB%A3%E6%95%B0A/%E5%B9%B3%E8%A1%8C%E7%8F%AD/2024-2025%E7%A7%8B%E5%AD%A3%E5%AD%A6%E6%9C%9F%E7%BA%BF%E6%80%A7%E4%BB%A3%E6%95%B0A%E4%B9%A0%E9%A2%98%E8%AF%BE%E8%AE%B2%E4%B9%89.pdf', '华东师范大学数学科学学院', 'https://math.ecnu.edu.cn/videos/detail.php?id=182', '杨墩坤', 'LINK_ONLY', NULL, '官方公开资料，阅读及下载按原站条款。未登记全文再分发授权，本站仅提供官方链接。', NULL, 'PUBLISHED', '2026-11-07核验官方来源、完整范围及文件入口；仅链接导航，未作教学内容审校。', NULL, NULL);

INSERT INTO study_resource (title, summary, subject, knowledge_points, difficulty_level, estimated_minutes, resource_type, resource_kind, language_code, coverage_note, download_url, source_name, source_url, author, license_code, license_url, license_note, content_text, status, review_note, reviewed_by, reviewed_at)
VALUES ('MIT 线性代数：考试与期末复习合集', '18.06 Linear Algebra: Exams / Past Semesters of 18.06。MIT线性代数整学期考试及解答，并连接多年课程试卷与习题档案。', '线性代数', '线性代数,线性方程组,向量空间,矩阵,特征值,正交性,期末试卷', 2, 0, 'EXTERNAL', 'REVIEW_PACK', 'en', '2010年3次阶段考试与期末试卷，均含解答；页面另有历年试卷档案。ZIP为2010整课包，不是全部历年试卷合订。', 'https://ocw.mit.edu/courses/18-06-linear-algebra-spring-2010/18.06-spring-2010.zip', 'MIT OpenCourseWare / MIT Mathematics', 'https://ocw.mit.edu/courses/18-06-linear-algebra-spring-2010/pages/exams/', 'Gilbert Strang（2010春季）；历年教师见各学期页面', 'LINK_ONLY', NULL, '原站标注 CC BY-NC-SA 4.0，须署名、非商业使用并按相同方式共享；第三方内容另看原站说明。本站提供官方链接，不镜像正文。', NULL, 'PUBLISHED', '2026-11-07核验官方来源、完整范围及文件入口；仅链接导航，未作教学内容审校。', NULL, NULL);

INSERT INTO study_resource (title, summary, subject, knowledge_points, difficulty_level, estimated_minutes, resource_type, resource_kind, language_code, coverage_note, download_url, source_name, source_url, author, license_code, license_url, license_note, content_text, status, review_note, reviewed_by, reviewed_at)
VALUES ('线性代数：UC Davis 完整教材', 'Linear Algebra。面向理工科线性代数课程的完整教材，兼顾矩阵计算、线性空间概念及章末复习题。', '线性代数', '线性代数,线性代数教材,线性映射,向量空间,特征值,最小二乘,奇异值', 2, 0, 'EXTERNAL', 'TEXTBOOK', 'en', '436页、17章：线性方程组、向量空间、矩阵、特征值、正交、最小二乘与奇异值，附样卷。', 'https://www.math.ucdavis.edu/~linear/linear-guest.pdf', 'University of California, Davis, Department of Mathematics', 'https://www.math.ucdavis.edu/~linear/', 'David Cherney；Tom Denton；Rohit Thomas；Andrew Waldron', 'LINK_ONLY', 'https://creativecommons.org/licenses/by-nc-sa/3.0/', '原站标注 CC BY-NC-SA 3.0 Unported，须署名、非商业使用并按相同方式共享；第三方内容另看原站说明。本站提供官方链接，不镜像正文。', NULL, 'PUBLISHED', '2026-11-07核验官方来源、完整范围及文件入口；仅链接导航，未作教学内容审校。', NULL, NULL);

INSERT INTO study_resource (title, summary, subject, knowledge_points, difficulty_level, estimated_minutes, resource_type, resource_kind, language_code, coverage_note, download_url, source_name, source_url, author, license_code, license_url, license_note, content_text, status, review_note, reviewed_by, reviewed_at)
VALUES ('北大概率统计：完整课堂讲义', '北京大学教师官方提供的概率统计课堂打印讲义，适合系统学习大学概率论与数理统计。', '概率论与数理统计', '概率论与数理统计,概率论,概率统计,随机变量,随机向量,数学期望,大数定律,中心极限定理,参数估计', 2, 0, 'EXTERNAL', 'COURSE_PACK', 'zh', '261页中文讲义：概率空间、随机变量、随机向量、期望方差、极限定理、描述统计和参数估计。2016秋季课程。', 'https://math.pku.edu.cn/teachers/lidf/course/probstathsy/probstathsy.pdf', '北京大学数学科学学院 · 李东风课程主页', 'https://math.pku.edu.cn/teachers/lidf/course/index.htm', '李东风（课件制作）；对应教材作者为何书元', 'LINK_ONLY', NULL, '官方公开资料，阅读及下载按原站条款。未登记全文再分发授权，本站仅提供官方链接。', NULL, 'PUBLISHED', '2026-11-07核验官方来源、完整范围及文件入口；仅链接导航，未作教学内容审校。', NULL, NULL);

INSERT INTO study_resource (title, summary, subject, knowledge_points, difficulty_level, estimated_minutes, resource_type, resource_kind, language_code, coverage_note, download_url, source_name, source_url, author, license_code, license_url, license_note, content_text, status, review_note, reviewed_by, reviewed_at)
VALUES ('MIT 概率与统计：完整课程与复习资料', '18.05 Introduction to Probability and Statistics, Spring 2022。完整概率统计课程，附各课阅读讲义、课堂题与解答、两次阶段考试和期末复习全套材料。', '概率论与数理统计', '概率论与数理统计,概率论,数理统计,贝叶斯推断,假设检验,置信区间,回归分析,期末复习', 2, 0, 'EXTERNAL', 'COURSE_PACK', 'en', '概率与统计整门课程：条件概率、分布、贝叶斯推断、检验、置信区间、回归。附复习题、模拟期末、正式期末与解答。', 'https://ocw.mit.edu/courses/18-05-introduction-to-probability-and-statistics-spring-2022/18.05-spring-2022.zip', 'MIT OpenCourseWare', 'https://ocw.mit.edu/courses/18-05-introduction-to-probability-and-statistics-spring-2022/', 'Jeremy Orloff；Jennifer French Kamrin', 'LINK_ONLY', NULL, '原站标注 CC BY-NC-SA 4.0，须署名、非商业使用并按相同方式共享；第三方内容另看原站说明。本站提供官方链接，不镜像正文。', NULL, 'PUBLISHED', '2026-11-07核验官方来源、完整范围及文件入口；仅链接导航，未作教学内容审校。', NULL, NULL);

INSERT INTO study_resource (title, summary, subject, knowledge_points, difficulty_level, estimated_minutes, resource_type, resource_kind, language_code, coverage_note, download_url, source_name, source_url, author, license_code, license_url, license_note, content_text, status, review_note, reviewed_by, reviewed_at)
VALUES ('概率论导论：Dartmouth 完整教材', 'Grinstead and Snell’s Introduction to Probability — The CHANCE Project。大学概率论完整教材，包含离散与连续概率、极限定理，并延伸到马尔可夫链与随机游走。', '概率论与数理统计', '概率论,概率论教材,条件概率,随机变量,数学期望,大数定律,中心极限定理,马尔可夫链', 2, 0, 'EXTERNAL', 'TEXTBOOK', 'en', '518页、12章：离散与连续概率、极限定理、马尔可夫链、随机游走。2006版完整概率论教材，不含完整统计推断课程。', 'https://math.dartmouth.edu/~prob/prob/prob.pdf', 'Dartmouth College Mathematics / The CHANCE Project', 'https://math.dartmouth.edu/~prob/prob/prob.pdf', 'Charles M. Grinstead；J. Laurie Snell；此2006版本由Peter G. Doyle整理', 'LINK_ONLY', NULL, '原文件声明 GNU Free Documentation License；具体版本及使用条件请查阅原书。本站仅提供官方完整PDF链接。', NULL, 'PUBLISHED', '2026-11-07核验官方来源、完整范围及文件入口；仅链接导航，未作教学内容审校。', NULL, NULL);

INSERT INTO study_resource (title, summary, subject, knowledge_points, difficulty_level, estimated_minutes, resource_type, resource_kind, language_code, coverage_note, download_url, source_name, source_url, author, license_code, license_url, license_note, content_text, status, review_note, reviewed_by, reviewed_at)
VALUES ('普通物理一：中文整学期课程', '30 讲、46 小时 32 分钟的完整学期课程，配套分讲手写笔记，覆盖力学、波动和热学。', '大学物理', '大学物理,狭义相对论与洛伦兹变换,牛顿定律、动量、质心,能量与势能,转动、角动量与惯性力,引力与流体,波动与声,热平衡、热力学第一定律、熵与第二定律', 2, 0, 'EXTERNAL', 'COURSE_PACK', 'zh-Hant', '30讲、完整18周课程：力学、振动、波动与热学，配套分讲手写笔记。2013秋季；课程可逐讲阅读学习。', NULL, '清华大学（新竹）开放式课程', 'https://ocw.nthu.edu.tw/courses/96', '林秀豪 教授', 'LINK_ONLY', NULL, '原站声明仅供学术使用并限制商业使用；视频与讲义的具体使用遵守原站条款。本站提供官方链接。', NULL, 'PUBLISHED', '2026-11-07核验官方来源、完整范围及文件入口；仅链接导航，未作教学内容审校。', NULL, NULL);

INSERT INTO study_resource (title, summary, subject, knowledge_points, difficulty_level, estimated_minutes, resource_type, resource_kind, language_code, coverage_note, download_url, source_name, source_url, author, license_code, license_url, license_note, content_text, status, review_note, reviewed_by, reviewed_at)
VALUES ('普通物理二：中文整学期课程', '32 讲、50 小时 25 分钟的完整学期课程，涵盖电磁学、光及近代物理，配套分讲手写笔记。', '大学物理', '大学物理,电荷、电场、高斯定律与电势,磁场与磁力,毕奥-萨伐尔定律、安培定律,电磁感应、麦克斯韦方程与电磁波,光的波动性、分辨率与光子,量子力学、原子、固体,核物理、放射性、基本粒子与宇宙学', 2, 0, 'EXTERNAL', 'COURSE_PACK', 'zh-Hant', '32讲、完整18周课程：电磁学、光学、量子与近代物理，配套分讲笔记。2014春季。', NULL, '清华大学（新竹）开放式课程', 'https://ocw.nthu.edu.tw/courses/107', '林秀豪 教授', 'LINK_ONLY', NULL, '原站声明仅供学术使用并限制商业使用；视频与讲义的具体使用遵守原站条款。本站提供官方链接。', NULL, 'PUBLISHED', '2026-11-07核验官方来源、完整范围及文件入口；仅链接导航，未作教学内容审校。', NULL, NULL);

INSERT INTO study_resource (title, summary, subject, knowledge_points, difficulty_level, estimated_minutes, resource_type, resource_kind, language_code, coverage_note, download_url, source_name, source_url, author, license_code, license_url, license_note, content_text, status, review_note, reviewed_by, reviewed_at)
VALUES ('大学物理 第1卷：力学、振动与波', 'University Physics Volume 1。微积分基础的大学物理三卷本第 1 卷，完整 17 章，覆盖力学、振动、波与声。', '大学物理', '大学物理,测量与矢量,直线/平面运动、牛顿定律,功与能、动量,刚体转动、角动量、平衡,引力、流体,振动、波与声', 2, 0, 'EXTERNAL', 'TEXTBOOK', 'en', '完整第1卷、17章：运动学、牛顿定律、能量、动量、转动、引力、流体、振动与波。三卷系列中的第一册。', 'https://assets.openstax.org/oscms-prodcms/media/documents/university-physics-volume-1_-_WEB.pdf', 'OpenStax · Rice University', 'https://openstax.org/details/books/university-physics-volume-1', 'Samuel J. Ling；Jeff Sanny；William Moebs（主编/主要作者，其他贡献者见前言）', 'LINK_ONLY', 'https://creativecommons.org/licenses/by-nc-sa/4.0/', '原站标注 CC BY-NC-SA 4.0，须署名、非商业使用并按相同方式共享；第三方内容另看原站说明。本站提供官方链接，不镜像正文。', NULL, 'PUBLISHED', '2026-11-07核验官方来源、完整范围及文件入口；仅链接导航，未作教学内容审校。', NULL, NULL);

INSERT INTO study_resource (title, summary, subject, knowledge_points, difficulty_level, estimated_minutes, resource_type, resource_kind, language_code, coverage_note, download_url, source_name, source_url, author, license_code, license_url, license_note, content_text, status, review_note, reviewed_by, reviewed_at)
VALUES ('大学物理 第2卷：热学、电与磁', 'University Physics Volume 2。微积分基础的大学物理三卷本第 2 卷，完整 16 章，覆盖热学、电与磁。', '大学物理', '大学物理,温度、热与气体动理论,热力学第一、第二定律,电荷、电场、电势、电容,电流、电阻与直流电路,磁场、磁感应、电感与交流电路,电磁波', 2, 0, 'EXTERNAL', 'TEXTBOOK', 'en', '完整第2卷、16章：热力学、电场、电势、电路、磁场、电磁感应与电磁波。三卷系列中的第二册。', 'https://assets.openstax.org/oscms-prodcms/media/documents/university-physics-volume-2_-_WEB.pdf', 'OpenStax · Rice University', 'https://openstax.org/details/books/university-physics-volume-2', 'Samuel J. Ling；Jeff Sanny；William Moebs（主编/主要作者，其他贡献者见前言）', 'LINK_ONLY', 'https://creativecommons.org/licenses/by-nc-sa/4.0/', '原站标注 CC BY-NC-SA 4.0，须署名、非商业使用并按相同方式共享；第三方内容另看原站说明。本站提供官方链接，不镜像正文。', NULL, 'PUBLISHED', '2026-11-07核验官方来源、完整范围及文件入口；仅链接导航，未作教学内容审校。', NULL, NULL);

INSERT INTO study_resource (title, summary, subject, knowledge_points, difficulty_level, estimated_minutes, resource_type, resource_kind, language_code, coverage_note, download_url, source_name, source_url, author, license_code, license_url, license_note, content_text, status, review_note, reviewed_by, reviewed_at)
VALUES ('大学物理 第3卷：光学与近代物理', 'University Physics Volume 3。微积分基础的大学物理三卷本第 3 卷，完整 11 章，覆盖光学与近代物理。', '大学物理', '大学物理,几何光学、干涉与衍射,相对论,光子、物质波与量子力学,原子、凝聚态物理,核物理、粒子物理与宇宙学', 2, 0, 'EXTERNAL', 'TEXTBOOK', 'en', '完整第3卷、11章：光学、相对论、量子、原子、凝聚态、核与粒子物理。三卷系列中的第三册。', 'https://assets.openstax.org/oscms-prodcms/media/documents/university-physics-volume-3_-_WEB.pdf', 'OpenStax · Rice University', 'https://openstax.org/details/books/university-physics-volume-3', 'Samuel J. Ling；Jeff Sanny；William Moebs（主编/主要作者，其他贡献者见前言）', 'LINK_ONLY', 'https://creativecommons.org/licenses/by-nc-sa/4.0/', '原站标注 CC BY-NC-SA 4.0，须署名、非商业使用并按相同方式共享；第三方内容另看原站说明。本站提供官方链接，不镜像正文。', NULL, 'PUBLISHED', '2026-11-07核验官方来源、完整范围及文件入口；仅链接导航，未作教学内容审校。', NULL, NULL);

INSERT INTO study_resource (title, summary, subject, knowledge_points, difficulty_level, estimated_minutes, resource_type, resource_kind, language_code, coverage_note, download_url, source_name, source_url, author, license_code, license_url, license_note, content_text, status, review_note, reviewed_by, reviewed_at)
VALUES ('大学学术英语读写：完整教材与写作手册', 'Writing Guide with Handbook。面向大学一年级写作与 composition 课程，系统训练学术阅读、论证、研究写作，并附语法、编辑及引文手册。', '大学英语', '大学英语,修辞情境、写作过程与文体,阅读和分析示范文本,论证、研究与证据运用,多种写作体裁及有效写作策略,语法、常见用法、编辑和文献引用,英语,阅读,写作', 2, 0, 'EXTERNAL', 'TEXTBOOK', 'en', '完整 Writing Guide with Handbook：大学学术阅读、论证、研究写作、语法与引文手册。适合学术英语，不是四、六级专用教材。', 'https://assets.openstax.org/oscms-prodcms/media/documents/WritingGuide-WEB.pdf', 'OpenStax · Rice University', 'https://openstax.org/details/books/writing-guide', 'Michelle Bachelor Robinson；Maria Jerskey；featuring Toby Fulwiler', 'LINK_ONLY', 'https://creativecommons.org/licenses/by-nc-sa/4.0/', '原站标注 CC BY-NC-SA 4.0，须署名、非商业使用并按相同方式共享；第三方内容另看原站说明。本站提供官方链接，不镜像正文。', NULL, 'PUBLISHED', '2026-11-07核验官方来源、完整范围及文件入口；仅链接导航，未作教学内容审校。', NULL, NULL);

INSERT INTO study_resource (title, summary, subject, knowledge_points, difficulty_level, estimated_minutes, resource_type, resource_kind, language_code, coverage_note, download_url, source_name, source_url, author, license_code, license_url, license_note, content_text, status, review_note, reviewed_by, reviewed_at)
VALUES ('MIT 学术英语写作：完整公开课程包', '21G.222 Expository Writing for Bilingual Students。面向本科双语/英语第二语言学生的学术写作课程公开资料包，涵盖说明文阅读、论文写作、词汇与语法。', '大学英语', '大学英语,说明文阅读与分析,学术词汇,清晰准确的英文书面表达,个人文章、非虚构书评、立场论文、概念解释,语法、标点与修改,口头讨论与演示,英语,阅读,写作', 2, 0, 'EXTERNAL', 'COURSE_PACK', 'en', '本科双语学生学术英语写作课程：阅读、词汇、文章、书评和立场论文。ZIP包含该在线版全部公开资料，不含指定商业教材。', 'https://ocw.mit.edu/courses/21g-222-expository-writing-for-bilingual-students-fall-2002/21g.222-fall-2002.zip', 'MIT OpenCourseWare', 'https://ocw.mit.edu/courses/21g-222-expository-writing-for-bilingual-students-fall-2002/', 'Patricia Brennecke', 'LINK_ONLY', 'https://creativecommons.org/licenses/by-nc-sa/4.0/', '原站标注 CC BY-NC-SA 4.0，须署名、非商业使用并按相同方式共享；第三方内容另看原站说明。本站提供官方链接，不镜像正文。', NULL, 'PUBLISHED', '2026-11-07核验官方来源、完整范围及文件入口；仅链接导航，未作教学内容审校。', NULL, NULL);

INSERT INTO study_resource (title, summary, subject, knowledge_points, difficulty_level, estimated_minutes, resource_type, resource_kind, language_code, coverage_note, download_url, source_name, source_url, author, license_code, license_url, license_note, content_text, status, review_note, reviewed_by, reviewed_at)
VALUES ('四、六级官方备考：完整大纲与样卷', '全国大学英语四、六级考试大纲（2016年修订版）。215 页官方完整 PDF，包含四/六级考试结构与词表、笔试和口试样卷、答题卡及作文/翻译评分样卷。', '大学英语', '大学英语,四级和六级语言技能要求、考试结构与分数说明,完整考试词表,四级笔试和口试样卷,六级笔试和口试样卷,四级/六级答题卡,作文与翻译评分标准及评分样卷,英语,阅读,写作', 2, 0, 'EXTERNAL', 'REVIEW_PACK', 'mul', '215页完整PDF：四、六级大纲、词表、笔试/口试样卷、答题卡与评分样卷。2016修订版；不含听力音频，非各校期末通用资料。', 'https://cet.neea.edu.cn/res/Home/1704/55b02330ac17274664f06d9d3db8249d.pdf', '中国教育考试网 / 教育部教育考试院', 'https://cet.neea.edu.cn/html1/folder/16113/1588-1.htm', '全国大学英语四、六级考试委员会（考试大纲制定机构）；教育部教育考试院官网公开发布', 'LINK_ONLY', NULL, '官方公开资料，阅读及下载按原站条款。未登记全文再分发授权，本站仅提供官方链接。', NULL, 'PUBLISHED', '2026-11-07核验官方来源、完整范围及文件入口；仅链接导航，未作教学内容审校。', NULL, NULL);

