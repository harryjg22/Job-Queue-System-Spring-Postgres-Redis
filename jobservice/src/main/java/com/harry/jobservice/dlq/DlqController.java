package com.harry.jobservice.dlq;

import com.harry.jobservice.jobs.JobEntity;
import com.harry.jobservice.jobs.JobRepository;
import com.harry.jobservice.jobs.JobStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/dlq")
public class DlqController {
    private static final String DLQ_KEY = "deadletter:jobs";
    private static final String QUEUE_KEY = "queue:jobs";

    private final JobRepository jobRepository;
    private final StringRedisTemplate redisTemplate;

    public DlqController(JobRepository jobRepository, StringRedisTemplate redisTemplate) {
        this.jobRepository = jobRepository;
        this.redisTemplate = redisTemplate;
    }

    // GET /dlq - Retrieve all jobs in the dead-letter queue
    @GetMapping
    public ResponseEntity<List<JobEntity>> getAllDlqJobs() {
        // Fetch all job IDs from the dead-letter queue in Redis (0 to -1 gets all elements)
        List<String> jobIds = redisTemplate.opsForList().range(DLQ_KEY, 0, -1);
        List<JobEntity> jobs = jobRepository.findAllById(jobIds);
        if(jobIds == null || jobIds.isEmpty()) {
            return ResponseEntity.ok().body(List.of());
        }
        return ResponseEntity.ok().body(jobs);
    }

    // POST /dlq/{id}/requeue - Requeue a job from the dead-letter queue back to the main queue
    @PostMapping("/{id}/requeue")
    public ResponseEntity<String> requeueJob(@PathVariable String id) {
        // Check if the job exists in the dead-letter queue
        JobEntity job = jobRepository.findById(id).orElse(null);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        // Reset job status to PENDING
        job.setStatus(JobStatus.PENDING);
        job.setAttempts(0);
        jobRepository.save(job);

        // Move job ID from dead-letter queue to main queue in Redis
        redisTemplate.opsForList().remove(DLQ_KEY, 1, id);//remove one occurrence of id from DLQ

        redisTemplate.opsForList().rightPush(QUEUE_KEY, id);//add id to main queue

        return ResponseEntity.ok("Job requeued successfully.");
    }

}