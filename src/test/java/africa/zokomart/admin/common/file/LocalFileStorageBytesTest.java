package africa.zokomart.admin.common.file;

import africa.zokomart.admin.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LocalFileStorageBytesTest {

    @Autowired FileStorageService storage;

    @Test
    void storeBytes_then_resolve_roundtrip() throws Exception {
        String url = storage.storeBytes(new byte[]{1, 2, 3}, "ad-temp", "png");
        assertTrue(url.startsWith("/files/ad-temp/") && url.endsWith(".png"));
        Path p = storage.resolvePublicUrl(url);
        assertTrue(Files.exists(p));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(p));
        Files.deleteIfExists(p);   // 清理
    }

    @Test
    void resolve_rejects_traversal_and_foreign_prefix() {
        assertThrows(BusinessException.class,
                () -> storage.resolvePublicUrl("/files/ad-temp/../../etc/passwd"));
        assertThrows(BusinessException.class,
                () -> storage.resolvePublicUrl("http://evil/x.png"));
        assertThrows(BusinessException.class,
                () -> storage.resolvePublicUrl("/other/ad-temp/x.png"));
    }
}
