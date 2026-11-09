ALTER TABLE study_resource
    ADD COLUMN document_url VARCHAR(255) NULL COMMENT '本站托管的完整教材 PDF 路径';

ALTER TABLE study_resource
    ADD COLUMN page_count INT NULL COMMENT '已核验 PDF 总页数';
