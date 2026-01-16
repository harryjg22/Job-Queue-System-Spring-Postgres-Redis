package com.harry.jobservice.jobs;

import jakarta.persistence.*;
import java.time.Instant;

// Represents a job entity in the database
// Contains fields for job ID, type, payload, status, attempts, and creation timestamp
// Uses JPA annotations for ORM mapping

@Entity
@Table(name = "jobs")
public class JobEntity {
    
    @Id
    @Column(nullable = false, updatable = false)
    private String id;

    @Column(nullable = false)
    private String type;

    @Column(columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public JobEntity(String id, String type, String payload) {
        this.id = id;
        this.type = type;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.attempts = 0;
        this.status = JobStatus.PENDING;
    }

    protected JobEntity() {
        // JPA requires a default constructor
    }

    // Getters and setters
    public String getId() {
        return id;
    }
    public String getType() {
        return type;
    }
    public String getPayload() {
        return payload;
    }
    public JobStatus getStatus() {
        return status;
    }
    public int getAttempts() {
        return attempts;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }
    public void incrementAttempts() {
        this.attempts++;
    }
    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

}
