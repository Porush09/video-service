package com.mountblue.youtube.video_service.security;

import com.mountblue.youtube.video_service.config.JwtFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/youtube/video/watch/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/youtube/video/*/view").permitAll()

                        .requestMatchers(HttpMethod.GET, "/youtube/video/upload").authenticated()
                        .requestMatchers(HttpMethod.POST, "/youtube/video/upload").authenticated()

                        .requestMatchers(HttpMethod.POST, "/youtube/video/*/like").authenticated()
                        .requestMatchers(HttpMethod.POST, "/youtube/video/*/dislike").authenticated()
                        .requestMatchers(HttpMethod.POST, "/youtube/video/delete/**").authenticated()

                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendRedirect("http://localhost:8081/login"))
                );

        return http.build();
    }
}
