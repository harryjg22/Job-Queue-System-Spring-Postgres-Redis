package com.harry.jobworker.worker;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;


@Component
public class RetryPromoter {
    private static final String QUEUE_KEY = "queue:jobs"; // Key for the Redis job queue
    private static final String RETRY_ZSET = "retry:jobs"; // Key for the Redis sorted set for retrying jobs
    private final StringRedisTemplate redisTemplate; // Template for Redis operations
    
    public RetryPromoter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Every 1 second, add ready-to-retry jobs back to the main job queue
    @Scheduled(fixedRate = 1000)
    public void promoteRetries() {
        long now = System.currentTimeMillis(); // Current time in milliseconds

        // Get job IDs from the retry sorted set with scores (timestamps) less than or equal to now
        Set<String> readyJobIds = redisTemplate.opsForZSet().rangeByScore(RETRY_ZSET, 0, now, 0, 50); // Limit to 50 jobs at a time

        if (readyJobIds == null || readyJobIds.isEmpty()) {
            return; // No jobs ready for retry
        }

        for (String jobId : readyJobIds) {
            // Remove the job ID from the retry sorted set
            Long removed = redisTemplate.opsForZSet().remove(RETRY_ZSET, jobId);
            if (removed != null && removed > 0) {
                // Push the job ID back onto the main job queue
                redisTemplate.opsForList().rightPush(QUEUE_KEY, jobId);
                System.out.println("Promoted job " + jobId + " back to the main queue for retry.");
            }
        }

    }


}
