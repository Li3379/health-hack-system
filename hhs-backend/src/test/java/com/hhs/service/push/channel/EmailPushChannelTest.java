package com.hhs.service.push.channel;

import com.hhs.dto.AlertVO;
import com.hhs.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import com.hhs.mapper.UserMapper;
import com.hhs.mapper.UserPushConfigMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Email body regression guard for {@link EmailPushChannel}.
 *
 * <p>Asserts on the generated HTML directly rather than round-tripping a {@code MimeMessage}:
 * the latter is multipart and the parse dance obscures which block actually rendered. The
 * thing S4 changed is one string builder, so the string builder is what we pin.</p>
 *
 * <p>Two properties:</p>
 * <ul>
 *   <li>the health-suggestion section still renders — deleting one block must not take its
 *       neighbour with it;</li>
 *   <li>the AI-analysis section is gone. Nothing calls {@code setAiAnalysis}, so restoring the
 *       block without a writer would resurrect dead code that looks alive.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Email Push Channel — 正文渲染")
class EmailPushChannelTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserPushConfigMapper userPushConfigMapper;

    @InjectMocks
    private EmailPushChannel channel;

    private String render(AlertVO alert) {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setNickname("Alice");
        user.setEmail("alice@example.com");

        // buildHtmlContent is the exact method S4 edited; calling it keeps the assertion
        // one hop away from the change instead of three (send -> MimeMessage -> multipart).
        return (String) ReflectionTestUtils.invokeMethod(channel, "buildHtmlContent", alert, user);
    }

    private AlertVO baseAlert() {
        AlertVO alert = new AlertVO();
        alert.setAlertType("WARNING");
        alert.setAlertLevel("MEDIUM");
        alert.setTitle("心率异常");
        alert.setMessage("您的心率超过了预设阈值");
        alert.setMetricKey("heartRate");
        return alert;
    }

    @Test
    @DisplayName("有建议时渲染「健康建议」区块 —— 删 AI 块不能连带删掉它")
    void rendersSuggestionBlock() {
        AlertVO alert = baseAlert();
        alert.setSuggestion("请适当休息并监测心率");

        String html = render(alert);

        assertTrue(html.contains("健康建议"), "缺少建议区块标题: " + html);
        assertTrue(html.contains("请适当休息并监测心率"), "缺少建议正文: " + html);
    }

    @Test
    @DisplayName("正文不再包含「AI分析」区块 —— 该分支已无写入方")
    void doesNotRenderAiAnalysisBlock() {
        AlertVO alert = baseAlert();
        alert.setSuggestion("请适当休息并监测心率");
        // 必须显式赋值：该块原本的守卫是 `aiAnalysis != null && !isEmpty`，不赋值的话
        // 「删了块」和「块还在但字段为 null」看起来一样，这条断言就什么都钉不住。
        alert.setAiAnalysis("模型推断：近期心率偏高与睡眠不足相关");

        String html = render(alert);

        assertFalse(html.contains("AI分析"), "「AI分析」应为死代码，实际仍渲染: " + html);
        assertFalse(html.contains("模型推断"), "aiAnalysis 正文不得出现在邮件里: " + html);
    }

    @Test
    @DisplayName("无建议时两块都不渲染，正文仍然完整")
    void rendersWithoutOptionalBlocks() {
        String html = render(baseAlert());

        assertFalse(html.contains("健康建议"));
        assertFalse(html.contains("AI分析"));
        assertTrue(html.contains("健康预警通知"));
        assertTrue(html.contains("此邮件由HHS健康管理系统自动发送"));
    }
}
