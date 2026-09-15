package africa.zokomart.admin.module.sales.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.sales.dto.SalesOrderCreateDTO;
import africa.zokomart.admin.module.sales.entity.SalesOrder;
import africa.zokomart.admin.module.sales.importer.SalesImportKey;
import africa.zokomart.admin.module.sales.importer.SalesImportRow;
import africa.zokomart.admin.module.sales.importer.SalesOrderExcelParser;
import africa.zokomart.admin.module.sales.importer.SalesOrderGrouper;
import africa.zokomart.admin.module.sales.mapper.SalesOrderMapper;
import africa.zokomart.admin.module.sales.service.SalesOrderImportService;
import africa.zokomart.admin.module.sales.service.SalesOrderService;
import africa.zokomart.admin.module.sales.vo.SalesOrderImportError;
import africa.zokomart.admin.module.sales.vo.SalesOrderImportResultVO;
import africa.zokomart.admin.module.supplierproduct.entity.SupplierProduct;
import africa.zokomart.admin.module.supplierproduct.mapper.SupplierProductMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 销售订单 Excel 导入。
 *
 * <p>本类<b>刻意不加</b> {@code @Transactional}：落库通过注入的 {@link SalesOrderService} 代理调用，
 * 由 {@code create()} 自己的事务边界保证「每单一个事务，单失败只回滚该单」。
 * 若在本类上加事务，一单失败会把整批回滚，与「整单失败、其余照常」的需求冲突。
 */
@Service
@RequiredArgsConstructor
public class SalesOrderImportServiceImpl implements SalesOrderImportService {

    private static final Logger log = LoggerFactory.getLogger(SalesOrderImportServiceImpl.class);

    private static final String XLSX_SUFFIX = ".xlsx";

    /** 未分类异常一律回这句给前端；真正原因（含 MyBatis 的 SQL/绑定参数，可能带客户 PII）只落服务端日志。 */
    private static final String GENERIC_FAILURE_REASON = "订单处理异常，请联系管理员查看服务端日志";

    private final SalesOrderService salesOrderService;
    private final SalesOrderMapper salesOrderMapper;
    private final SupplierProductMapper supplierProductMapper;

    private final SalesOrderExcelParser parser = new SalesOrderExcelParser();
    private final SalesOrderGrouper grouper = new SalesOrderGrouper();

    @Override
    public SalesOrderImportResultVO importExcel(MultipartFile file) {
        List<SalesImportRow> rows = parser.parse(readBytes(file));
        LinkedHashMap<SalesImportKey, List<SalesImportRow>> groups = grouper.group(rows);

        SalesOrderImportResultVO result = new SalesOrderImportResultVO();
        result.setTotalRows(rows.size());
        result.setOrderCount(groups.size());

        Set<SalesImportKey> existing = loadExistingKeys(groups.keySet());

        for (Map.Entry<SalesImportKey, List<SalesImportRow>> e : groups.entrySet()) {
            handleGroup(e.getKey(), e.getValue(), existing, result);
        }
        return result;
    }

    private void handleGroup(SalesImportKey key, List<SalesImportRow> group,
                             Set<SalesImportKey> existing, SalesOrderImportResultVO result) {
        try {
            String badRow = group.stream().map(SalesImportRow::error)
                    .filter(java.util.Objects::nonNull).findFirst().orElse(null);
            if (badRow != null) {
                throw new IllegalArgumentException(badRow);
            }
            if (existing.contains(key)) {
                result.setSkipped(result.getSkipped() + 1);
                return;
            }
            salesOrderService.create(toDto(group));
            result.setSuccess(result.getSuccess() + 1);
        } catch (IllegalArgumentException | BusinessException ex) {
            // 这两类异常的 message 是给操作员看的，本来就是安全、可读的业务提示，原样透出。
            String failingCode = ex instanceof ProductResolutionException pre ? pre.productCode() : null;
            recordFailure(result, group, ex.getMessage(), failingCode);
        } catch (Exception ex) {
            // 未分类异常（典型如 MyBatis PersistenceException）message 里可能带 SQL 与
            // 绑定参数——客户姓名/电话/地址等 PII，绝不能原样吐给前端；服务端留痕即可。
            log.warn("导入订单处理异常 rows={}", rowsOf(group), ex);
            recordFailure(result, group, GENERIC_FAILURE_REASON, null);
        }
    }

