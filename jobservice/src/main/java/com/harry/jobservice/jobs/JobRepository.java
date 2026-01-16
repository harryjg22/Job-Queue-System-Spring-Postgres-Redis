package com.harry.jobservice.jobs;

import org.springframework.data.jpa.repository.JpaRepository;

//Gives access to database CRUD operations for JobEntity
//e.g., save(), findById(), findAll(), delete(), etc.

public interface JobRepository extends JpaRepository<JobEntity, String> {
    // Additional repository methods can be added here if needed
}
