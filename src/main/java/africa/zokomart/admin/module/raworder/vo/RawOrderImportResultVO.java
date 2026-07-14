package africa.zokomart.admin.module.raworder.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 导入结果汇总。total=数据行数；success + failed 应等于 total。 */
@Data
public class RawOrderImportResultVO {
    private int total;
    private int success;
    private int failed;
    private List<RawOrderRowError> errors = new ArrayList<>();
}
