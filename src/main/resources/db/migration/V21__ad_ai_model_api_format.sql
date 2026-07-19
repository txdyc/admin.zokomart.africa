-- ===========================================================================
-- V21: ad_ai_model 增加接口形态 api_format。
--   CHAT  = OpenAI 兼容 chat/completions 多模态（nano-banana 类模型）
--   IMAGE = OpenAI Images API（/v1/images/generations 文生图、/v1/images/edits 图生图，
--           gpt-image-2 类模型，multipart 上传参考图）
--   存量行默认 CHAT，行为不变。
-- ===========================================================================
ALTER TABLE ad_ai_model
    ADD COLUMN api_format VARCHAR(16) NOT NULL DEFAULT 'CHAT'
        COMMENT '接口形态: CHAT=chat/completions 多模态; IMAGE=images generations/edits'
        AFTER model_code;
