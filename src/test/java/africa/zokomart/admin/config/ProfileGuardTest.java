package africa.zokomart.admin.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纯单元测试：不起 Spring 上下文，不碰 MySQL/Redis。
 */
class ProfileGuardTest {

    @Test
    void prod_with_local_fails_fast() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ProfileGuard.verify(new String[]{"prod", "local"}));
        assertTrue(ex.getMessage().contains("prod"));
        assertTrue(ex.getMessage().contains("local"));
    }

    @Test
    void order_does_not_matter() {
        assertThrows(IllegalStateException.class,
                () -> ProfileGuard.verify(new String[]{"local", "prod"}));
    }

    @Test
    void prod_alone_is_fine() {
        assertDoesNotThrow(() -> ProfileGuard.verify(new String[]{"prod"}));
    }

    @Test
    void dev_with_local_is_the_normal_dev_setup() {
        assertDoesNotThrow(() -> ProfileGuard.verify(new String[]{"dev", "local"}));
    }

    @Test
    void no_active_profile_is_fine() {
        assertDoesNotThrow(() -> ProfileGuard.verify(new String[]{}));
    }
}
