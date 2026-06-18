package africa.zokomart.admin.module.wcsync.client;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 推往 WooCommerce 的单个商品载体。regularPrice 为字符串（WC 价格用字符串）。 */
@Data
@AllArgsConstructor
public class WcProduct {
    private String name;
    private String sku;
    private String regularPrice;
    private String salePrice;
    private int stockQuantity;
    private String status;     // "publish" | "draft"
    private long categoryId;   // WC 商品分类 id（品牌）
    private String imageUrl;   // 可空
}
