package africa.zokomart.admin.common.file;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.config.UploadProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageServiceTest {

    @TempDir
    Path tmp;
    LocalFileStorageService svc;

    @BeforeEach
    void setUp() {
        UploadProperties p = new UploadProperties();
        p.setDir(tmp.toString());
        p.setUrlPrefix("/files");
        p.setMaxSize(1024);
        p.setAllowedTypes(List.of("png", "jpg"));
        svc = new LocalFileStorageService(p);
    }

    @Test
    void stores_png_and_returns_url() {
        MockMultipartFile f = new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1, 2, 3});
        String url = svc.store(f, "brand");
        assertThat(url).startsWith("/files/brand/").endsWith(".png");
        String name = url.substring("/files/brand/".length());
        assertThat(Files.exists(tmp.resolve("brand").resolve(name))).isTrue();
    }

    @Test
    void rejects_disallowed_type() {
        MockMultipartFile f = new MockMultipartFile("file", "x.txt", "text/plain", new byte[]{1});
        assertThatThrownBy(() -> svc.store(f, "brand")).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejects_oversize() {
        MockMultipartFile f = new MockMultipartFile("file", "big.png", "image/png", new byte[2048]);
        assertThatThrownBy(() -> svc.store(f, "brand")).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejects_empty() {
        MockMultipartFile f = new MockMultipartFile("file", "e.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> svc.store(f, "brand")).isInstanceOf(BusinessException.class);
    }

    @Test
    void falls_back_to_common_for_bad_category() {
        MockMultipartFile f = new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1});
        String url = svc.store(f, "../evil");
        assertThat(url).startsWith("/files/common/");
    }
}
