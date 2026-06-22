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
}
