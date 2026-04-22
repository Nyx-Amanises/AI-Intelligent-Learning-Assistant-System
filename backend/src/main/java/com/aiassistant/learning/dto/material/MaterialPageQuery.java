package com.aiassistant.learning.dto.material;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 资料分页查询参数。
 *
 * <p>前端查询资料列表时会带上这些字段，Service 根据它们拼接查询条件。</p>
 */
@Data
public class MaterialPageQuery {

    /**
     * 当前页码，从 1 开始。
     */
    @Min(value = 1, message = "页码最小为1")
    private Long current = 1L;

    /**
     * 每页条数，限制最大 50，避免一次查询返回太多数据。
     */
    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 50, message = "每页条数最大为50")
    private Long size = 10L;

    /**
     * 标题关键词，非空时按标题模糊搜索。
     */
    private String title;

    /**
     * 资料类型筛选条件，例如 PDF、DOCX、TXT、TEXT。
     */
    private String materialType;

    /**
     * 解析状态筛选条件，例如 PENDING、PROCESSING、SUCCESS、FAILED。
     */
    private String parseStatus;
}
