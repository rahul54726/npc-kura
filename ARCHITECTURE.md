# Kura Architecture

## Overview

`kura` is a Spring Boot 3 service for resumable, chunk-based file upload.
It exposes REST endpoints to:

- initialize an upload session
- upload file chunks in sequence
- finalize and merge all chunks into a single file

The service uses PostgreSQL for metadata and local filesystem storage for chunk/final file bytes.

## Tech Stack

- Java 21
- Spring Boot (`web`, `data-jpa`, `validation`)
- PostgreSQL
- Hibernate/JPA
- Maven Wrapper (`mvnw`)
- Docker (runtime and Jenkins build/deploy flow)

## High-Level Components

### 1) API Layer

Located in `src/main/java/com/npc/kura/controller`:

- `FileController`
  - `POST /api/v1/files/initiate`
  - `POST /api/v1/files/{fileId}/chunk`
  - `POST /api/v1/files/{fileId}/complete`
- `Health`
  - `GET /health`

Responsibilities:

- validate incoming HTTP input
- map request/response DTOs
- delegate business logic to service layer

### 2) Service Layer

Located in `src/main/java/com/npc/kura/service`:

- `FileStorageService`

Responsibilities:

- create upload sessions
- persist chunk metadata
- write chunk bytes to disk under `storage/kura_nodes/`
- validate chunk completeness
- merge chunks in sequence into final file using Java NIO `FileChannel`
- update upload lifecycle status (`UPLOADING`, `COMPLETED`, `FAILED`)

### 3) Persistence Layer

Located in `src/main/java/com/npc/kura/repository`:

- `FileMetadataRepository` (`JpaRepository<FileMetadata, String>`)
- `FileChunkRepository` (`JpaRepository<FileChunk, Long>`)

Database entities (in `src/main/java/com/npc/kura/entity`):

- `FileMetadata` (upload session root record)
- `FileChunk` (one record per uploaded chunk)

Relationship:

- one `FileMetadata` -> many `FileChunk`

### 4) DTO and Domain Types

Located in `src/main/java/com/npc/kura/dto` and `src/main/java/com/npc/kura/enums`:

- request/response payload contracts
- upload status enum (`Status`)

## Upload Workflow

1. **Initiate**
   - Client calls `POST /api/v1/files/initiate` with file name, size, total chunks.
   - Service creates `FileMetadata` row with generated UUID and status `UPLOADING`.
   - Response includes `fileId`.

2. **Chunk Upload**
   - Client calls `POST /api/v1/files/{fileId}/chunk` with `sequenceNumber` and multipart chunk.
   - Service verifies upload is still `UPLOADING`.
   - Chunk bytes are written to `storage/kura_nodes/{fileId}_part{sequenceNumber}`.
   - `FileChunk` row is saved with path and size.

3. **Complete**
   - Client calls `POST /api/v1/files/{fileId}/complete`.
   - Service checks received chunk count matches expected `totalChunks`.
   - Chunks are sorted by sequence and merged into:
     - `storage/kura_nodes/{fileId}_{originalFileName}`
   - Temporary chunk files are deleted.
   - Metadata status is updated to `COMPLETED` (or `FAILED` on merge error).

## Data Model

### `FileMetadata`

- `id` (UUID string primary key)
- `originalFileName`
- `totalSize`
- `totalChunks`
- `status` (`Status`)
- `createdAt`
- `chunks` (`@OneToMany`)

### `FileChunk`

- `id` (auto-generated long primary key)
- `fileMetadata` (`@ManyToOne`, FK `file_id`)
- `sequenceNumber`
- `storegePath` (filesystem path for chunk file)
- `chunkSize`

## Runtime and Configuration

Configuration file: `src/main/resources/application.yaml`

- server port: `8081`
- datasource: PostgreSQL (`npc_kura`)
- JPA: `ddl-auto: update`, PostgreSQL dialect
- multipart limits:
  - max file size: `10MB`
  - max request size: `15MB`

Application bootstrap:

- `KuraApplication` sets default JVM timezone to `Asia/Kolkata`

## Deployment Architecture

The repository includes Jenkins + Docker based deployment:

- `Jenkinsfile` pipeline stages:
  - checkout
  - test and package (`./mvnw clean package`)
  - deploy to EC2 (copy jar + `Dockerfile.runtime`, build image, run container)
  - push image to Docker Hub
- `Dockerfile.runtime` is used on target host for runtime image build.
- `docker-compose.jenkins.yml` and `Dockerfile.jenkins` support Jenkins environment setup.
- datasource runtime secrets are injected from Jenkins credentials (`kura-datasource-url`, `kura-db-creds`) as container environment variables.

## Current Architecture Characteristics

- **Strengths**
  - clean layered separation (controller/service/repository)
  - resumable chunk model with explicit completion step
  - lightweight filesystem chunk storage with DB-backed tracking
- **Operational notes**
  - filesystem path is local to runtime node; horizontal scaling needs shared/object storage
  - there is no global exception handler yet (`@ControllerAdvice` can standardize errors)
  - completion checks count of chunks; stronger integrity checks can include hash/size validation
  - default config uses direct datasource credentials; move to environment/secret management per environment

## Repository Layout (Important Paths)

- `src/main/java/com/npc/kura/controller` - REST controllers
- `src/main/java/com/npc/kura/service` - business logic
- `src/main/java/com/npc/kura/repository` - JPA repositories
- `src/main/java/com/npc/kura/entity` - persistence entities
- `src/main/java/com/npc/kura/dto` - API DTO contracts
- `src/main/resources/application.yaml` - runtime config
- `src/test/java/com/npc/kura/service` - unit tests
- `storage/kura_nodes` - local upload/chunk storage directory

