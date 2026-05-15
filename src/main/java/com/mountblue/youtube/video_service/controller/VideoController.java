package com.mountblue.youtube.video_service.controller;

import com.mountblue.youtube.video_service.dto.FeedPageResponse;
import com.mountblue.youtube.video_service.dto.HomeVideoDto;
import com.mountblue.youtube.video_service.dto.WatchVideoDto;
import com.mountblue.youtube.video_service.service.S3Service;
import com.mountblue.youtube.video_service.service.VideoService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoService videoService;
    private final S3Service s3Service;

    public VideoController(VideoService videoService,
                           S3Service s3Service) {
        this.videoService = videoService;
        this.s3Service = s3Service;
    }

    @PostMapping("/upload")
    public String uploadVideo(@RequestParam("file") MultipartFile file,
                              @RequestParam("thumbnail") MultipartFile thumbnail,
                              @RequestParam("title") String title,
                              @RequestParam("description") String description) {

        videoService.uploadVideo(file, thumbnail, title, description);
        return "Video uploaded successfully";
    }

    @GetMapping("/home")
    public FeedPageResponse getHomeVideos(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "12") int size) {

        Page<HomeVideoDto> videoPage = videoService.getLatestVideos(page, size);

        FeedPageResponse response = new FeedPageResponse();
        response.setVideos(videoPage.getContent());
        response.setCurrentPage(page);
        response.setHasNext(videoPage.hasNext());

        return response;
    }

    @GetMapping("/{id}")
    public WatchVideoDto getVideo(@PathVariable Long id) {
        return videoService.getVideoForWatchPage(id);
    }

    @DeleteMapping("/{id}")
    public String deleteVideo(@PathVariable Long id) {
        videoService.deleteVideo(id);
        return "Video deleted successfully";
    }

    @PostMapping("/{id}/view")
    public void incrementView(@PathVariable Long id) {
        videoService.incrementViewCount(id);
    }

    @PostMapping("/{id}/like")
    public void likeVideo(@PathVariable Long id) {
        videoService.likeVideo(id);
    }

    @PostMapping("/{id}/dislike")
    public void dislikeVideo(@PathVariable Long id) {
        videoService.dislikeVideo(id);
    }

    @GetMapping("/{id}/reaction")
    public String getCurrentUserReaction(@PathVariable Long id) {
        return videoService.getCurrentUserReactionIfLoggedIn(id);
    }

    @GetMapping("/thumbnail-url")
    public String getThumbnailUrl(@RequestParam String key) {
        return s3Service.generatePresignedUrl(key);
    }
}