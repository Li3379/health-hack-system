package com.hhs.controller;

import com.hhs.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Tag(name = "文件上传", description = "头像、图片等文件上传功能")
@Slf4j
@RestController
@RequestMapping("/api/upload")
public class FileUploadController {

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    @Value("${file.upload.base-url:http://localhost:8082}")
    private String baseUrl;

    /**
     * 规范化上传路径，确保使用绝对路径
     */
    private String getNormalizedUploadPath() {
        return Paths.get(uploadPath).toAbsolutePath().normalize().toString();
    }

    @Operation(
        summary = "上传头像",
        description = "上传用户头像图片，支持 jpg/png/gif 等格式，大小不超过 2MB"
    )
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public Result<com.hhs.vo.UploadResponseVO> uploadAvatar(
            @Parameter(
                description = "头像图片文件",
                required = true,
                content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)
            )
            @RequestParam("file") MultipartFile file) {
        log.info("收到头像上传请求，文件名：{}，大小：{} bytes", 
                file.getOriginalFilename(), file.getSize());
        
        if (file.isEmpty()) {
            log.warn("文件为空");
            return Result.failure(400, "文件不能为空");
        }

        // 验证文件类型
        String contentType = file.getContentType();
        log.info("文件类型：{}", contentType);
        if (contentType == null || !contentType.startsWith("image/")) {
            log.warn("不支持的文件类型：{}", contentType);
            return Result.failure(400, "只能上传图片文件");
        }

        // 验证文件大小 (2MB)
        if (file.getSize() > 2 * 1024 * 1024) {
            log.warn("文件过大：{} bytes", file.getSize());
            return Result.failure(400, "文件大小不能超过2MB");
        }

        try {
            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = "avatar_" + UUID.randomUUID() + extension;

            // 创建上传目录（使用规范化路径）
            Path uploadDir = Paths.get(getNormalizedUploadPath(), "avatars");
            Files.createDirectories(uploadDir);
            log.info("上传目录：{}", uploadDir.toAbsolutePath());

            // 保存文件（使用 REPLACE_EXISTING 避免冲突）
            Path filePath = uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            log.info("文件保存成功：{}", filePath.toAbsolutePath());

            // 返回访问URL（使用完整的baseUrl）
            String fileUrl = baseUrl + "/uploads/avatars/" + filename;
            com.hhs.vo.UploadResponseVO response = com.hhs.vo.UploadResponseVO.builder()
                    .url(fileUrl)
                    .filename(filename)
                    .size(file.getSize())
                    .build();
            log.info("返回文件URL：{}", fileUrl);
            return Result.success(response);

        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.failure(500, "文件上传失败：" + e.getMessage());
        }
    }
}

