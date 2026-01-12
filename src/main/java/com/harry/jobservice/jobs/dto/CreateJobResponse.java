package com.harry.jobservice.jobs.dto;

// DTO for creating a new job response JSON object
// Contains fields for job ID and status
// Used in API JSON responses after creating jobs
// Simple data carrier class

public class CreateJobResponse {
    public String jobId;
    public String status;

    public CreateJobResponse(String jobId, String status) {
        this.jobId = jobId;
        this.status = status;
    }
}
