package africa.zokomart.admin.module.raworder.controller;

import africa.zokomart.admin.common.result.PageResult;
import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.raworder.service.RawOrderImportService;
import africa.zokomart.admin.module.raworder.service.RawOrderService;
import africa.zokomart.admin.module.raworder.vo.RawOrderImportResultVO;
import africa.zokomart.admin.module.raworder.vo.RawOrderVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@Tag(name = "原始订单")
public class RawOrderController {

    private final RawOrderService rawOrderService;
    private final RawOrderImportService rawOrderImportService;

    @GetMapping("/api/raw-orders")
    @SaCheckPermission("raw-order:list")
    public Result<PageResult<RawOrderVO>> page(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEnd,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(rawOrderService.page(dateStart, dateEnd, status, brand, keyword, current, size));
    }

    @PostMapping("/api/raw-orders/import")
    @SaCheckPermission("raw-order:import")
    public Result<RawOrderImportResultVO> importCsv(@RequestParam("file") MultipartFile file) {
        return Result.ok(rawOrderImportService.importCsv(file));
    }
}
