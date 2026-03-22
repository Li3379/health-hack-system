package com.hhs.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileVO {
    private UserVO profile;
}
