package africa.zokomart.admin.module.wcsync.client;

public interface WooCommerceClient {

    boolean configured();

    long ensureCategory(String name, long parentWcId);

    long ensureBrand(String name);

    Long findProductIdBySku(String sku);

    /** 新建商品，返回 WC 商品 id + 主图 media id。 */
    WcProductRef createProduct(WcProduct product);

    /** 更新商品，返回 WC 商品 id + 主图 media id。 */
    WcProductRef updateProduct(long wcProductId, WcProduct product);

    /** 读取已存在商品当前主图 media id；无图返回 null。供历史脏记录"收编"用。 */
    Long findProductMainImageId(long wcProductId);

    /** 读取商品当前描述与图片列表（广告图同步用）。 */
    WcProductDetail getProduct(long wcProductId);

    /** 仅更新商品描述（广告图区块二次写入用）。 */
    void updateProductDescription(long wcProductId, String description);
}
