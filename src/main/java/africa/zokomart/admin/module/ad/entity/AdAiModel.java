package africa.zokomart.admin.module.ad.entity;

import africa.zokomart.admin.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ad_ai_model")
public class AdAiModel extends BaseEntity {
    private String name;
    private String baseUrl;
    private String apiKey;
    private String modelCode;
    /** 接口形态：CHAT=chat/completions 多模态；IMAGE=images generations/edits。 */
    private String apiFormat;
    private Integer enabled;
    private Integer sort;
    private String remark;
}
