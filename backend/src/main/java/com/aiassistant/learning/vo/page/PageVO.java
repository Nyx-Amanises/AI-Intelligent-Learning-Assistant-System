package com.aiassistant.learning.vo.page;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 通用分页返回对象。
 *
 * <p>很多列表接口都可以复用这个结构。泛型 T 表示列表中每一条记录的类型。</p>
 *
 * @param <T> 分页记录类型
 */
@Data
@Builder
public class PageVO<T> {

    /**
     * 当前页码。
     */
    private Long current;

    /**
     * 每页条数。
     */
    private Long size;

    /**
     * 总记录数。
     */
    private Long total;

    /**
     * 总页数。
     */
    private Long pages;

    /**
     * 当前页记录列表。
     */
    private List<T> records;
}
