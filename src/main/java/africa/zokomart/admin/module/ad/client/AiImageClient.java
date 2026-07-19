package africa.zokomart.admin.module.ad.client;

import africa.zokomart.admin.module.ad.entity.AdAiModel;

import java.nio.file.Path;
import java.util.List;

public interface AiImageClient {

    /**
     * 调一次模型生图（一次调用可能返回多张）。
     * refImages 为本地参考图路径（读为 base64 data-URI 随多模态消息发送）。
     * 网络失败/无图返回 抛 BusinessException(AD_GENERATE_FAILED)。
     */
    List<byte[]> generate(AdAiModel model, String prompt, List<Path> refImages);
}
