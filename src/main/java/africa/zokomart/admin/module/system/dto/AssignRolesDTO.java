package africa.zokomart.admin.module.system.dto;

import lombok.Data;

import java.util.List;

@Data
public class AssignRolesDTO {
    private List<Long> roleIds;
}
