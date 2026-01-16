package com.harry.jobworker.worker;

import com.harry.jobworker.jobs.JobEntity;
import com.harry.jobworker.jobs.JobStatus;
import com.harry.jobworker.jobs.JobRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory; // Importing RedisConnectionFactory for Redis connections
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component // Marking the class as a Spring component
           // Tells Spring to create this class automatically when the application starts
public class RedisJobWorker implements CommandLineRunner{ // Tells spring to run this code
                                                          // immediately after the application finishes starting up

    private final JobRepository jobRepository; // Repository for accessing job data
                                               // Lets you open a connection to Redis
                                               // Needed for blocking pop operations
    private final RedisConnectionFactory redisConnectionFactory; // Factory for creating Redis connections
    
    public RedisJobWorker(JobRepository jobRepository, RedisConnectionFactory redisConnectionFactory) {
        this.jobRepository = jobRepository;
        this.redisConnectionFactory = redisConnectionFactory;
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
                var result = connection.bLPop(0,queueKey); // Blocks until a job is available in the queue "queue:jobs"
                                                           // 0 means it will wait indefinitely
                if (result != null && result.size() < 2) continue; // Check if a job was retrieved
                                                                   // BLPop returns a list with two elements: 
                                                                   //   the key and the value usually returned as bytes, 
                                                                   //   expecting size >= 2
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
            job.setStatus(JobStatus.IN_PROGRESS); // Update job status to RUNNING
            jobRepository.save(job); // Save the updated job status to the database

            Thread.sleep(1500); // Simulate job processing (doing work) time (1.5 seconds)

            job.setStatus(JobStatus.COMPLETED); // Update job status to COMPLETED
            jobRepository.save(job); // Save the updated job status to the database

            System.out.println("Processed job: " + job.getId() + "-> COMPLETED"); // Log the processed job ID
        } catch (Exception e){
            job.setStatus(JobStatus.FAILED); // Update job status to FAILED in case of an error
            jobRepository.save(job); // Save the updated job status to the database

            System.out.println("Processed job: " + job.getId() + "-> FAILED"); // Log the failed job ID
        }
    }

}
