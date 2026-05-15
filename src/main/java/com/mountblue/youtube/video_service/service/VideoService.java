package com.mountblue.youtube.video_service.service;

import com.mountblue.youtube.video_service.client.SearchClient;
import com.mountblue.youtube.video_service.dto.HomeVideoDto;
import com.mountblue.youtube.video_service.dto.UserInfoDto;
import com.mountblue.youtube.video_service.dto.VideoIndexRequest;
import com.mountblue.youtube.video_service.dto.WatchVideoDto;
import com.mountblue.youtube.video_service.client.AuthClient;
import com.mountblue.youtube.video_service.entity.*;
import com.mountblue.youtube.video_service.repository.VideoLikeRepository;
import com.mountblue.youtube.video_service.repository.VideoRepository;
import com.mountblue.youtube.video_service.security.JwtUserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;

@Service
public class VideoService {

    private final VideoRepository videoRepository;
    private final VideoLikeRepository videoLikeRepository;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Service s3Service;
    private final AuthClient authClient;
    private final SearchClient searchClient;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    public VideoService(VideoRepository videoRepository,
                        VideoLikeRepository videoLikeRepository,
                        S3Client s3Client,
                        S3Presigner s3Presigner,
                        S3Service s3Service,
                        AuthClient authClient,
                        SearchClient searchClient) {
        this.videoRepository = videoRepository;
        this.videoLikeRepository = videoLikeRepository;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.s3Service = s3Service;
        this.authClient = authClient;
        this.searchClient = searchClient;
    }

