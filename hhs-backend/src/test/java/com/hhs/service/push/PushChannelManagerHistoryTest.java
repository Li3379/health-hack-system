package com.hhs.service.push;

import com.hhs.dto.AlertVO;
import com.hhs.service.PushHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code pushAll} 的留痕契约（PUSH-4）。
 *
 * <p>修复前 {@code pushAll} 先用 {@code isAvailable} 预过滤，未通过过滤的渠道**根本不进入
 * push()**，因此不写任何 push_history 行。结果是「用户没配邮箱」与「系统压根没产生告警」
 * 在界面上完全一样 —— 推送历史只剩「暂无推送记录」。
 *
 * <p>但复审指出朴素修法会引入新噪声：{@code WebSocketPushChannel.isAvailable} 在用户未连接
 * 时返回 false，而 {@code pushAll} 对**每条告警**都会过一遍所有渠道 —— 若一律补记 SKIPPED，
 * 离线用户的每次告警都会堆一行 WEBSOCKET SKIPPED，把历史淹掉。
 *
 * <p>因此区分两件事：<em>从未配置</em>（该留痕）与<em>配置了但此刻不可达</em>（不该留痕）。
 * 下面四条分别钉住这两侧及其组合。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PushChannelManager — pushAll 的 SKIPPED 留痕")
class PushChannelManagerHistoryTest {

    @Mock
    private PushHistoryService pushHistoryService;

    private PushChannelManager manager;

    private static final Long USER_ID = 7L;

    @BeforeEach
    void setUp() {
        manager = new PushChannelManager();
        manager.setPushHistoryService(pushHistoryService);
    }

    private AlertVO alert() {
        AlertVO alert = new AlertVO();
        alert.setId(100L);
        alert.setTitle("心率异常");
        alert.setAlertLevel("MEDIUM");
        return alert;
    }

    /**
     * 一个可控渠道：可用性、是否配置、以及 push 是否真的被调用，三者独立设置。
     *
     * <p>独立设置是关键 —— 修复前「不可用」与「push 未被调用」是同一个事实；现在必须能
     * 分别断言「push 被调用了」与「没有写历史」。
     */
    private static final class StubChannel implements PushChannel {
        private final ChannelType type;
        private final boolean configured;
        private int pushCalls;
        private PushResult result;

        StubChannel(ChannelType type, boolean configured, PushResult result) {
            this.type = type;
            this.configured = configured;
            this.result = result;
        }

        @Override
        public PushResult push(Long userId, AlertVO alert) {
            pushCalls++;
            return result;
        }

        @Override
        public ChannelType getChannelType() {
            return type;
        }

        @Override
        public boolean isAvailable(Long userId) {
            // 配置了才可能可达：这与真实实现的方向一致
            return configured;
        }

        @Override
        public boolean isConfigured(Long userId) {
            return configured;
        }
    }

    /** 配置了但此刻不可达（如 WebSocket 未连接）：isAvailable=false，isConfigured=true。 */
    private static final class UnreachableButConfiguredChannel implements PushChannel {
        int pushCalls;

        @Override
        public PushResult push(Long userId, AlertVO alert) {
            pushCalls++;
            return PushResult.success(ChannelType.WEBSOCKET);
        }

        @Override
        public ChannelType getChannelType() {
            return ChannelType.WEBSOCKET;
        }

        @Override
        public boolean isAvailable(Long userId) {
            return false;
        }

        @Override
        public boolean isConfigured(Long userId) {
            return true;
        }
    }

