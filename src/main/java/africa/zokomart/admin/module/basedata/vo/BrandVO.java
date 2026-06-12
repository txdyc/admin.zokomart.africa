package africa.zokomart.admin.module.basedata.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BrandVO {
    private Long id;
    private String name;
    private String code;
    private String logoUrl;
    private Integer sort;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
}
