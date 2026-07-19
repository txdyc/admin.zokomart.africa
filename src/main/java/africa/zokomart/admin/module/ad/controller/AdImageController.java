package africa.zokomart.admin.module.ad.controller;

import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.ad.dto.AdGenerateDTO;
import africa.zokomart.admin.module.ad.service.AdImageService;
import africa.zokomart.admin.module.ad.vo.AdGenerateVO;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ad/images")
@RequiredArgsConstructor
@Tag(name = "广告-AI 生图")
public class AdImageController {

    private final AdImageService service;

    @PostMapping("/generate")
    @SaCheckPermission("ad:image:generate")
    public Result<AdGenerateVO> generate(@Valid @RequestBody AdGenerateDTO dto) {
        return Result.ok(service.generate(dto));
    }
}