    @Test
    @DisplayName("未配置的渠道：pushAll 也要走到它，并留下一条 SKIPPED")
    void unconfiguredChannelLeavesSkippedRow() {
        StubChannel email = new StubChannel(ChannelType.EMAIL, false, PushResult.success(ChannelType.EMAIL));
        manager.setChannels(List.of(email));

        List<PushResult> results = manager.pushAll(USER_ID, alert());

        assertTrue(email.pushCalls <= 1);
        assertEquals(1, results.size());
        assertFalse(results.get(0).isSuccess());
        assertTrue(results.get(0).getMessage().startsWith("Skipped:"));

        ArgumentCaptor<PushResult> captor = ArgumentCaptor.forClass(PushResult.class);
        verify(pushHistoryService).recordPush(eq(USER_ID), eq(100L), captor.capture());
        assertTrue(captor.getValue().getMessage().contains("not configured"),
                "未配置的消息要能被区分出来: " + captor.getValue().getMessage());
    }

    @Test
    @DisplayName("已配置但此刻不可达：不算「未配置」，不写历史（否则离线用户每条告警一行）")
    void unreachableButConfiguredChannelWritesNoRow() {
        UnreachableButConfiguredChannel ws = new UnreachableButConfiguredChannel();
        manager.setChannels(List.of(ws));

        List<PushResult> results = manager.pushAll(USER_ID, alert());

        assertEquals(1, results.size());
        assertFalse(results.get(0).isSuccess(), "调用方仍应看到「没送达」");
        verify(pushHistoryService, never()).recordPush(anyLong(), any(), any(PushResult.class));
    }

    @Test
    @DisplayName("已配置且可达：照常推送并记录结果（不被新分支影响）")
    void configuredAndReachableChannelPushesAndRecords() {
        StubChannel email = new StubChannel(ChannelType.EMAIL, true, PushResult.success(ChannelType.EMAIL));
        manager.setChannels(List.of(email));

        List<PushResult> results = manager.pushAll(USER_ID, alert());

        assertEquals(1, email.pushCalls);
        assertTrue(results.get(0).isSuccess());
        verify(pushHistoryService, times(1)).recordPush(anyLong(), any(), any(PushResult.class));
    }

    @Test
    @DisplayName("混合场景：已配置渠道仍能送达，未配置渠道留痕，二者互不吞没")
    void mixedChannelsAreHandledIndependently() {
        StubChannel email = new StubChannel(ChannelType.EMAIL, true, PushResult.success(ChannelType.EMAIL));
        StubChannel feishu = new StubChannel(ChannelType.FEISHU, false, PushResult.success(ChannelType.FEISHU));
        manager.setChannels(List.of(email, feishu));

        List<PushResult> results = manager.pushAll(USER_ID, alert());

        assertEquals(2, results.size());
        assertEquals(1, results.stream().filter(PushResult::isSuccess).count());
        // 一条成功 + 一条未配置的 SKIPPED
        verify(pushHistoryService, times(2)).recordPush(anyLong(), any(), any(PushResult.class));
    }

    @Test
    @DisplayName("单渠道 push() 对未配置渠道的行为不变：SKIPPED 且留痕")
    void singleChannelPushStillRecordsSkipped() {
        StubChannel email = new StubChannel(ChannelType.EMAIL, false, PushResult.success(ChannelType.EMAIL));

        PushResult result = manager.push(USER_ID, alert(), ChannelType.EMAIL);

        assertFalse(result.isSuccess());
        verify(pushHistoryService, times(1)).recordPush(anyLong(), any(), any(PushResult.class));
    }

    @Test
    @DisplayName("限流超限仍是 SKIPPED 留痕（不受本次改动影响）")
    void rateLimitedChannelStillRecordsSkipped() {
        StubChannel email = new StubChannel(ChannelType.EMAIL, true, PushResult.success(ChannelType.EMAIL));
        manager.setChannels(List.of(email));
        when(pushHistoryService.isRateLimitExceeded(USER_ID, ChannelType.EMAIL)).thenReturn(true);

        PushResult result = manager.push(USER_ID, alert(), ChannelType.EMAIL);

        assertFalse(result.isSuccess());
        assertEquals(0, email.pushCalls, "限流时不应真的发出请求");
        verify(pushHistoryService, times(1)).recordPush(anyLong(), any(), any(PushResult.class));
    }
}