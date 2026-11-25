package com.hhs.service.push.channel;

import com.hhs.dto.AlertVO;
import com.hhs.mapper.UserPushConfigMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 转义回归（PUSH-8）。
 *
 * <p>企微与飞书两个渠道原来各写一份转义，且**两份都把 {@code &} 放到最后**：
 * 先 {@code <}→{@code &lt;}、{@code >}→{@code &gt;}，再把 {@code &}→{@code &amp;}，
 * 于是刚生成的实体被二次转义 —— {@code "心率>120 & 偏高"} 变成
 * {@code "心率&amp;gt;120 &amp; 偏高"}。
 *
 * <p>按复审意见，这一条应视为**潜在缺陷**而不是「用户看到乱码」：现有文案生成器
 * （{@code ThresholdEvaluator.formatTitle}、{@code MetricDisplayFormatter.formatAlertMessage}、
 * {@code AlertServiceImpl.defaultSuggestionFor}）产出的文本里没有裸的 {@code >}/{@code <}/{@code &}，
 * 所以线上基本触发不到。但它是真的、且只要将来有人把指标名或建议写成「血压>140」就会立刻可见。
 *
 * <p>断言落在**最终消息体**上而不是只测工具类：如果只有工具类被测，任何一个渠道改回自己的
 * 内联实现都不会被发现。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("推送渠道 — 转义顺序")
class PushChannelEscapingTest {

    @Mock
    private UserPushConfigMapper userPushConfigMapper;

    private AlertVO alertWithMarkup() {
        AlertVO alert = new AlertVO();
        alert.setAlertType("WARNING");
        alert.setAlertLevel("MEDIUM");
        alert.setTitle("心率>120 & 偏高");
        alert.setMessage("血压<90 的情况");
        alert.setSuggestion("请复查 & 咨询医生");
        alert.setMetricKey("heartRate");
        return alert;
    }

    // ---------------------------------------------------------------
    // 工具类本身
    // ---------------------------------------------------------------

    @Test
    @DisplayName("escapeMarkup：先转 & 再转尖括号，不出双重实体")
    void escapeMarkupOrdersAmpersandFirst() {
        assertEquals("a&gt;b &amp; c", PushTextEscaper.escapeMarkup("a>b & c"));
        assertEquals("a&lt;b &amp; c", PushTextEscaper.escapeMarkup("a<b & c"));
        // 已经是实体的输入会被当成字面量转义一次 —— 这是正确的：用户写的就是这些字符
        assertEquals("&amp;gt;", PushTextEscaper.escapeMarkup("&gt;"));
        assertEquals("", PushTextEscaper.escapeMarkup(null));
        assertEquals("", PushTextEscaper.escapeMarkup(""));
        // 无特殊字符时原样返回（不引入额外噪音）
        assertEquals("心率过高", PushTextEscaper.escapeMarkup("心率过高"));
    }

    // ---------------------------------------------------------------
    // 企微
    // ---------------------------------------------------------------

    private Map<String, Object> wecomContent(AlertVO alert) {
        WeComPushChannel channel = new WeComPushChannel(userPushConfigMapper, new org.springframework.web.client.RestTemplate());
        return (Map<String, Object>) ReflectionTestUtils.invokeMethod(channel, "buildMarkdownMessage", alert);
    }

    @Test
    @DisplayName("企微消息体里 &gt; 只出现一层（旧实现是 &amp;gt;）")
    void wecomDoesNotDoubleEscape() {
        Map<String, Object> message = wecomContent(alertWithMarkup());
        String content = String.valueOf(((Map<String, Object>) message.get("markdown")).get("content"));

        assertFalse(content.contains("&amp;gt;"), "出现双重转义: " + content);
        assertFalse(content.contains("&amp;lt;"), "出现双重转义: " + content);
        assertTrue(content.contains("&gt;"), "尖括号应被转义为单层实体: " + content);
        assertTrue(content.contains("&amp;"), "& 应被转义: " + content);
        // 标题原文里的 ">120" 必须变成 "&gt;120"，不是 "&amp;gt;120"
        assertTrue(content.contains("心率&gt;120"), content);
    }

    // ---------------------------------------------------------------
    // 飞书
    // ---------------------------------------------------------------

    private Map<String, Object> feishuCard(AlertVO alert) {
        FeishuPushChannel channel = new FeishuPushChannel(userPushConfigMapper, new org.springframework.web.client.RestTemplate());
        return (Map<String, Object>) ReflectionTestUtils.invokeMethod(channel, "buildCardMessage", alert);
    }

    @Test
    @DisplayName("飞书卡片体里 &gt; 只出现一层（旧实现是 &amp;gt;）")
    void feishuDoesNotDoubleEscape() {
        Map<String, Object> message = feishuCard(alertWithMarkup());
        String serialized = String.valueOf(message);

        assertFalse(serialized.contains("&amp;gt;"), "出现双重转义: " + serialized);
        assertFalse(serialized.contains("&amp;lt;"), "出现双重转义: " + serialized);
        assertTrue(serialized.contains("&gt;"), "尖括号应被转义为单层实体: " + serialized);
        assertTrue(serialized.contains("心率&gt;120"), serialized);
    }

    @Test
    @DisplayName("两个渠道对同一段文本给出同一份转义结果（共用实现）")
    void bothChannelsShareOneEscape() {
        String sample = "心率>120 & 偏高";
        String expected = PushTextEscaper.escapeMarkup(sample);

        String wecom = (String) ReflectionTestUtils.invokeMethod(
                new WeComPushChannel(userPushConfigMapper, new org.springframework.web.client.RestTemplate()), "escapeMarkdown", sample);
        String feishu = (String) ReflectionTestUtils.invokeMethod(
                new FeishuPushChannel(userPushConfigMapper, new org.springframework.web.client.RestTemplate()), "escapeText", sample);

        assertEquals(expected, wecom);
        assertEquals(expected, feishu);
    }
}