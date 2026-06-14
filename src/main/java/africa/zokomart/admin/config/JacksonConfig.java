package africa.zokomart.admin.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * 雪花算法生成的 Long 主键（ASSIGN_ID）数值远超 JavaScript 安全整数范围 (2^53-1)，
 * 前端 JSON.parse 会丢失精度，导致按 id 的增删改查命中错误记录。
 *
 * <p>这里只把「超出安全范围」的 Long 序列化为字符串；分页 total/current/size、
 * 种子小 ID 等仍按数字输出，避免前端分页/计数字段类型发生变化。
 */
@Configuration
public class JacksonConfig {

    /** JavaScript Number.MAX_SAFE_INTEGER */
    static final long MAX_SAFE_INTEGER = 9007199254740991L;
    static final long MIN_SAFE_INTEGER = -9007199254740991L;

    private static final JsonSerializer<Long> SAFE_LONG_SERIALIZER = new JsonSerializer<>() {
        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            if (value == null) {
                gen.writeNull();
            } else if (value > MAX_SAFE_INTEGER || value < MIN_SAFE_INTEGER) {
                gen.writeString(value.toString());
            } else {
                gen.writeNumber(value);
            }
        }
    };

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer bigNumberAsStringCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, SAFE_LONG_SERIALIZER);
            module.addSerializer(Long.TYPE, SAFE_LONG_SERIALIZER);
            // modulesToInstall 追加注册，保留 JavaTimeModule 等默认模块
            builder.modulesToInstall(module);
        };
    }
}
