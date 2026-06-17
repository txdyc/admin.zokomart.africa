package africa.zokomart.admin.module.customer.controller;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.customer.service.CustomerService;
import africa.zokomart.admin.module.customer.vo.CustomerVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "客户管理")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/api/customers")
    @SaCheckPermission("customer:list")
    public Result<PageResult<CustomerVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(customerService.page(keyword, current, size));
    }
}
