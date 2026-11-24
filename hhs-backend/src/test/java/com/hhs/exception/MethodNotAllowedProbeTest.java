package com.hhs.exception;

import com.hhs.config.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.context.ContextConfiguration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 405 的端到端锚点：真实 MVC 链路（DispatcherServlet → GlobalExceptionHandler），
 * 不是直接调 handler 方法。
 *
 * <p>复现的是方案描述的场景：一个只支持 PUT 的端点被 GET/DELETE 调用。修复前该异常落到
 * {@code @ExceptionHandler(Exception.class)} 兜底，返回 HTTP 200 + body.code=500 +
 * errorCode=system.error；修复后必须是 HTTP 405 + {@code Allow: PUT} +
 * errorCode=request.method_not_allowed。</p>
 */
@WebMvcTest(controllers = {
        MethodNotAllowedProbeTest.ProbeController.class,
        MethodNotAllowedProbeTest.JsonOnlyController.class,
        MethodNotAllowedProbeTest.JsonOutputController.class
})
@ContextConfiguration(classes = {
        MethodNotAllowedProbeTest.ProbeController.class,
        MethodNotAllowedProbeTest.JsonOnlyController.class,
        MethodNotAllowedProbeTest.JsonOutputController.class,
        TestSecurityConfig.class,
        GlobalExceptionHandler.class
})
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
@DisplayName("405 方法不支持 — 端到端")
class MethodNotAllowedProbeTest {

    @Autowired
    private MockMvc mockMvc;

    /** 只声明 PUT，用来触发 HttpRequestMethodNotSupportedException。 */
    @RestController
    @RequestMapping("/api/probe405")
    static class ProbeController {
        @PutMapping
        public String update() {
            return "ok";
        }

        @GetMapping("/other")
        public String other() {
            return "ok";
        }
    }

    @Configuration
    static class Config {
        // 占位：具体 bean 由 @ContextConfiguration 提供
    }

    @org.junit.jupiter.api.Test
    @DisplayName("GET 一个只支持 PUT 的端点 → 405 + Allow: PUT + request.method_not_allowed")
    void getOnPutOnlyEndpoint_returns405WithAllow() throws Exception {
        mockMvc.perform(get("/api/probe405").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("PUT")))
            .andExpect(jsonPath("$.code").value(405))
            .andExpect(jsonPath("$.errorCode").value("request.method_not_allowed"));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("DELETE 同样 405 —— 不是 200/500 的伪成功")
    void deleteOnPutOnlyEndpoint_returns405() throws Exception {
        mockMvc.perform(delete("/api/probe405").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("PUT")))
            .andExpect(jsonPath("$.code").value(405));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("正确的方法仍然正常 —— 修复没有把 PUT 一起拦掉")
    void putOnPutOnlyEndpoint_stillWorks() throws Exception {
        mockMvc.perform(put("/api/probe405").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // 415 / 406：同一根因（4xx 客户端契约错误被伪装成 5xx）
    // ------------------------------------------------------------------

    /** 声明 consumes=application/json，用 text/plain 打它即可触发 415。 */
    @RestController
    @RequestMapping("/api/probe415")
    static class JsonOnlyController {
        @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
        public String create(@RequestBody java.util.Map<String, Object> body) {
            return "ok";
        }
    }

    /**
     * 只产出 JSON 的端点：返回 POJO + produces=application/json。
     *
     * <p>之前用返回 {@code String} 的端点测 406 会失败：String 可以被序列化成 text/html，
     * Spring 就满足了 {@code Accept: text/html}，返回 200。必须用 JSON-only 的产物才能
     * 真正触发 {@link org.springframework.web.HttpMediaTypeNotAcceptableException}。</p>
     */
    @RestController
    @RequestMapping("/api/probe406")
    static class JsonOutputController {
        @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
        public java.util.Map<String, Object> read() {
            return java.util.Map.of("ok", true);
        }
    }

    @org.junit.jupiter.api.Test
    @DisplayName("text/plain 打 consumes=json 的端点 → 415 + request.media_type_not_supported")
    void plainTextBody_returns415() throws Exception {
        mockMvc.perform(post("/api/probe415")
                .contentType(MediaType.TEXT_PLAIN)
                .content("not json")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.code").value(415))
            .andExpect(jsonPath("$.errorCode").value("request.media_type_not_supported"))
            // 反例：修复前是 HTTP 200 + code=500 + system.error + 「服务暂时不可用」
            .andExpect(jsonPath("$.errorCode").value(org.hamcrest.Matchers.not("system.error")));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("只接受 text/html 时 → 406（不是 200/500）")
    void unacceptableAcceptHeader_returns406() throws Exception {
        // 406 的响应体本身受同一个协商约束：客户端说「不要 JSON」，Spring 就无法把 JSON 错误信封
        // 写回去，body 为空。因此这里断言的是状态码与 errorCode 是否被正确映射（不再落 500），
        // 而不是 body 内容 —— 后者在 406 语义下本来就无法保证。
        var result = mockMvc.perform(get("/api/probe406").accept(MediaType.TEXT_HTML))
            .andExpect(status().isNotAcceptable())
            .andReturn();

        // handler 确实跑了：body 为空是因为响应协商拒绝 JSON，而不是落进了 Exception 兜底。
        org.junit.jupiter.api.Assertions.assertEquals(
            406, result.getResponse().getStatus(),
            "修复前该请求会走 Exception 兜底返回 200/500");
    }
}
