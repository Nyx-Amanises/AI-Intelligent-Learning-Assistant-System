package com.aiassistant.learning.vo.page;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PageVO<T> {

    private Long current;

    private Long size;

    private Long total;

    private Long pages;

    private List<T> records;
}
