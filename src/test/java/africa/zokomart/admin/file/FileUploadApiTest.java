package africa.zokomart.admin.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 文件上传接口集成测试：登录后上传图片成功、拒非图片、匿名 401。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.upload.dir=target/test-uploads")
class FileUploadApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"superadmin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/token").asText();
    }

    @Test
    void upload_png_returns_url() throws Exception {
        String t = token();
        MockMultipartFile f = new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1, 2, 3, 4});
        mvc.perform(multipart("/api/files/upload").file(f)
                        .param("category", "brand")
                        .header("Authorization", t))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.url").value(Matchers.startsWith("/files/brand/")));
    }

    @Test
    void reject_non_image() throws Exception {
        String t = token();
        MockMultipartFile f = new MockMultipartFile("file", "x.txt", "text/plain", new byte[]{1});
        mvc.perform(multipart("/api/files/upload").file(f).header("Authorization", t))
                .andExpect(jsonPath("$.code").value(40005));
    }

    @Test
    void reject_anonymous() throws Exception {
        MockMultipartFile f = new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1});
        mvc.perform(multipart("/api/files/upload").file(f))
                .andExpect(jsonPath("$.code").value(401));
    }
}
