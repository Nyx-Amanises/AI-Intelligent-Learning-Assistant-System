# AI Learning Assistant Backend

## 技术栈

- Spring Boot 3
- MyBatis Plus
- MySQL
- Redis
- Lombok

## 当前已完成

- Maven 项目基础结构
- MyBatis Plus 基础配置
- 自动填充时间字段
- 逻辑删除基类
- 与数据库表对应的第一批实体类
- 基础 Mapper 接口
- 统一返回体与全局异常处理
- 注册、登录、获取当前用户信息接口
- JWT 鉴权拦截基础能力
- 文本资料新增、分页、详情、删除接口
- 文件上传与 `txt/pdf/docx` 解析入库能力
- AI 总结生成、生成记录落库、AI 笔记保存

## 建议下一步

1. 继续实现 AI 出题、题集查询和练习提交接口。
2. 为资料模块增加文件预览或下载接口。
3. 把状态字段提取成统一枚举，减少硬编码。
4. 最后增加接口文档和部署配置。