    private static String rowsOf(List<SalesImportRow> group) {
        return group.stream().map(r -> String.valueOf(r.rowNum())).collect(Collectors.joining(","));
    }

    private SalesOrderCreateDTO toDto(List<SalesImportRow> group) {
        SalesImportRow first = group.get(0);
        SalesOrderCreateDTO dto = new SalesOrderCreateDTO();
        dto.setCustomerName(first.customerName());
        dto.setCustomerPhone(first.phone());
        dto.setCustomerAddress(first.address());
        dto.setCity(blankToNull(first.city()));
        dto.setOrderDate(first.orderDate());

        List<SalesOrderCreateDTO.Item> items = new ArrayList<>(group.size());
        for (SalesImportRow r : group) {
            SupplierProduct sp = resolveProduct(r.productCode());
            SalesOrderCreateDTO.Item item = new SalesOrderCreateDTO.Item();
            item.setSupplierProductId(sp.getId());
            item.setQty(r.quantity());
            // Sale Price 是行小计：单价 = 小计 / 数量；amount 保留原值，避免除不尽导致总额漂移
            item.setUnitPrice(r.salePrice().divide(BigDecimal.valueOf(r.quantity()), 2, RoundingMode.HALF_UP));
            item.setAmount(r.salePrice());
            item.setExternalOrderId(r.externalOrderId());
            items.add(item);
        }
        dto.setItems(items);
        return dto;
    }

    /** product_code 在 supplier_product 里只是「供应商内唯一」，跨供应商可能重名 → 歧义视为失败。 */
    private SupplierProduct resolveProduct(String code) {
        List<SupplierProduct> found = supplierProductMapper.selectList(
                Wrappers.<SupplierProduct>lambdaQuery().eq(SupplierProduct::getProductCode, code));
        if (found.isEmpty()) {
            throw new ProductResolutionException("产品编码不存在: " + code, code);
        }
        if (found.size() > 1) {
            throw new ProductResolutionException(
                    "产品编码 " + code + " 匹配到 " + found.size() + " 个供应商产品，无法确定", code);
        }
        return found.get(0);
    }

    /**
     * 携带具体失败编码的解析异常。一个订单可能由多行合并而成，出错的编码不一定是
     * 分组第一行的编码；recordFailure 若退化成「取分组内第一个非空编码」，会在合并
     * 多行订单（本功能的常态）时把操作员指向一个完全合法、根本没出错的编码。
     */
    private static final class ProductResolutionException extends IllegalArgumentException {
        private final String productCode;

        ProductResolutionException(String message, String productCode) {
            super(message);
            this.productCode = productCode;
        }

        String productCode() {
            return productCode;
        }
    }

    /** 一次性把相关日期的已有订单读进内存建查重集合，避免逐单查库。 */
    private Set<SalesImportKey> loadExistingKeys(Set<SalesImportKey> incoming) {
        List<LocalDate> dates = incoming.stream()
                .map(SalesImportKey::orderDate).filter(java.util.Objects::nonNull)
                .distinct().toList();
        if (dates.isEmpty()) {
            return new HashSet<>();
        }
        return salesOrderMapper.selectList(Wrappers.<SalesOrder>lambdaQuery()
                        .in(SalesOrder::getOrderDate, dates))
                .stream()
                .map(o -> SalesImportKey.of(o.getCustomerPhone(), o.getCustomerName(),
                        o.getCustomerAddress(), o.getOrderDate()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * @param failingCode 明确知道的失败编码（如 resolveProduct 抛出的 ProductResolutionException）优先使用；
     *                    为 null 时（如整行校验错误）退化为分组内第一个非空编码，仅作定位参考。
     */
    private void recordFailure(SalesOrderImportResultVO result, List<SalesImportRow> group,
                                String reason, String failingCode) {
        result.setFailed(result.getFailed() + 1);
        String code = failingCode != null ? failingCode
                : group.stream().map(SalesImportRow::productCode)
                        .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        result.getErrors().add(new SalesOrderImportError(
                rowsOf(group),
                group.stream().map(SalesImportRow::externalOrderId)
                        .filter(java.util.Objects::nonNull).collect(Collectors.joining(",")),
                group.get(0).customerName(),
                code,
                reason));
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.IMPORT_FILE_INVALID);
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(XLSX_SUFFIX)) {
            throw new BusinessException(ResultCode.IMPORT_FILE_INVALID);
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.IMPORT_FILE_INVALID);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
