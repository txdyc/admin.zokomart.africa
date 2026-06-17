package africa.zokomart.admin.module.customer.dto;

import lombok.Data;

@Data
public class CustomerQuery {
    private String keyword;
    private long current = 1;
    private long size = 10;
}
