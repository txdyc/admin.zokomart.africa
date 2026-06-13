package africa.zokomart.admin.module.inventory.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 出入库流水：只增不改，不参与逻辑删除/乐观锁，故不继承 BaseEntity。 */
@Data
@TableName("inventory_transaction")
public class InventoryTransaction {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long supplierProductId;
    private String type;
    private Integer qtyChange;
    private Integer beforeQty;
    private Integer afterQty;
    private String refType;
    private Long refId;
    private String refNo;
    private Long operatorId;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
