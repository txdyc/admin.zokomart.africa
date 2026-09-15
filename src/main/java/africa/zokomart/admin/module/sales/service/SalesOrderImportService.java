package africa.zokomart.admin.module.sales.service;

import africa.zokomart.admin.module.sales.vo.SalesOrderImportResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface SalesOrderImportService {

    /** 解析 .xlsx，按客户+日期归并成销售订单并落库（扣库存）。整单失败不影响其它订单。 */
    SalesOrderImportResultVO importExcel(MultipartFile file);
}
