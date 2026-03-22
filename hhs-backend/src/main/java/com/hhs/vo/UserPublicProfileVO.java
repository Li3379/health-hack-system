package com.hhs.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserPublicProfileVO {
    private Long id;
    private String nickname;
    private String avatar;
}
