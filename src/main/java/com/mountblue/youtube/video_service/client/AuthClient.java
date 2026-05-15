package com.mountblue.youtube.video_service.client;

import com.mountblue.youtube.video_service.dto.UserInfoDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "auth-service")
public interface AuthClient {

    @GetMapping("/api/users/{id}")
    UserInfoDto getUserById(@PathVariable("id") Long id);
}
