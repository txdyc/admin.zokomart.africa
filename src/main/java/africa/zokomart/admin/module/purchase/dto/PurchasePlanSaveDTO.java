package africa.zokomart.admin.module.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class PurchasePlanSaveDTO {
    private Long id;
    private String remark;

    @NotEmpty(message = "采购计划明细不能为空")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "供应商产品不能为空")
        private Long supplierProductId;

        @NotNull(message = "采购数量不能为空")
        @Min(value = 0, message = "采购数量不能为负")
        private Integer purchaseQty;
    }
}
