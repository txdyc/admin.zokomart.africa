package africa.zokomart.admin.module.basedata.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CategoryVO {
    private Long id;
    private Long parentId;
    private String name;
    private Integer sort;
    private Integer status;
    private LocalDateTime createTime;
    private List<CategoryVO> children = new ArrayList<>();
}
