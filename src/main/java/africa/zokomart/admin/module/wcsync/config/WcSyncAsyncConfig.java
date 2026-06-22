package africa.zokomart.admin.module.wcsync.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** WooCommerce 同步异步执行：单线程串行，避免对 WC 并发请求触发限流。 */
@Configuration
@EnableAsync
public class WcSyncAsyncConfig {

    @Bean("wcSyncExecutor")
    public Executor wcSyncExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(1);
        ex.setQueueCapacity(50);
        ex.setThreadNamePrefix("wc-sync-");
        ex.initialize();
        return ex;
    }
}
