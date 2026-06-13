package africa.zokomart.admin.common.base;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/** 业务单号生成：前缀 + yyyyMMddHHmmssSSS + 3 位随机，满足后台单据的唯一性需求。 */
public final class BizNo {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private BizNo() {
    }

    public static String gen(String prefix) {
        return prefix + LocalDateTime.now().format(FMT) + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }
}
