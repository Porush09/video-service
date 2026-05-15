package com.mountblue.youtube.video_service.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class HomeVideoDto {
    private Long id;
    private String title;
    private Long viewsCount;
    private LocalDateTime createdAt;
    private String thumbnailUrl;
    private String channelName;
    private Long subscribersCount;
}
