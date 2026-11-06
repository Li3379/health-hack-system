package com.hhs.security;

import com.hhs.common.constant.ErrorCode;
import com.hhs.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Validates user-supplied outbound webhook URLs to prevent server-side request forgery (SSRF).
 *
 * <p>The application POSTs alert payloads to user-configured webhook URLs (Feishu / WeCom).
 * Without validation any authenticated user could point the backend at the loopback interface,
 * an internal admin service, or the cloud metadata endpoint ({@code 169.254.169.254}).
 *
 * <p>Rules enforced:
 * <ul>
 *   <li>absolute {@code https} URL only — no {@code http}, {@code file}, {@code gopher}, …</li>
 *   <li>no userinfo in the authority ({@code https://someuser@host}) — prevents authority
 *       confusion</li>
 *   <li>no explicit port other than {@code 443} (the standard HTTPS port)</li>
 *   <li>host must not be {@code localhost} / {@code *.localhost}</li>
 *   <li>every resolved address must be public — loopback, site-local, link-local, any-local
 *       and multicast addresses are rejected</li>
 * </ul>
 *
 * <p>Hostnames are resolved with {@link InetAddress#getAllByName(String)} and rejected when any
 * address is non-public. Resolution failure is treated as invalid (fail closed) so a URL whose
 * safety cannot be established is never persisted.
 */
@Slf4j
public final class WebhookUrlValidator {

    /** Longest accepted webhook URL (matches {@code push_config.config_value VARCHAR(500)}). */
    public static final int MAX_URL_LENGTH = 500;

    private static final int DEFAULT_HTTPS_PORT = 443;

    private WebhookUrlValidator() {
    }

    /**
     * Validate a webhook URL and return its parsed form.
     *
     * @param url the candidate URL
     * @return the parsed {@link URI} when valid
     * @throws BusinessException with {@link ErrorCode#VALIDATION_INVALID_PARAMETER} and a Chinese
     *                           detail when the URL is missing, malformed, or points into a
     *                           non-public network
     */
    public static URI validate(String url) {
        if (url == null || url.isBlank()) {
            throw invalid("推送地址不能为空");
        }
        String candidate = url.trim();
        if (candidate.length() > MAX_URL_LENGTH) {
            throw invalid("推送地址过长");
        }

        URI uri = parse(candidate);

        if (!uri.isAbsolute() || uri.getScheme() == null) {
            throw invalid("推送地址格式不正确");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw invalid("仅支持 https 推送地址");
        }
        if (uri.getRawUserInfo() != null) {
            throw invalid("推送地址不能包含用户信息");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw invalid("推送地址格式不正确");
        }
        int port = uri.getPort();
        if (port != -1 && port != DEFAULT_HTTPS_PORT) {
            throw invalid("推送地址仅支持标准 https 端口");
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if ("localhost".equals(host) || host.endsWith(".localhost")) {
            throw invalid("推送地址不可用");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            log.warn("Webhook host cannot be resolved: {}", host);
            throw invalid("推送地址无法解析");
        }

        for (InetAddress address : addresses) {
            if (isNonPublic(address)) {
                log.warn("Rejected non-public webhook address: host={}, address={}", host,
                        address.getHostAddress());
                throw invalid("推送地址不可用");
            }
        }

        return uri;
    }

    /**
     * Whether the resolved address is outside the public internet.
     *
     * @param address resolved address
     * @return true for loopback, site-local, link-local, any-local or multicast addresses
     */
    public static boolean isNonPublic(InetAddress address) {
        return address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress();
    }

    private static URI parse(String candidate) {
        try {
            return new URI(candidate);
        } catch (URISyntaxException e) {
            throw invalid("推送地址格式不正确");
        }
    }

    private static BusinessException invalid(String detail) {
        return new BusinessException(ErrorCode.VALIDATION_INVALID_PARAMETER, detail);
    }
}