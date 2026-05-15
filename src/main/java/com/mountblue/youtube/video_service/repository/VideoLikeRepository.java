package com.mountblue.youtube.video_service.repository;

import com.mountblue.youtube.video_service.entity.ReactionType;
import com.mountblue.youtube.video_service.entity.VideoLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VideoLikeRepository extends JpaRepository<VideoLike, Long> {

    Optional<VideoLike> findByUserIdAndVideoId(Long userId, Long videoId);

    List<VideoLike> findByUserIdAndType(Long userId, ReactionType type);
}
