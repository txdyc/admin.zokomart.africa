package africa.zokomart.admin.module.file.controller;

import africa.zokomart.admin.common.file.FileStorageService;
import africa.zokomart.admin.common.result.Result;
import africa.zokomart.admin.module.file.vo.FileUploadVO;
import cn.dev33.satoken.annotation.SaCheckLogin;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传：任意登录用户可用（不绑定具体按钮权限），供各业务模块复用。
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "文件上传")
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping("/upload")
    @SaCheckLogin
    public Result<FileUploadVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "common") String category) {
        return Result.ok(new FileUploadVO(fileStorageService.store(file, category)));
    }
}
