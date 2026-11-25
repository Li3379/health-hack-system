package com.hhs.service.push.channel;

/**
 * 推送渠道共用的文本转义。
 *
 * <p>为什么单独抽出来：企微（markdown）与飞书（lark_md）都要把告警标题/建议拼进各自的消息体，
 * 两个渠道原来各写一份 {@code escapeMarkdown} / {@code escapeText}，且**两份都写错了顺序** ——
 * 先替换 {@code <}、{@code >} 生成 {@code &lt;} / {@code &gt;}，最后才替换 {@code &}，
 * 于是刚生成的实体里的 {@code &} 被二次转义：
 * {@code "心率>120 & 偏高"} → {@code "心率&amp;gt;120 &amp; 偏高"}。用户看到的是实体而非字符。
 *
 * <p>转义必须**先处理 {@code &}**，否则后续替换生成的 {@code &} 会被自己的反斜杠吃掉。
 * 把这条规则收在一处，就不会出现「一处修好另一处忘掉」。
 *
 * <p>注意：这是 HTML/Markdown 语义的转义（{@code &} / {@code <} / {@code >}），
 * 不含引号 —— 企微与飞书的消息体不是 HTML 属性上下文，转义引号只会让正文里多出
 * {@code &quot;} 噪音。
 */
final class PushTextEscaper {

    private PushTextEscaper() {
        // 工具类
    }

    /**
     * 转义 {@code &}、{@code <}、{@code >}，顺序不可调换。
     *
     * @param input 原始文本，可为 null
     * @return 转义后的文本；null 输入返回空串（与原先各渠道的行为一致）
     */
    static String escapeMarkup(String input) {
        if (input == null) {
            return "";
        }
        return input
                // & 必须最先，否则下面生成的 &lt; / &gt; 里的 & 会被再次转义
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}