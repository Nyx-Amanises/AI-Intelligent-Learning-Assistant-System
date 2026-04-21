package com.aiassistant.learning.vo.analytics;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MasteryDistributionVO {

    private String level;

    private String label;

    private Integer count;

    private BigDecimal percent;
}
