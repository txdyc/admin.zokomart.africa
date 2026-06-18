package africa.zokomart.admin.module.wcsync.service;

import africa.zokomart.admin.module.wcsync.vo.WcSyncResultVO;

import java.util.List;

public interface WcSyncService {

    /** 把该供应商在选定品牌下的产品单向同步到 WooCommerce（按 SKU 幂等）。 */
    WcSyncResultVO syncSupplierBrands(Long supplierId, List<Long> brandIds);
}
