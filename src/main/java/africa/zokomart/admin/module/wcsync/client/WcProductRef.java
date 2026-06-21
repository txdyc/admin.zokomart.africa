package africa.zokomart.admin.module.wcsync.client;

import lombok.AllArgsConstructor;
import lombok.Data;

/** create/update 返回：WC 商品 id + 主图 media id（可空）。 */
@Data
@AllArgsConstructor
public class WcProductRef {
    private long productId;
    private Long imageId;   // images[0].id，可空
}
