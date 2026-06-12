package africa.zokomart.admin.common.security;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 当前登录用户辅助方法。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** 当前登录用户 id。未登录会抛 Sa-Token 异常（由全局异常处理转 401）。 */
    public static Long getUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    public static boolean isLogin() {
        return StpUtil.isLogin();
    }
}
