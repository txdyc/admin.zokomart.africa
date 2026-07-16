package africa.zokomart.admin.module.dashboard.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 通用「名称 + 金额」条目：按分类/品牌收入、供应商采购支出等横向条形图。 */
@Data
public class NamedAmountVO {
    private String name;
    private BigDecimal amount;
}
