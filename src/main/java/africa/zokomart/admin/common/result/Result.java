package africa.zokomart.admin.common.result;

import lombok.Data;

/**
 * 统一响应包装。所有接口出口均返回 Result。
 * 字段：code 业务码 / msg 提示 / data 数据。
 */
@Data
public class Result<T> {

    private int code;
    private String msg;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = ResultCode.SUCCESS.getCode();
        r.msg = ResultCode.SUCCESS.getMsg();
        r.data = data;
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(int code, String msg) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        return r;
    }

    public static <T> Result<T> fail(ResultCode rc) {
        return fail(rc.getCode(), rc.getMsg());
    }
}
