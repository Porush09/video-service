package com.mountblue.youtube.video_service.repository;

import com.mountblue.youtube.video_service.entity.VideoFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoFileRepository extends JpaRepository<VideoFile, Long> {

    List<VideoFile> findByVideoId(Long videoId);
}