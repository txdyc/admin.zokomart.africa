package africa.zokomart.admin.basedata;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.module.basedata.dto.SupplierSaveDTO;
import africa.zokomart.admin.module.basedata.dto.BrandSaveDTO;
import africa.zokomart.admin.module.basedata.service.BrandService;
import africa.zokomart.admin.module.basedata.service.SupplierBrandService;
import africa.zokomart.admin.module.basedata.service.SupplierService;
import africa.zokomart.admin.module.supplierproduct.dto.SupplierProductSaveDTO;
import africa.zokomart.admin.module.supplierproduct.service.SupplierProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SupplierBrandServiceTest {

    @Autowired SupplierBrandService supplierBrandService;
    @Autowired SupplierService supplierService;
    @Autowired BrandService brandService;
    @Autowired SupplierProductService supplierProductService;

    private long newSupplier(String tag) {
        SupplierSaveDTO s = new SupplierSaveDTO();
        s.setName("SB_Sup_" + tag);
        s.setStatus(1);
        return supplierService.createSupplier(s);
    }

    private long newBrand(String tag) {
        BrandSaveDTO b = new BrandSaveDTO();
        b.setName("SB_Brand_" + tag);
        b.setStatus(1);
        return brandService.createBrand(b);
    }

    @Test
    void assign_sets_idempotent_and_isAuthorized() {
        long ts = System.nanoTime();
        long supplierId = newSupplier(ts + "");
        long b1 = newBrand(ts + "_1");
        long b2 = newBrand(ts + "_2");

        supplierBrandService.assign(supplierId, List.of(b1, b2));
        assertThat(supplierBrandService.listBySupplier(supplierId)).hasSize(2);
        assertThat(supplierBrandService.isAuthorized(supplierId, b1)).isTrue();
        assertThat(supplierBrandService.isAuthorized(supplierId, b2)).isTrue();

        supplierBrandService.assign(supplierId, List.of(b1));
        assertThat(supplierBrandService.listBySupplier(supplierId)).hasSize(1);
        assertThat(supplierBrandService.isAuthorized(supplierId, b2)).isFalse();

        supplierBrandService.assign(supplierId, List.of(b1));
        assertThat(supplierBrandService.listBySupplier(supplierId)).hasSize(1);

        assertThat(supplierBrandService.existsByBrandId(b1)).isTrue();
    }

    @Test
    void unbind_blocked_when_products_exist() {
        long ts = System.nanoTime();
        long supplierId = newSupplier(ts + "");
        long brandId = newBrand(ts + "");
        supplierBrandService.assign(supplierId, List.of(brandId));

        SupplierProductSaveDTO sp = new SupplierProductSaveDTO();
        sp.setSupplierId(supplierId);
        sp.setBrandId(brandId);
        sp.setName("SB_Prod_" + ts);
        sp.setProductCode("SBC_" + ts);
        sp.setWholesalePrice(new BigDecimal("1"));
        sp.setRetailPrice(new BigDecimal("2"));
        sp.setMinPurchaseQty(1);
        sp.setStatus(1);
        supplierProductService.createSupplierProduct(sp);

        assertThatThrownBy(() -> supplierBrandService.assign(supplierId, List.of()))
                .isInstanceOf(BusinessException.class);
    }
}
