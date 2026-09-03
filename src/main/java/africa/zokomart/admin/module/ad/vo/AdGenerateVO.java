package africa.zokomart.admin.module.ad.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AdGenerateVO {
    private List<String> tempUrls;
    private List<String> errors;
}
