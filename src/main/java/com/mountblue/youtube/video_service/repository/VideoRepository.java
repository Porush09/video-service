package com.mountblue.youtube.video_service.repository;

import com.mountblue.youtube.video_service.entity.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<Video, Long> {

    Page<Video> findByStatusTrueOrderByCreatedAtDesc(Pageable pageable);

}
