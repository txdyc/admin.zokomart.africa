package africa.zokomart.admin.module.system.init;

import africa.zokomart.admin.module.system.entity.SysUser;
import africa.zokomart.admin.module.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动时确保存在一个超级管理员账号（幂等）。
 * 默认账号 superadmin / Admin@123，首次登录后应尽快修改密码。
 * 用代码初始化而非 SQL 种子，避免在迁移脚本中手写不可复现的 BCrypt 密文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminInitializer implements ApplicationRunner {

    private static final String DEFAULT_USERNAME = "superadmin";
    private static final String DEFAULT_PASSWORD = "Admin@123";

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        Long count = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getIsSuper, 1));
        if (count != null && count > 0) {
            return;
        }
        SysUser admin = new SysUser();
        admin.setUsername(DEFAULT_USERNAME);
        admin.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        admin.setNickname("Super Admin");
        admin.setStatus(1);
        admin.setIsSuper(1);
        admin.setRemark("system bootstrap super admin");
        userMapper.insert(admin);
        log.warn("Bootstrap super admin created: username={} (please change the default password)", DEFAULT_USERNAME);
    }
}
