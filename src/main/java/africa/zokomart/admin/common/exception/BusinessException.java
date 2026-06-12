package africa.zokomart.admin.common.exception;

import africa.zokomart.admin.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常。由 service 层在业务规则不满足时抛出，统一由 GlobalExceptionHandler 转为 Result。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public BusinessException(ResultCode rc) {
        super(rc.getMsg());
        this.code = rc.getCode();
    }

    public BusinessException(ResultCode rc, String msg) {
        super(msg);
        this.code = rc.getCode();
    }
}
