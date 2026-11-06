package com.hhs.security;

import com.hhs.common.constant.ErrorCode;
import com.hhs.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SSRF regression tests for {@link WebhookUrlValidator}.
 *
 * <p>IP literals are used for the address-classification cases so no DNS lookup is required;
 * {@link java.net.InetAddress#getAllByName(String)} resolves a literal without a network call.
 */
class WebhookUrlValidatorTest {

    @Test
    @DisplayName("a public HTTPS webhook URL is accepted")
    void acceptsPublicHttpsUrl() {
        // 8.8.8.8 is a public IP literal — deterministic, no DNS required.
        assertThatCode(() -> WebhookUrlValidator.validate("https://8.8.8.8/open-apis/bot/v2/hook/x"))
            .doesNotThrowAnyException();
        // Explicit standard port is also fine.
        assertThatCode(() -> WebhookUrlValidator.validate("https://8.8.8.8:443/hook"))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "rejects {0}")
    @ValueSource(strings = {
        "http://8.8.8.8/hook",
        "http://localhost/hook",
        "http://127.0.0.1/hook",
        "http://169.254.169.254/latest/meta-data/",
        "http://10.0.0.5/hook",
        "file:///etc/passwd",
        "ftp://8.8.8.8/hook",
        "https://someuser@8.8.8.8/hook",
        "https://8.8.8.8:8443/hook",
        "/relative/path"
    })
    @DisplayName("non-https, userinfo, non-standard port and relative URLs are rejected")
    void rejectsUnsafeUrls(String url) {
        assertThatThrownBy(() -> WebhookUrlValidator.validate(url))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.VALIDATION_INVALID_PARAMETER);
    }

    @ParameterizedTest(name = "rejects https://{0} targeting a private network")
    @ValueSource(strings = {
        "127.0.0.1",
        "169.254.169.254",
        "10.1.2.3",
        "192.168.1.10",
        "172.16.0.1",
        "0.0.0.0"
    })
    @DisplayName("https URLs resolving to loopback / private / link-local addresses are rejected")
    void rejectsNonPublicAddressesEvenOverHttps(String host) {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://" + host + "/hook"))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("blank and over-long values are rejected")
    void rejectsBlankAndOverlong() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("   "))
            .isInstanceOf(BusinessException.class);

        String tooLong = "https://8.8.8.8/" + "a".repeat(WebhookUrlValidator.MAX_URL_LENGTH);
        assertThatThrownBy(() -> WebhookUrlValidator.validate(tooLong))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("loopback and link-local addresses are classified as non-public")
    void classifiesAddresses() throws Exception {
        assertThat(WebhookUrlValidator.isNonPublic(
            java.net.InetAddress.getByName("127.0.0.1"))).isTrue();
        assertThat(WebhookUrlValidator.isNonPublic(
            java.net.InetAddress.getByName("169.254.169.254"))).isTrue();
        assertThat(WebhookUrlValidator.isNonPublic(
            java.net.InetAddress.getByName("10.0.0.1"))).isTrue();
        assertThat(WebhookUrlValidator.isNonPublic(
            java.net.InetAddress.getByName("8.8.8.8"))).isFalse();
    }
}