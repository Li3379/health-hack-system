package com.hhs.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 找到 Jackson 消息转换器并注入配置好的 ObjectMapper
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter) {
                ((MappingJackson2HttpMessageConverter) converter).setObjectMapper(objectMapper);
            }
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 规范化路径，确保使用绝对路径
        String normalizedPath = Paths.get(uploadPath).toAbsolutePath().normalize().toString();
        // 统一使用正斜杠（Spring ResourceHandler 需要）
        normalizedPath = normalizedPath.replace("\\", "/");
        // 确保路径以斜杠结尾
        if (!normalizedPath.endsWith("/")) {
            normalizedPath += "/";
        }

        // 配置上传文件的访问路径
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + normalizedPath)
                .resourceChain(false); // 禁用资源链，避免缓存问题
    }
}

