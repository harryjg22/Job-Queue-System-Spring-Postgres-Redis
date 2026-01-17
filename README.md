# Job Queue System (Spring Boot + Redis + Postgres)

### Simple, production-style **job queue** built with:
- **Job Service** (HTTP API) to create and query jobs
- **Job Worker** (background processor) to execute queried jobs
- **Redis** provides the queue, retry scheduling, and dead-letter queue (DLQ)
  - _queue:jobs_ (LIST) holds IDs of jobs waiting to be processed
  - _retry:jobs_ (ZSET) holds job IDs + when to retry them.
  - _deadletter:jobs_ (LIST) list of failed job IDs (or references).
- **PostgreSQL** is the official record of each job
  - status (PENDING/IN_PROGRESS/COMPLETED/FAILED),
  - attempts
  - createdAt
  - payload

### Supports:
- Job creation via REST (`POST /jobs`)
- Polling job status (`GET /jobs/{id}`)
- Automatic retries with exponential backoff (Redis Sorted Set)
- Dead-letter queue after max attempts (`GET /dlq`)
- Requeue of jobs in dlq to retry execution (`POST /dlq/{id}/requeue`)

---

## Architecture

### Data flow
1. Client calls **Job Service** to create a job
2. Job Service stores the job record in **Postgres** and pushes the `jobId` to **Redis list** (`queue:jobs`)
3. Job Worker blocks on Redis (`BLPOP`) and receives a `jobId`
4. Worker loads the job from **Postgres** and processes it
5. On failure:
  -  Increments the job's attempts in Postgres
  -  If the job has not exceeded max attempts, it is scheduled to retry using **Redis ZSET** (`retry:jobs`) with score = _retryTime_
  -  If the job has exceeded max attempts, it is sent to **Redis DLQ list** (`deadletter:jobs`) and status is set to `FAILED`
6. A **RetryPromoter** runs every second, moving ready jobIds from `retry:jobs` back to `queue:jobs`

### Redis keys
1. `queue:jobs` (LIST) - main queue
2. `retry:jobs` (LIST) - retry schedule with the time that the job is eligible to retry as the score
3. `deadletter:jobs` (LIST): Failed jobs

---

## Requirements

- Docker + Docker compose (recommended)
- Java (match project compiler version
  - If your `pom.xml` uses `release 25`, you must build with JDK 25 locally  
  - For Docker builds, use a base image that supports your Java release or downgrade compiler release
- Maven (only for running locally without Docker)

---

**Quick Start** (Docker Compose)
- This runs Postgres, Redis, Job Service, and Job Worker together

From repo root:
  - in terminal:
    - Create containers
    `docker compose up -d --build`
    - Verify containers were created
      `docker compose ps`
    - Check Service + Worker logs
      `docker compose logs -f jobservice`
      `docker compose logs -f jobworker`

**Quick Start** (Local Dev)
- if you want to run Postgres + Redis in Docker, and run apps on your machine

From repo root:
  - in terminal:
    - Start infra
      `docker compose up -d postgres redis`
    - Run Job Service in one terminal
      `cd jobservice`
      `mvn spring-boot:run`
    - Run Job Worker in a separate terminal
      `cd jobworker`
      `mvn spring-boot:run`

# API
HTTP Requests:
1. Create a job - `curl -s -X POST http://localhost:8080/jobs \
                   -H "Content-Type: application/json" \
                   -d '{"type":"TEST","payload":"hello"' -w "\n"`
  - HTTP Response: `'{"jobId":"###","status":"PENDING/COMPLETED"}'}`
2. Get job status - `curl -s http://localhost:8080/jobs/<PASTE_JOBID_HERE> -w "\n"`
3. Get dead-letter queue jobs - `curl -s http://localhost:8080/dlq -w "\n"`
4. Create a job that fails N times, then succeeds (this one fails twice (`FAIL_TIMES = 2`))
    - `curl -s -X POST http://localhost:8080/jobs \
      -H "Content-Type: application/json" \
      -d '{"type":"TEST", "payload":"FAIL_TIMES=2"}' -w "\n"`
5. Create a job that always fails (`payload:FAIL_ALWAYS`)
  - It will be sent to dlq after three failures
    - `curl -s -X POST http://localhost:8080/jobs \
      -H "Content-Type: application/json" \
      -d '{"type":"TEST", "payload":"FAIL_ALWAYS"}' -w "\n"`
6. Requeue a job from the Dead-Letter Queue
    -`curl -s -X POST http://localhost:8080/dlq/<PASTE_JOBID_HERE>/requeue -w "\n"`

# Inspect Redis
- Main queue length
  - `docker exec -it jobservice_redis redis-cli LLEN queue:jobs`
- Main queue contents
  - `docker exec -it jobservice_redis redis-cli LRANGE queue:jobs 0 -1`
- Retry ZSET length
  - `docker exec -it jobservice_redis redis-cli ZCARD retry:jobs`
- DLQ length
  - `docker exec -it jobservice_redis redis-cli LLEN deadletter:jobs`
- DLQ contents
  - `docker exec -it jobservice_redis redis-cli LRANGE deadletter:jobs 0 -1`





   


- 
