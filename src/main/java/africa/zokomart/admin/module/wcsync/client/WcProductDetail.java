package africa.zokomart.admin.module.wcsync.client;

import java.util.List;

/** getProduct 返回的商品当前状态（描述 + 图片列表，images 顺序即 WC 展示顺序）。 */
public record WcProductDetail(long id, String description, List<WcImage> images) { }
