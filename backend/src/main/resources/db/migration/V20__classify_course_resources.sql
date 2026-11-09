ALTER TABLE study_resource
    ADD COLUMN resource_kind VARCHAR(32) NOT NULL DEFAULT 'KNOWLEDGE_CARD' COMMENT 'TEXTBOOK/COURSE_PACK/REVIEW_PACK/KNOWLEDGE_CARD';
ALTER TABLE study_resource
    ADD COLUMN language_code VARCHAR(35) NULL COMMENT '资料语言，如 zh-CN、en';
ALTER TABLE study_resource
    ADD COLUMN coverage_note VARCHAR(1000) NULL COMMENT '完整资料的课程或章节覆盖范围';
ALTER TABLE study_resource
    ADD COLUMN download_url VARCHAR(1000) NULL COMMENT '经核实的官方完整资料下载链接';

CREATE INDEX idx_study_resource_status_kind ON study_resource (status, resource_kind);

-- 只调整 V19 的初始学习卡片；已经导入 study_material 的个人副本及其关联记录保留。
UPDATE study_resource
SET resource_kind = 'KNOWLEDGE_CARD', language_code = 'zh-CN',
    coverage_note = '单个知识点的简短学习卡片，不是完整教材或课程资料。',
    status = 'WITHDRAWN'
WHERE resource_type = 'INTERNAL'
  AND license_code = 'INTERNAL'
  AND source_name = '知序学习卡片'
  AND author = '知序 · AI 辅助编写'
  AND title IN (
    '函数与定义域：先确认输入是否合法',
    '复合函数与反函数：理清输入输出顺序',
    '奇偶性与周期性：利用图像对称简化问题',
    '基本初等函数：把图像与变化规律联系起来',
    '数列极限：无限接近一个确定的数',
    '函数极限与左右极限：先分清趋近方向',
    '无穷小与无穷大：描述变化过程而非固定数',
    '极限运算法则：能代入之前先检查条件',
    '夹逼准则：用上下界控制复杂函数',
    '两个重要极限：识别标准形式与弧度条件',
    '等价无穷小：只在允许的结构里替换',
    '连续与间断点：把极限和函数值接起来',
    '闭区间连续函数：最值与介值都有保障',
    '导数定义：从平均变化率走向瞬时变化率',
    '可导与连续：单向推论不能倒过来用',
    '基本求导法则：先识别运算结构',
    '链式法则：复合函数由外到内逐层求导',
    '隐函数与参数求导：别把因变量当常数',
    '微分与线性近似：用切线估算小变化',
    '中值定理：把整段变化联系到某点导数',
    '洛必达法则：只处理满足条件的未定式',
    '单调性与极值：看导数符号是否改变',
    '凹凸性与拐点：二阶导数零点只是候选',
    '泰勒公式：保留首个有效项与余项',
    '不定积分与原函数：积分常数不能遗漏',
    '换元积分法：把复合结构与微分配成一对',
    '分部积分法：选对需要求导的一部分',
    '定积分：带方向的累积与黎曼和',
    '微积分基本定理：累积函数的导数',
    '定积分的应用：面积与函数平均值'
  );

UPDATE study_resource
SET resource_kind = 'COURSE_PACK', language_code = 'en', estimated_minutes = 0,
    coverage_note = '单变量微积分课程，覆盖极限、导数、积分及其应用；课程资料以官方目录为准。'
WHERE resource_type = 'EXTERNAL' AND license_code = 'LINK_ONLY'
  AND source_name = 'MIT OpenCourseWare'
  AND title = 'MIT OCW：Single Variable Calculus'
  AND source_url = 'https://ocw.mit.edu/courses/18-01sc-single-variable-calculus-fall-2010/';

UPDATE study_resource
SET resource_kind = 'TEXTBOOK', language_code = 'en', estimated_minutes = 0,
    coverage_note = '微积分第一卷，覆盖函数与图像、极限、导数、积分及相关应用。'
WHERE resource_type = 'EXTERNAL' AND license_code = 'LINK_ONLY'
  AND source_name = 'OpenStax'
  AND title = 'OpenStax：Calculus Volume 1'
  AND source_url = 'https://openstax.org/details/books/calculus-volume-1';
