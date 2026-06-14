package africa.zokomart.admin.module.file.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileUploadVO {
    /** 对外可访问的相对路径，如 /files/brand/{uuid}.png */
    private String url;
}
