package africa.zokomart.admin.module.wcsync.client;

/** WC 商品图引用：id（已有媒体，不重传）或 src（让 WC sideload），二选一非空。 */
public record WcImage(Long id, String src) { }
