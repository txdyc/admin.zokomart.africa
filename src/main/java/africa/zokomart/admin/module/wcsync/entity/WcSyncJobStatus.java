package africa.zokomart.admin.module.wcsync.entity;

/** 同步任务状态常量。 */
public final class WcSyncJobStatus {
    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String PARTIAL = "PARTIAL";
    public static final String FAILED = "FAILED";
    public static final String INTERRUPTED = "INTERRUPTED";

    private WcSyncJobStatus() {}
}
