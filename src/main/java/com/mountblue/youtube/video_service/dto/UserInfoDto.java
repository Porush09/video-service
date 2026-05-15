package com.mountblue.youtube.video_service.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserInfoDto {
    private Long id;
    private String email;
    private String channelName;
}
