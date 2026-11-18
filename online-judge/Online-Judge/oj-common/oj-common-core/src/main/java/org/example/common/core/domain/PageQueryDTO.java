package org.example.common.core.domain;

import lombok.Data;

@Data
public class PageQueryDTO {

    private Integer pageSize = 10;  //每页数据量  必传

    private Integer pageNum = 1;   //页码   必传

}
