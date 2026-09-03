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

    /** 存储原始字节（如 AI 生成图），返回公开相对路径。不做类型/大小校验（服务端自产数据）。 */
    String storeBytes(byte[] data, String category, String ext);

    /** 把公开相对路径（/files/{cat}/{name}）反解为本地绝对路径；前缀不符或归一化后越出上传目录抛 AD_INVALID_TEMP_URL。 */
    java.nio.file.Path resolvePublicUrl(String publicUrl);
}
