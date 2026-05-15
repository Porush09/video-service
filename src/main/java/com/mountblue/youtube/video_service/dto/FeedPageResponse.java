package com.mountblue.youtube.video_service.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FeedPageResponse {
    private List<HomeVideoDto> videos;
    private int currentPage;
    private boolean hasNext;
}
