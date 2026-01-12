package com.harry.jobservice.jobs.dto;

// DTO for creating a new job request
// Contains fields for job type and payload
// Used in API requests to create jobs
// No validation or business logic included
// Simple data carrier class
// Public fields for easy access
// No methods other than default constructor
// Can be extended in the future if needed


public class CreateJobRequest {
    public String type;
    public String payload;
}
