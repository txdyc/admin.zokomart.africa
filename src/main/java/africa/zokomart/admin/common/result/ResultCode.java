package africa.zokomart.admin.common.result;

import lombok.Getter;

/**
 * 统一业务状态码。0 表示成功，其余为各类业务错误码。
 * 业务码按域分段（4xxxx 为各业务域细化错误），便于前端识别处理。
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "success"),
    BAD_REQUEST(400, "bad request"),
    UNAUTHORIZED(401, "unauthorized"),
    FORBIDDEN(403, "forbidden"),
    NOT_FOUND(404, "not found"),
    BUSINESS_ERROR(500, "business error"),

    // 业务域细化错误码
    INSUFFICIENT_STOCK(40001, "库存不足"),
    BELOW_MIN_PURCHASE_QTY(40002, "低于最小采购量"),
    INVALID_STATUS_TRANSITION(40003, "非法的状态流转"),

    // 文件上传
    FILE_EMPTY(40004, "上传文件为空"),
    FILE_TYPE_NOT_ALLOWED(40005, "不支持的文件类型"),
    FILE_TOO_LARGE(40006, "文件超过大小限制"),

    // 供应商-品牌授权
    BRAND_NOT_AUTHORIZED(40007, "该品牌未对此供应商授权"),
    BRAND_IN_USE(40008, "该品牌下已有供应商产品，无法取消授权");

    private final int code;
    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
