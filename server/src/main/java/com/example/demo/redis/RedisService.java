package com.example.demo.redis;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate redisTemplate;

    public void save(String key, String value) {
        redisTemplate.opsForValue()
                .set(key, value, Duration.ofHours(1));
    }

    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }
}