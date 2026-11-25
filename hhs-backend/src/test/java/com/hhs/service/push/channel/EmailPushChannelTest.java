package com.hhs.service.push.channel;

import com.hhs.dto.AlertVO;
import com.hhs.service.push.PushResult;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

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

    // ============================================================
    // 失败文案的可诊断性（PUSH-1）
    //
    // 配置问题本身不在代码里修：SMTP 535 是服务端拒绝凭据，只能由使用者换授权码。
    // 但代码侧原本把 Spring 硬编码的 "Authentication failed" 当作唯一信息记进
    // push_history，把服务器给出的 535 原文（"Login fail. Account is abnormal, ..."）
    // 连同 cause 一起丢掉了 —— 三种完全不同的处置（换码 / 开服务 / 等频率限制）
    // 在界面上长得一模一样。下面两条钉住「cause 必须被带出来」。
    // ============================================================

    private String describe(Throwable error) {
        return (String) ReflectionTestUtils.invokeMethod(channel, "describeFailure", error);
    }

    @Test
    @DisplayName("Authentication failed 必须带上服务器 535 原文（Spring 把它藏在 cause 里）")
    void describeFailureKeepsSmtpReplyFromCause() {
        // Spring 的真实形态：MailAuthenticationException(Throwable) 的 message 是硬编码字面量
        // "Authentication failed"（javap 确认 ldc #10），服务器答复只在 cause 上。
        Throwable serverReply = new jakarta.mail.AuthenticationFailedException(
                "535 Login fail. Account is abnormal, service is not open, "
                        + "password is incorrect, login frequency limited, or system is busy.");
        org.springframework.mail.MailAuthenticationException error =
                new org.springframework.mail.MailAuthenticationException(serverReply);

        String described = describe(error);

        assertTrue(described.startsWith("Authentication failed"),
                "仍应以 Spring 的措辞起头，便于既有匹配规则（PushSettings.getFailureHint）识别: " + described);
        assertTrue(described.contains("535"),
                "必须保留服务器返回码，否则无法区分换码 / 开服务 / 频率限制: " + described);
        assertTrue(described.contains("login frequency limited"),
                "535 原文的各类原因都要完整保留: " + described);
    }

    @Test
    @DisplayName("没有 cause 时不追加空括号，也不重复同一句话")
    void describeFailureLeavesPlainMessagesAlone() {
        // 非认证类失败不追加处置建议（对它们编建议就是猜），原文原样保留
        assertEquals("Unknown host smtp.example.com", describe(new jakarta.mail.MessagingException("Unknown host smtp.example.com")));

        // cause 的 message 与自身相同（某些 socket 异常会这样）时不得拼成 "x (x)"
        String message = "Connection refused";
        assertEquals(message, describe(new RuntimeException(message, new RuntimeException(message))));
    }

    @Test
    @DisplayName("认证失败补一条可操作的中文处置（PUSH-2）")
    void describeFailureAddsActionableHintForAuthFailure() {
        Throwable serverReply = new jakarta.mail.AuthenticationFailedException(
                "535 Login fail. Account is abnormal, service is not open, password is incorrect, "
                        + "login frequency limited, or system is busy.");
        String described = describe(new org.springframework.mail.MailAuthenticationException(serverReply));

        // 用户需要知道「改哪个环境变量、用什么凭据」；只说 Authentication failed 等于没说
        assertTrue(described.contains("SMTP 认证失败"), described);
        assertTrue(described.contains("MAIL_USERNAME"), described);
        assertTrue(described.contains("MAIL_PASSWORD"), described);
        assertTrue(described.contains("授权码"), described);
        // 服务器原文仍在，处置建议只是追加在后面
        assertTrue(described.contains("535"), described);
    }

    @Test
    @DisplayName("非认证失败不补认证建议（避免把连接问题误标成凭据问题）")
    void describeFailureDoesNotHintAuthForOtherFailures() {
        // 主机名/文本里都带 auth，但它不是认证失败 —— 分类只看异常类型，不看字面
        String described = describe(new jakarta.mail.MessagingException(
                "Could not connect to host \"smtp.auth.example.com\": connection refused"));

        assertFalse(described.contains("MAIL_PASSWORD"),
                "连接失败不该建议去改授权码: " + described);
        assertTrue(described.contains("connection refused"), described);
    }

    @Test
    @DisplayName("MailSendException 的子异常挂在 messageExceptions 上（cause 为 null）也要被带出")
    void describeFailureReadsMailSendExceptionMessageExceptions() {
        // javap 确认：doSend 消息被拒时抛 MailSendException(Map)，三参构造器把 cause 置 null，
        // 子异常只存在于 messageExceptions。只沿 getCause() 走会什么都看不到。
        Exception rejection = new jakarta.mail.MessagingException(
                "550 Mail content denied / 550 Invalid user");
        org.springframework.mail.MailSendException error = new org.springframework.mail.MailSendException(
                java.util.Map.of(new Object(), rejection));

        String described = describe(error);

        assertTrue(described.contains("550"), "被拒收的 SMTP 原文必须出现: " + described);
    }

    @Test
    @DisplayName("失败文案被限制在 push_history.message 的长度内")
    void describeFailureIsCapped() {
        Throwable longCause = new jakarta.mail.AuthenticationFailedException("5".repeat(900));
        String described = describe(new org.springframework.mail.MailAuthenticationException(longCause));

        // 列是 VARCHAR(500)，留出余量；超长时截断并带省略号，而不是让 insert 在库里失败/截半
        assertTrue(described.length() <= 480, "描述过长: " + described.length());
        assertTrue(described.endsWith("...") || described.length() <= 480);
    }

    @Test
    @DisplayName("推送失败时把带 cause 的描述放进 PushResult（不是裸 getMessage）")
    void pushFailureCarriesDiagnosableMessage() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setNickname("Alice");
        user.setEmail("alice@example.com");
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "EMAIL")).thenReturn(null);
        // fromEmail 来自 @Value("${spring.mail.username:}")，单测里没启 Spring 上下文时是 null，
        // MimeMessageHelper 会先在 setFrom 处抛 "From address must not be null"，就测不到 AUTH 路径了。
        ReflectionTestUtils.setField(channel, "fromEmail", "hhs@example.com");

        // 必须给一个真实的 MimeMessage：mock 的 createMimeMessage() 返回 null，会让失败停在
        // 「mimeMessage is null」而不是真正的 AUTH 错误上，断言就测不到想测的东西。
        jakarta.mail.Session session = jakarta.mail.Session.getInstance(new java.util.Properties());
        when(mailSender.createMimeMessage()).thenReturn(new jakarta.mail.internet.MimeMessage(session));

        Throwable serverReply = new jakarta.mail.AuthenticationFailedException(
                "535 Login fail. Account is abnormal, service is not open, password is incorrect, "
                        + "login frequency limited, or system is busy.");
        doThrow(new org.springframework.mail.MailAuthenticationException(serverReply))
                .when(mailSender).send(any(jakarta.mail.internet.MimeMessage.class));

        PushResult result = channel.push(1L, baseAlert());

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("535"),
                "push_history 与测试推送响应里必须能看到服务器原文: " + result.getMessage());
    }
}
