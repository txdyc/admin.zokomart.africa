package africa.zokomart.admin.module.ad.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AdDiscardDTO {
    @NotEmpty
    private List<String> tempUrls;
}
