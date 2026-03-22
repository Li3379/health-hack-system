package com.hhs.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 64, message = "昵称长度不能超过64字符")
        String nickname,

        @Email(message = "邮箱格式不正确")
        String email,

        @Size(max = 255, message = "头像URL过长")
        String avatar,

        @Size(max = 32, message = "手机号长度过长")
        String phone
) {
}
