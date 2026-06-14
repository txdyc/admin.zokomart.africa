package africa.zokomart.admin.common.file;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.config.UploadProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 本地文件系统存储实现。校验类型/大小后按 category 落盘，返回相对 URL。
 */
@Service
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {

    private final UploadProperties props;
    private static final Pattern CATEGORY = Pattern.compile("[a-z0-9_-]+");

    @Override
    public String store(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.FILE_EMPTY);
        }
        if (file.getSize() > props.getMaxSize()) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE);
        }
        String cat = (category != null && CATEGORY.matcher(category).matches()) ? category : "common";
        String ext = extOf(file.getOriginalFilename());
        if (!props.getAllowedTypes().contains(ext)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Path dir = Paths.get(props.getDir(), cat).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename));
        } catch (IOException e) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "文件写入失败");
        }
        return props.getUrlPrefix() + "/" + cat + "/" + filename;
    }

    private String extOf(String name) {
        if (name == null) {
            return "";
        }
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1).toLowerCase(Locale.ROOT) : "";
    }
}
