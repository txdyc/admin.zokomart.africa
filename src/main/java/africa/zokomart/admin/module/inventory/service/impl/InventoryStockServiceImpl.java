package africa.zokomart.admin.module.inventory.service.impl;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.inventory.entity.InventoryStock;
import africa.zokomart.admin.module.inventory.entity.InventoryTransaction;
import africa.zokomart.admin.module.inventory.mapper.InventoryStockMapper;
import africa.zokomart.admin.module.inventory.mapper.InventoryTransactionMapper;
import africa.zokomart.admin.module.inventory.service.InventoryStockService;
import africa.zokomart.admin.module.supplierproduct.entity.SupplierProduct;
import africa.zokomart.admin.module.supplierproduct.mapper.SupplierProductMapper;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InventoryStockServiceImpl extends ServiceImpl<InventoryStockMapper, InventoryStock>
        implements InventoryStockService {

    private static final int MAX_RETRY = 3;

    private final InventoryTransactionMapper txMapper;
    private final SupplierProductMapper supplierProductMapper;

    @Override
    public int getQty(Long supplierProductId) {
        InventoryStock s = findStock(supplierProductId);
        return s == null ? 0 : s.getQuantity();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStock(Long supplierProductId, int qtyChange, String type,
                            String refType, Long refId, String refNo, String remark) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            InventoryStock stock = findStock(supplierProductId);
            int before;
            int after;
            boolean ok;
            if (stock == null) {
                before = 0;
                after = qtyChange;
                if (after < 0) {
                    throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "库存不足");
                }
                ok = createStock(supplierProductId, after);
            } else {
                before = stock.getQuantity();
                after = before + qtyChange;
                if (after < 0) {
                    throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "库存不足");
                }
                stock.setQuantity(after);
                // MP 乐观锁：version 不匹配则 update 影响行数为 0
                ok = updateById(stock);
            }
            if (ok) {
                writeTx(supplierProductId, qtyChange, before, after, type, refType, refId, refNo, remark);
                return;
            }
        }
        throw new BusinessException(ResultCode.BUSINESS_ERROR, "库存更新冲突，请重试");
    }

    private InventoryStock findStock(Long supplierProductId) {
        return getOne(Wrappers.<InventoryStock>lambdaQuery()
                .eq(InventoryStock::getSupplierProductId, supplierProductId), false);
    }

    /** 新建库存记录，冗余 supplier/brand/category 取自供应商产品。唯一键冲突（并发首建）返回 false 以触发重试。 */
    private boolean createStock(Long supplierProductId, int quantity) {
        SupplierProduct sp = supplierProductMapper.selectById(supplierProductId);
        InventoryStock stock = new InventoryStock();
        stock.setSupplierProductId(supplierProductId);
        if (sp != null) {
            stock.setSupplierId(sp.getSupplierId());
            stock.setBrandId(sp.getBrandId());
            stock.setCategoryId(sp.getCategoryId());
        }
        stock.setQuantity(quantity);
        try {
            return save(stock);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return false; // 并发下已被他人创建，重试走更新分支
        }
    }

    private void writeTx(Long supplierProductId, int qtyChange, int before, int after,
                         String type, String refType, Long refId, String refNo, String remark) {
        InventoryTransaction tx = new InventoryTransaction();
        tx.setSupplierProductId(supplierProductId);
        tx.setType(type);
        tx.setQtyChange(qtyChange);
        tx.setBeforeQty(before);
        tx.setAfterQty(after);
        tx.setRefType(refType);
        tx.setRefId(refId);
        tx.setRefNo(refNo);
        tx.setOperatorId(currentUserIdOrNull());
        tx.setRemark(remark);
        tx.setCreateTime(LocalDateTime.now());
        txMapper.insert(tx);
    }

    private Long currentUserIdOrNull() {
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
