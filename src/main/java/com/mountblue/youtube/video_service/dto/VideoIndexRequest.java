package com.mountblue.youtube.video_service.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class VideoIndexRequest {
    private Long id;
    private String title;
    private String description;
    private String channelName;
    private String thumbnailKey;
    private Long viewsCount;
    private LocalDateTime createdAt;
}
