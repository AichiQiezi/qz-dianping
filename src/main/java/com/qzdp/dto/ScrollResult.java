package com.qzdp.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 *  封装滚动分页的数据
 * @author 六号风
 */
@Data
@Builder
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
