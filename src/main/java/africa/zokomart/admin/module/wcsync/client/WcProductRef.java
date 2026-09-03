package africa.zokomart.admin.module.wcsync.client;

import lombok.Data;

import java.util.List;

/** create/update 返回：WC 商品 id + 主图 media id（可空）。 */
@Data
public class WcProductRef {
    private final long productId;
    private final Long imageId;
    /** 响应中全部图片（id+src，顺序同请求发送顺序）；旧构造器路径下为 null。 */
    private final List<WcImage> images;

    public WcProductRef(long productId, Long imageId) {
        this(productId, imageId, null);
    }

    public WcProductRef(long productId, Long imageId, List<WcImage> images) {
        this.productId = productId;
        this.imageId = imageId;
        this.images = images;
    }
}
