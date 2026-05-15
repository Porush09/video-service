package com.mountblue.youtube.video_service.client;

import com.mountblue.youtube.video_service.dto.VideoIndexRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "search-service")
public interface SearchClient {

    @PostMapping("/api/search/videos")
    String indexVideo(@RequestBody VideoIndexRequest request);
}