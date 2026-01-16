package com.harry.jobservice.jobs;

import com.harry.jobservice.jobs.dto.CreateJobResponse;
import com.harry.jobservice.jobs.dto.CreateJobRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

// REST controller for managing jobs
// Handles job creation and retrieval
// Uses JobRepository for database interactions
// Defines endpoints under /jobs
// Uses CreateJobRequest and CreateJobResponse DTOs for request/response payloads
// Simple controller with two endpoints: POST /jobs and GET /jobs/{id}
// No complex business logic, just basic CRUD operations
// Annotated with @RestController and @RequestMapping


@RestController
@RequestMapping("/jobs")
public class JobController {
    private final JobRepository jobRepository;
    private final StringRedisTemplate redisTemplate;

    public JobController(JobRepository jobRepository, StringRedisTemplate redisTemplate) {
        this.jobRepository = jobRepository;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping
    public ResponseEntity<CreateJobResponse> createJob(@RequestBody CreateJobRequest request) {
        // Generate a unique job ID
        String jobId = UUID.randomUUID().toString();

        String jobType = (request.type == null || request.type.isEmpty()) ? "DEFAULT" : request.type;
        String jobPayload = (request.payload == null) ? "" : request.payload;



        // Create a new JobEntity
        JobEntity job = new JobEntity(jobId, jobType, jobPayload);

        // Save the job to the database
        jobRepository.save(job);

        // Enqueue job ID in Redis list
        redisTemplate.opsForList().rightPush("queue:jobs", job.getId());

        // Create response
        CreateJobResponse response = new CreateJobResponse(job.getId(), job.getStatus().name());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobEntity> getJob(@PathVariable String id) {
        return jobRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("{id}/requeue")
    public ResponseEntity<Void> requeueJob(@PathVariable String id) {
        // Check if job exists
        if (!jobRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        JobEntity job = jobRepository.findById(id).get();
        // Update job status to PENDING
        job.setStatus(JobStatus.PENDING);
        jobRepository.save(job);

        // Add job ID to Redis queue
        redisTemplate.opsForList().rightPush("queue:jobs", id);

        // Return 200 OK response
        return ResponseEntity.ok().build();
    }

    
}