package com.harry.jobworker.jobs;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

//Gives access to database CRUD operations for JobEntity
//e.g., save(), findById(), findAll(), delete(), etc.

public interface JobRepository extends JpaRepository<JobEntity, String> {
    // Additional repository methods can be added here if needed

    // Atomically claim a job by updating its status from PENDING to IN_PROGRESS
    @Modifying
    @Transactional
    @Query("""
        UPDATE JobEntity j 
        SET j.status = com.harry.jobworker.jobs.JobStatus.IN_PROGRESS 
        WHERE j.id = :id and j.status = com.harry.jobworker.jobs.JobStatus.PENDING
        """)
    int claimJob(String id);

    // Atomically complete a job by updating its status from IN_PROGRESS to COMPLETED
    // Prevents completing a job that is not in IN_PROGRESS state
    @Modifying
    @Transactional
    @Query("""
        UPDATE JobEntity j 
        SET j.status = com.harry.jobworker.jobs.JobStatus.COMPLETED
        WHERE j.id = :id and j.status = com.harry.jobworker.jobs.JobStatus.IN_PROGRESS
        """)
    int completeJob(String id);
    
    
}