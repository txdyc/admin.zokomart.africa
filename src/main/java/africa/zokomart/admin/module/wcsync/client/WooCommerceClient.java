package africa.zokomart.admin.module.wcsync.client;

public interface WooCommerceClient {

    /** 是否已配置（base-url/key/secret 齐全）。 */
    boolean configured();

    /** 按名查/建 WC 商品分类，返回分类 id。 */
    long ensureCategory(String name);

    /** 按 SKU 查 WC 商品 id；不存在返回 null。 */
    Long findProductIdBySku(String sku);

    /** 新建商品，返回新 WC 商品 id。 */
    long createProduct(WcProduct product);

    /** 更新已存在商品。 */
    void updateProduct(long wcProductId, WcProduct product);
}
