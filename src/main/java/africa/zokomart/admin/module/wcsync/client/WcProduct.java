package africa.zokomart.admin.module.wcsync.client;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

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
    private long categoryId;   // WC 商品分类 id（产品真实分类的叶子；<=0 表示不设）
    private long brandWcId;    // WC 品牌 id（<=0 表示不设）
    private String imageSrc;   // 要 sideload 的源图 URL；null = 本次不传 images 字段（WC 不动现有图）
    /** 非 null = 本次完整指定 images 列表（含主图；覆盖 imageSrc 分支）；null = 沿用 imageSrc 旧语义。 */
    private List<WcImage> imagesOverride;
}
