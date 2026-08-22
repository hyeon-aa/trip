package com.example.demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        // Vercel 배포 도메인(trip-t2kb.vercel.app) 추가 — 로컬 개발은 계속
        // localhost:3000으로 붙으므로 둘 다 허용한다.
        .allowedOrigins("http://localhost:3000", "https://trip-t2kb.vercel.app")
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE");
  }
}
