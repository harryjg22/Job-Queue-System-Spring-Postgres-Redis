package com.harry.jobworker.worker;

import com.harry.jobworker.jobs.JobEntity;
import com.harry.jobworker.jobs.JobStatus;
import com.harry.jobworker.jobs.JobRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory; // Importing RedisConnectionFactory for Redis connections
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component // Marking the class as a Spring component
           // Tells Spring to create this class automatically when the application starts
public class RedisJobWorker implements CommandLineRunner{ // Tells spring to run this code
                                                          // immediately after the application finishes starting up

    private static final String JOB_QUEUE_KEY = "queue:jobs"; // Key for the Redis job queue
    private static final String RETRY_ZSET = "retry:jobs"; // Key for the Redis sorted set for retrying jobs
    private static final String DEAD_LETTER_KEY = "deadletter:jobs"; // Key for the Redis dead letter queue

    private static final int MAX_RETRIES = 3; // Maximum number of retries for a job
    private static final long BASE_BACKOFF_MILLIS = 2000; // Base backoff time in milliseconds for retries
    private static final long MAX_BACKOFF_MILLIS = 30000; // Maximum backoff time in milliseconds

    private final StringRedisTemplate redisTemplate; // Template for Redis operations
    private final JobRepository jobRepository; // Repository for accessing job data
                                               // Lets you open a connection to Redis
                                               // Needed for blocking pop operations
    private final RedisConnectionFactory redisConnectionFactory; // Factory for creating Redis connections

    public RedisJobWorker(JobRepository jobRepository, RedisConnectionFactory redisConnectionFactory, StringRedisTemplate redisTemplate){
        this.jobRepository = jobRepository;
        this.redisConnectionFactory = redisConnectionFactory;
        this.redisTemplate = redisTemplate;
    }

    // This is the workers main loop
    // It runs indefinitely, waiting for jobs to process
    @Override
    public void run(String... args) throws Exception {
        System.out.println("Worker running (blocking). Waiting for jobs on queue:jobs...");

        byte[] queueKey = "queue:jobs".getBytes(StandardCharsets.UTF_8); // Key for the Redis job queue, low-level Redis operations use byte arrays
                                                                         // UTF-8 encoding for converting strings to byte arrays
        
        while (true) { // Infinite loop to keep the worker running
            try (RedisConnection connection = redisConnectionFactory.getConnection()) { // Open a Redis connection and automatically closes the connection safely
                // Blocking pop operation
                var result = connection.bLPop(30, queueKey); // Blocks until a job is available in the queue "queue:jobs"
                                                           // 0 means it will wait indefinitely
                                                           // Returns a list of byte arrays representing the popped job ID
                if (result == null || result.size() < 2) continue; // Check if a job was retrieved
                                                                   // BLPop returns a list with two elements: 
                                                                   //   the key and the value usually returned as bytes, 
                                                                   //   expecting size >= 2
                                                                   // If no job was retrieved, continue to the next iteration of the loop
                                                                   // In case redis returns null (no job) or the result size is less than 2, we skip processing
                String jobId = new String(result.get(1), StandardCharsets.UTF_8); // Convert the job ID from bytes to string using UTF-8 encoding

                // Lookup the job in PostgreSQL and process it
                // if it does not exist, we just ignore it
                jobRepository.findById(jobId).ifPresent(this::processJob);

            } catch (Exception e) {
                System.out.println("Worker Error: " + e.getMessage());
                
                // In case of an error, wait a bit before retrying so Redis does nto permanently crash
                try {
                    Thread.sleep(1000); // Sleep for a second before retrying in case of an error
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); // Restore the interrupted status
                }
            }
        }
    }


    // Process a job by updating its status and simulating work
    public void processJob(JobEntity job){
        try{
            
            // Only start if status is still pending
            if(job.getStatus() != JobStatus.PENDING){
                return;
            }
            
            job.setStatus(JobStatus.IN_PROGRESS); // Update job status to RUNNING
            jobRepository.save(job); // Save the updated job status to the database

            System.out.println("Processing job: " +  job.getId() + " -> " + job.getStatus()); // Log the processing job ID

            if(job.getPayload() != null && job.getPayload().contains("FAIL_ALWAYS")){
                throw new RuntimeException("Simulated job failure for job " + job.getId());
            }

            if(job.getPayload() != null && job.getPayload().contains("FAIL_TIMES=")){
                int n = Integer.parseInt(job.getPayload().substring("FAIL_TIMES=".length()).trim());
                if(job.getAttempts() < n){
                    throw new RuntimeException("Simulated job attempt for job " + job.getId() + ", attempt " + (job.getAttempts() + 1)); 
                }
            }

            Thread.sleep(1500); // Simulate job processing (doing work) time (1.5 seconds)

            job.setStatus(JobStatus.COMPLETED); // Update job status to COMPLETED
            jobRepository.save(job); // Save the updated job status to the database

            System.out.println("Processed job: " + job.getId() + "-> COMPLETED"); // Log the processed job ID
        } catch (Exception e){
            job.incrementAttempts();

            if(job.getAttempts() >= MAX_RETRIES){
                job.setStatus(JobStatus.FAILED); // Update job status to FAILED in case of an error
                jobRepository.save(job); // Save the updated job status to the database

                redisTemplate.opsForList().rightPush(DEAD_LETTER_KEY, job.getId()); // Push failed job ID into the DLQ
                System.out.println("Processed job: " + job.getId() + "-> FAILED (max attempts exceeded) (sent to DLQ)"); // Log the failed job ID
                return;
            }
            
            long delay = Math.min(BASE_BACKOFF_MILLIS * (1L << (job.getAttempts() - 1)), MAX_BACKOFF_MILLIS); // Exponential backoff calculation, 
                                                                                                              // BASE_BACKOFF_MILLIS * long(2^(attempts-1)), caps at MAX_BACKOFF_MILLIS
            long retryTime = System.currentTimeMillis() + delay; // Calculate the retry time based on the current time and the delay
                                                                 // Job is retried 'delay' milliseconds from now (current time)

            job.setStatus(JobStatus.PENDING); // Reset status to pending for retry
            jobRepository.save(job); // Save the updated job status to the database

            redisTemplate.opsForZSet().add(RETRY_ZSET, job.getId(), retryTime); // Add job ID to the retry sorted set with the calculated retry time as the score

            System.out.println("Processed job: " + job.getId() + " -> FAILED (attempt " + job.getAttempts() + "), retrying in " + delay + "ms"); // Log the retry information

            
        }
    }

}
