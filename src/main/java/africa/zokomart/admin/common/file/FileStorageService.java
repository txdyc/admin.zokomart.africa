package africa.zokomart.admin.common.file;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储抽象。当前本地实现；日后可替换为对象存储（MinIO/S3/OSS）而不动调用方。
 */
public interface FileStorageService {

    /**
     * 存储文件，返回对外可访问的相对路径（如 /files/brand/{uuid}.png）。
     *
     * @param file     上传文件
     * @param category 业务分类（决定子目录），非法时回退为 common
     */
    String store(MultipartFile file, String category);
}
