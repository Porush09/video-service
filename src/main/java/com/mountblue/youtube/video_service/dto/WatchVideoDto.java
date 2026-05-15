package com.mountblue.youtube.video_service.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class WatchVideoDto {
    private Long id;
    private String title;
    private String description;
    private Long viewsCount;
    private Long likesCount;
    private Long dislikesCount;
    private String channelName;
    private Long channelId;
    private Map<String, String> qualityUrls;
    private Map<String, String> downloadUrls;
}
