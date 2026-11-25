package com.hhs.service.push.channel;

import com.hhs.dto.AlertVO;
import com.hhs.entity.UserPushConfig;
import com.hhs.mapper.UserPushConfigMapper;
import com.hhs.service.push.PushResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 企微 / 飞书 webhook 渠道的单测（PUSH-9）。
 *
 * <p>修复前 {@code service/push/channel/} 下只有 EmailPushChannelTest，企微与飞书零覆盖 ——
 * 而这两条通路的成败判定只靠子串嗅探：企微 {@code contains("\"errcode\":0")}、
 * 飞书 {@code contains("\"StatusCode\":0")}。子串对**空白与字段顺序**敏感，因此
 * {@code {"errcode": 0}}（冒号后带空格）或 pretty-print 的响应会被误判为失败，
 * 用户明明收到消息却在推送历史里看到 FAILED。
 *
 * <p>现在改为按 JSON 字段判定（{@link WebhookResponseEvaluator}），本类逐类钉住：
 * 成功 / 带空白的成功 / 错误码≠0 / 非 JSON / 超时异常 / URL 被 SSRF 校验拒 / 未配置。
 *
 * <p>URL 用公共 IP 字面量（{@code https://8.8.8.8/...}），不触发 DNS —— 与
 * {@code WebhookUrlValidatorTest} 同一做法。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("推送到 webhook 渠道 — 响应判定与失败分支")
class WebhookPushChannelTest {

    /** 通过 SSRF 校验的公共地址（IP 字面量，无需 DNS）。 */
    private static final String WEBHOOK = "https://8.8.8.8/open-apis/bot/v2/hook/test";

    @Mock
    private UserPushConfigMapper userPushConfigMapper;

    private RestTemplate restTemplate;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();

        UserPushConfig config = new UserPushConfig();
        config.setUserId(1L);
        config.setEnabled(1);
        config.setConfigValue(WEBHOOK);
        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "WECOM")).thenReturn(config);
        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "FEISHU")).thenReturn(config);
    }

    private AlertVO alert() {
        AlertVO alert = new AlertVO();
        alert.setAlertType("WARNING");
        alert.setAlertLevel("MEDIUM");
        alert.setTitle("心率异常");
        alert.setMessage("心率超过阈值");
        alert.setMetricKey("heartRate");
        return alert;
    }

    private WeComPushChannel wecom() {
        return new WeComPushChannel(userPushConfigMapper, restTemplate);
    }

    private FeishuPushChannel feishu() {
        return new FeishuPushChannel(userPushConfigMapper, restTemplate);
    }

    private void expectPost(String body) {
        server.expect(requestTo(WEBHOOK))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    // ---------------------------------------------------------------
    // 企微
    // ---------------------------------------------------------------

    @Test
    @DisplayName("企微：errcode=0 判成功，并真的 POST 到配置的 webhook")
    void wecomSuccess() {
        expectPost("{\"errcode\":0,\"errmsg\":\"ok\"}");

        PushResult result = wecom().push(1L, alert());

        assertTrue(result.isSuccess(), result.getMessage());
        server.verify();
    }

    @Test
    @DisplayName("企微：冒号后带空格的 {\"errcode\": 0} 也判成功（旧子串嗅探会误判失败）")
    void wecomSuccessWithWhitespace() {
        // MockRestServiceServer 回的是原样字节：这里刻意保留 JSON 里的空白，
        // 旧实现 contains("\"errcode\":0") 在这里会返回 false → 误记为 FAILED。
        expectPost("{ \"errcode\" : 0 , \"errmsg\" : \"ok\" }");

        PushResult result = wecom().push(1L, alert());

        assertTrue(result.isSuccess(), result.getMessage());
    }

    @Test
    @DisplayName("企微：errcode 非 0 判失败，并把 errmsg 带进失败原因")
    void wecomBusinessError() {
        expectPost("{\"errcode\":93000,\"errmsg\":\"invalid webhook url\"}");

        PushResult result = wecom().push(1L, alert());

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("93000"), result.getMessage());
        assertTrue(result.getMessage().contains("invalid webhook url"), result.getMessage());
    }

    @Test
    @DisplayName("企微：非 JSON 响应不能被当成成功")
    void wecomNonJsonResponseFails() {
        expectPost("<html>gateway error</html>");

        PushResult result = wecom().push(1L, alert());

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("unparseable"), result.getMessage());
    }

    @Test
    @DisplayName("企微：空响应体不能被当成成功")
    void wecomEmptyResponseFails() {
        expectPost("");

        PushResult result = wecom().push(1L, alert());

        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("企微：传输异常（超时/连接失败）返回 FAILED 且不抛给调用方")
    void wecomTransportFailureIsReported() {
        server.expect(requestTo(WEBHOOK))
                .andRespond(request -> {
                    throw new ResourceAccessException("Read timed out");
                });

        PushResult result = wecom().push(1L, alert());

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("timed out"), result.getMessage());
    }

    // ---------------------------------------------------------------
    // 飞书
    // ---------------------------------------------------------------

    @Test
    @DisplayName("飞书：StatusCode=0 判成功")
    void feishuSuccess() {
        expectPost("{\"StatusCode\":0,\"StatusMessage\":\"success\"}");

        PushResult result = feishu().push(1L, alert());

        assertTrue(result.isSuccess(), result.getMessage());
        server.verify();
    }

    @Test
    @DisplayName("飞书：空白改写后的 StatusCode 仍判成功")
    void feishuSuccessWithWhitespace() {
        expectPost("{ \"StatusCode\" : 0 , \"StatusMessage\" : \"success\" }");

        assertTrue(feishu().push(1L, alert()).isSuccess());
    }

    @Test
    @DisplayName("飞书：新版 {code:0} 形态也能判成功（StatusCode 缺失时回退到 code）")
    void feishuAcceptsNewCodeShape() {
        expectPost("{\"code\":0,\"msg\":\"success\"}");

        assertTrue(feishu().push(1L, alert()).isSuccess());
    }

    @Test
    @DisplayName("飞书：StatusCode 非 0 判失败并带上 StatusMessage")
    void feishuBusinessError() {
        expectPost("{\"StatusCode\":19002,\"StatusMessage\":\"sign match fail\"}");

        PushResult result = feishu().push(1L, alert());

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("19002"), result.getMessage());
        assertTrue(result.getMessage().contains("sign match fail"), result.getMessage());
    }

    @Test
    @DisplayName("飞书：非 JSON 响应不能被当成成功")
    void feishuNonJsonResponseFails() {
        expectPost("not json at all");

        assertFalse(feishu().push(1L, alert()).isSuccess());
    }

    // ---------------------------------------------------------------
    // 共用前置分支
    // ---------------------------------------------------------------

    @Test
    @DisplayName("两个渠道：未配置 webhook 时返回 SKIPPED，且一个请求都不发")
    void unconfiguredWebhookSkips() {
        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "WECOM")).thenReturn(null);
        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "FEISHU")).thenReturn(null);

        PushResult wecomResult = wecom().push(1L, alert());
        PushResult feishuResult = feishu().push(1L, alert());

        assertFalse(wecomResult.isSuccess());
        assertFalse(feishuResult.isSuccess());
        assertEquals("Skipped: Webhook not configured", wecomResult.getMessage());
        assertEquals("Skipped: Webhook not configured", feishuResult.getMessage());
        // 没有登记任何期望，verify 通过即证明没有发出请求
        server.verify();
    }

    @Test
    @DisplayName("两个渠道：指向内网的 URL 被 SSRF 校验拒，不发请求")
    void privateAddressIsRejectedBeforePosting() {
        UserPushConfig internal = new UserPushConfig();
        internal.setUserId(1L);
        internal.setEnabled(1);
        internal.setConfigValue("https://127.0.0.1/internal");
        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "WECOM")).thenReturn(internal);
        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "FEISHU")).thenReturn(internal);

        PushResult wecomResult = wecom().push(1L, alert());
        PushResult feishuResult = feishu().push(1L, alert());

        assertFalse(wecomResult.isSuccess());
        assertEquals("Webhook URL rejected", wecomResult.getMessage());
        assertEquals("Webhook URL rejected", feishuResult.getMessage());
        server.verify();
    }

    @Test
    @DisplayName("服务器级默认 webhook（push.wecom/feishu.webhook-url）确实会被用上")
    void defaultWebhookIsUsedWhenUserHasNoConfig() {
        when(userPushConfigMapper.findByUserIdAndChannelType(1L, "WECOM")).thenReturn(null);
        WeComPushChannel channel = wecom();
        // 这两个 key 在 yml/compose/.env.example 里都没有定义，默认恒为空。
        // 这里用反射注入，证明「代码支持默认值」与「配置没人提供」是两件事。
        ReflectionTestUtils.setField(channel, "defaultWebhookUrl", WEBHOOK);

        expectPost("{\"errcode\":0}");

        assertTrue(channel.push(1L, alert()).isSuccess());
        server.verify();
    }
}