    private JwtUserPrincipal getCurrentUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserPrincipal)) {
            throw new RuntimeException("User not authenticated");
        }

        return (JwtUserPrincipal) authentication.getPrincipal();
    }

    public void uploadVideo(MultipartFile file,
                            MultipartFile thumbnail,
                            String title,
                            String description) {

        String fileName = file.getOriginalFilename();

        if (fileName == null || !fileName.toLowerCase().endsWith(".mp4")) {
            throw new RuntimeException("Only MP4 files are allowed");
        }

        JwtUserPrincipal currentUser = getCurrentUser();

        authClient.getUserById(currentUser.getUserId());

        try {
            // ===== SAVE VIDEO =====
            Video video = new Video();
            video.setTitle(title);
            video.setDescription(description);
            video.setUserId(currentUser.getUserId());

            videoRepository.save(video);
            Long videoId = video.getId();

            String thumbnailKey = uploadThumbnailToS3(thumbnail, videoId);
            video.setThumbnailUrl(thumbnailKey);
            videoRepository.save(video);

            // ===== TEMP DIR =====
            Path tempDir = Paths.get("temp").toAbsolutePath();
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
            }

            long timestamp = System.currentTimeMillis();
            Path originalPath = tempDir.resolve(timestamp + "_original.mp4");
            Files.copy(file.getInputStream(), originalPath, StandardCopyOption.REPLACE_EXISTING);

            Map<String, Path> outputs = new HashMap<>();
            outputs.put("1080", tempDir.resolve(timestamp + "_1080.mp4"));
            outputs.put("720",  tempDir.resolve(timestamp + "_720.mp4"));
            outputs.put("480",  tempDir.resolve(timestamp + "_480.mp4"));
            outputs.put("360",  tempDir.resolve(timestamp + "_360.mp4"));

            convertVideo(originalPath, outputs.get("1080"), "1080", "23");
            convertVideo(originalPath, outputs.get("720"),  "720",  "26");
            convertVideo(originalPath, outputs.get("480"),  "480",  "28");
            convertVideo(originalPath, outputs.get("360"),  "360",  "30");

            for (Map.Entry<String, Path> entry : outputs.entrySet()) {
                String quality = entry.getKey();
                Path absoluteFilePath  = entry.getValue().toAbsolutePath();

                if (!Files.exists(absoluteFilePath)) {
                    throw new RuntimeException("Converted file not found: " + absoluteFilePath);
                }

                String key     = "videos/" + videoId + "/" + quality + ".mp4";

                PutObjectRequest request = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType("video/mp4")
                        .build();

                s3Client.putObject(request, RequestBody.fromFile(absoluteFilePath));

                VideoFile videoFile = new VideoFile();
                videoFile.setQuality(quality + "p");
                videoFile.setUrl(key);
                videoFile.setVideo(video);
                video.getVideoFiles().add(videoFile);
            }

            video.setStatus(true);
            videoRepository.save(video);

            UserInfoDto user = authClient.getUserById(video.getUserId());

            VideoIndexRequest indexRequest = new VideoIndexRequest();
            indexRequest.setId(video.getId());
            indexRequest.setTitle(video.getTitle());
            indexRequest.setDescription(video.getDescription());
            indexRequest.setChannelName(user.getChannelName());
            indexRequest.setThumbnailKey(video.getThumbnailUrl());
            indexRequest.setViewsCount(video.getViewsCount());
            indexRequest.setCreatedAt(video.getCreatedAt());

            searchClient.indexVideo(indexRequest);

            // Cleanup converted files
            for (Path path : outputs.values()) {
                Files.deleteIfExists(path);
            }

            // Cleanup original temp file
            Files.deleteIfExists(originalPath);

            System.out.println("Upload complete");

        } catch (Exception e) {
            throw new RuntimeException("Video processing failed", e);
        }
    }

    private String uploadThumbnailToS3(MultipartFile thumbnail, Long videoId) {
        try {
            String thumbnailName = thumbnail.getOriginalFilename();
            String extension = "jpg";
            if (thumbnailName != null && thumbnailName.contains(".")) {
                extension = thumbnailName.substring(thumbnailName.lastIndexOf('.') + 1);
            }

            String thumbnailKey = "thumbnails/" + videoId + "." + extension;

            PutObjectRequest thumbnailRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(thumbnailKey)
                    .contentType(thumbnail.getContentType())
                    .build();

            s3Client.putObject(
                    thumbnailRequest,
                    RequestBody.fromInputStream(thumbnail.getInputStream(), thumbnail.getSize())
            );

            return thumbnailKey;

        } catch (Exception e) {
            throw new RuntimeException("Thumbnail upload failed", e);
        }
    }

    // ===== COMMON METHOD =====
    private void convertVideo(Path input, Path output, String resolution, String crf) throws Exception {

        ProcessBuilder builder = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-i", input.toAbsolutePath().toString(),
                "-vf", "scale=-2:" + resolution,
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-crf", crf,
                "-c:a", "aac",
                "-b:a", "128k",
                output.toAbsolutePath().toString()
        );

        builder.redirectErrorStream(true);

        Process process = builder.start();

        int exitCode = process.waitFor();

        if (exitCode != 0 || !Files.exists(output)) {
            throw new RuntimeException("FFmpeg failed for " + resolution + "p");
        }

        System.out.println(resolution + "p created: " + output.toAbsolutePath());
    }

    public WatchVideoDto getVideoForWatchPage(Long id) {

        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Video not found"));

        Map<String, String> qualityUrls = new HashMap<>();
        Map<String, String> downloadUrls = new HashMap<>();

        String safeTitle = video.getTitle().replaceAll("[\\\\/:*?\"<>|]", "_");

        for (VideoFile videoFile : video.getVideoFiles()) {

            String key = videoFile.getUrl();
            String quality = videoFile.getQuality();

            // playback URL
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest =
                    GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofMinutes(15))
                            .getObjectRequest(getObjectRequest)
                            .build();

            String playUrl = s3Presigner
                    .presignGetObject(presignRequest)
                    .url()
                    .toString();

            qualityUrls.put(quality, playUrl);

            // download URL
            GetObjectRequest downloadRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .responseContentDisposition(
                            "attachment; filename=\"" + safeTitle + "-" + quality + ".mp4\""
                    )
                    .build();

            GetObjectPresignRequest downloadPresignRequest =
                    GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofMinutes(15))
                            .getObjectRequest(downloadRequest)
                            .build();

            String downloadUrl = s3Presigner
                    .presignGetObject(downloadPresignRequest)
                    .url()
                    .toString();

            downloadUrls.put(quality, downloadUrl);
        }

        WatchVideoDto dto = new WatchVideoDto();

        dto.setId(video.getId());
        dto.setTitle(video.getTitle());
        dto.setDescription(video.getDescription());
        dto.setViewsCount(video.getViewsCount());
        dto.setLikesCount(video.getLikesCount());
        dto.setDislikesCount(video.getDislikesCount());

        dto.setQualityUrls(qualityUrls);
        dto.setDownloadUrls(downloadUrls);

        UserInfoDto user = authClient.getUserById(video.getUserId());

        dto.setChannelName(user.getChannelName());
        dto.setChannelId(user.getId());

        return dto;
    }



    public void deleteVideo(Long id) {

        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Video not found"));

        JwtUserPrincipal currentUser = getCurrentUser();

        if (!video.getUserId().equals(currentUser.getUserId())) {
            throw new RuntimeException("You are not allowed to delete this video");
        }

        if (video.getThumbnailUrl() != null) {
            s3Client.deleteObject(builder -> builder
                    .bucket(bucketName)
                    .key(video.getThumbnailUrl())
            );
        }

        for (VideoFile file : video.getVideoFiles()) {
            s3Client.deleteObject(builder -> builder
                    .bucket(bucketName)
                    .key(file.getUrl())
            );
        }

        videoRepository.delete(video);
    }

    public void incrementViewCount(Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));

        video.setViewsCount(video.getViewsCount() + 1);
        videoRepository.save(video);
    }

    public void likeVideo(Long videoId) {
        JwtUserPrincipal currentUser = getCurrentUser();

        authClient.getUserById(currentUser.getUserId());

        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));

        Optional<VideoLike> existing =
                videoLikeRepository.findByUserIdAndVideoId(currentUser.getUserId(), videoId);

        //no reaction
        if (existing.isEmpty()) {

            VideoLike like = new VideoLike();
            like.setUserId(currentUser.getUserId());;
            like.setVideo(video);
            like.setType(ReactionType.LIKE);

            videoLikeRepository.save(like);
            video.setLikesCount(video.getLikesCount() + 1);
            videoRepository.save(video);

            return;
        }

        VideoLike reaction = existing.get();

        //already LIKE → remove it
        if (reaction.getType() == ReactionType.LIKE) {

            videoLikeRepository.delete(reaction);

            video.setLikesCount(video.getLikesCount() - 1);
            videoRepository.save(video);

            return;
        }

        //was DISLIKE → switch to LIKE
        if (reaction.getType() == ReactionType.DISLIKE) {

            reaction.setType(ReactionType.LIKE);
            videoLikeRepository.save(reaction);

            video.setDislikesCount(video.getDislikesCount() - 1);
            video.setLikesCount(video.getLikesCount() + 1);
            videoRepository.save(video);
        }
    }

    public void dislikeVideo(Long videoId) {
        JwtUserPrincipal currentUser = getCurrentUser();

        authClient.getUserById(currentUser.getUserId());

        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));

        Optional<VideoLike> existing =
                videoLikeRepository.findByUserIdAndVideoId(currentUser.getUserId(), videoId);

        if (existing.isEmpty()) {
            VideoLike dislike = new VideoLike();
            dislike.setUserId(currentUser.getUserId());
            dislike.setVideo(video);
            dislike.setType(ReactionType.DISLIKE);

            videoLikeRepository.save(dislike);

            video.setDislikesCount(video.getDislikesCount() + 1);
            videoRepository.save(video);
            return;
        }

        VideoLike reaction = existing.get();

        if (reaction.getType() == ReactionType.DISLIKE) {
            videoLikeRepository.delete(reaction);

            video.setDislikesCount(video.getDislikesCount() - 1);
            videoRepository.save(video);
            return;
        }

        if (reaction.getType() == ReactionType.LIKE) {
            reaction.setType(ReactionType.DISLIKE);
            videoLikeRepository.save(reaction);

            video.setLikesCount(video.getLikesCount() - 1);
            video.setDislikesCount(video.getDislikesCount() + 1);
            videoRepository.save(video);
        }
    }

    public String getCurrentUserReactionIfLoggedIn(Long videoId) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null ||
                !(authentication.getPrincipal() instanceof JwtUserPrincipal)) {
            return null; // not logged in
        }

        JwtUserPrincipal user = (JwtUserPrincipal) authentication.getPrincipal();

        Optional<VideoLike> reaction =
                videoLikeRepository.findByUserIdAndVideoId(user.getUserId(), videoId);

        return reaction.map(r -> r.getType().name()).orElse(null);
    }

    public Page<HomeVideoDto> getLatestVideos(int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        Page<Video> videoPage =
                videoRepository.findByStatusTrueOrderByCreatedAtDesc(pageable);

        List<HomeVideoDto> dtoList = new ArrayList<>();

        for (Video video : videoPage.getContent()) {

            String thumbnailKey = video.getThumbnailUrl();
            String thumbnailAccessUrl = s3Service.generatePresignedUrl(thumbnailKey);

            HomeVideoDto dto = new HomeVideoDto();
            dto.setId(video.getId());
            dto.setTitle(video.getTitle());
            dto.setViewsCount(video.getViewsCount());
            dto.setCreatedAt(video.getCreatedAt());
            dto.setThumbnailUrl(thumbnailAccessUrl);

            UserInfoDto user = authClient.getUserById(video.getUserId());
            dto.setChannelName(user.getChannelName());

            dtoList.add(dto);
        }

        return new PageImpl<>(dtoList, pageable, videoPage.getTotalElements());
    }
}
