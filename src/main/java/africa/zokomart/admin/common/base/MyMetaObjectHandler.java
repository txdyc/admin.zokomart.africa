package africa.zokomart.admin.common.base;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充。createBy/updateBy 取当前登录用户；未登录场景（如初始化）填 0。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    private Long currentUserId() {
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long uid = currentUserId();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "createBy", Long.class, uid);
        this.strictInsertFill(metaObject, "updateBy", Long.class, uid);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updateBy", Long.class, currentUserId());
    }
}
