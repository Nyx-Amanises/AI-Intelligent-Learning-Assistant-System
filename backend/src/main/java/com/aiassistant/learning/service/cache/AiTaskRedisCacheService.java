package com.aiassistant.learning.service.cache;

import com.aiassistant.learning.vo.ai.AiTaskDetailVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiTaskRedisCacheService {

    private static final String TASK_DETAIL_KEY_PREFIX = "ai:task:detail:";
    private static final Duration ACTIVE_TASK_TTL = Duration.ofMinutes(20);
    private static final Duration TERMINAL_TASK_TTL = Duration.ofHours(12);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public AiTaskRedisCacheService(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<AiTaskDetailVO> getTaskDetail(Long userId, Long taskId) {
        if (userId == null || taskId == null) {
            return Optional.empty();
        }
        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(buildTaskDetailKey(userId, taskId));
            if (!StringUtils.hasText(cachedJson)) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(cachedJson, AiTaskDetailVO.class));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public void cacheTaskDetail(AiTaskDetailVO detailVO) {
        if (detailVO == null || detailVO.getUserId() == null || detailVO.getId() == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    buildTaskDetailKey(detailVO.getUserId(), detailVO.getId()),
                    objectMapper.writeValueAsString(detailVO),
                    resolveTtl(detailVO.getStatus())
            );
        } catch (Exception ignored) {
            // Redis unavailable should not break the main business flow.
        }
    }

    public void evictTaskDetail(Long userId, Long taskId) {
        if (userId == null || taskId == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(buildTaskDetailKey(userId, taskId));
        } catch (Exception ignored) {
            // Best effort eviction.
        }
    }

    private String buildTaskDetailKey(Long userId, Long taskId) {
        return TASK_DETAIL_KEY_PREFIX + userId + ":" + taskId;
    }

    private Duration resolveTtl(String status) {
        if (!StringUtils.hasText(status)) {
            return ACTIVE_TASK_TTL;
        }
        String normalized = status.trim().toUpperCase();
        return switch (normalized) {
            case "SUCCESS", "FAILED", "CANCELLED" -> TERMINAL_TASK_TTL;
            default -> ACTIVE_TASK_TTL;
        };
    }
}
