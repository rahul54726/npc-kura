package com.npc.kura.controller;

import com.npc.kura.dto.ChunkUploadResponse;
import com.npc.kura.dto.FileCompleteRequest;
import com.npc.kura.dto.FileCompleteResponse;
import com.npc.kura.dto.FileInitiateRequest;
import com.npc.kura.dto.FileInitiateResponse;
import com.npc.kura.service.FileStorageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST Controller for handling resumable chunk-based file uploads.
 * Exposes endpoints to initiate, process chunks, and finalize file assembly.
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Validated
public class FileController {

    private final FileStorageService fileStorageService;

    /**
     * Initializes the file upload process and registers the metadata in the database.
     * * @param request Contains original file name, total size, and total expected chunks.
     * @return ResponseEntity containing the generated unique file ID.
     */
    @PostMapping("/initiate")
    public ResponseEntity<FileInitiateResponse> initiateUpload(@Valid @RequestBody FileInitiateRequest request) {
        FileInitiateResponse response = fileStorageService.initiateUpload(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Processes and stores an individual binary chunk for a specific file.
     * * @param fileId         The unique identifier generated during initialization.
     * @param sequenceNumber The ordered sequence number of the current chunk (1-based index).
     * @param chunk          The binary multipart payload.
     * @return ResponseEntity confirming the successful storage of the chunk.
     */
    @PostMapping("/{fileId}/chunk")
    public ResponseEntity<ChunkUploadResponse> uploadChunk(
            @PathVariable String fileId,
            @RequestParam("sequenceNumber") @Min(value = 1, message = "sequenceNumber must be greater than or equal to 1") int sequenceNumber,
            @RequestParam("chunkChecksum")
            @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "chunkChecksum must be a valid SHA-256 hex string")
            String chunkChecksum,
            @RequestParam("chunk") MultipartFile chunk) {
        ChunkUploadResponse response = fileStorageService.uploadChunk(fileId, sequenceNumber, chunkChecksum, chunk);
        return ResponseEntity.ok(response);
    }

    /**
     * Finalizes the upload process by verifying chunk integrity and merging them into a single file via Java NIO.
     * * @param fileId The unique identifier of the target file.
     * @return ResponseEntity containing the final storage path and status.
     */
    @PostMapping("/{fileId}/complete")
    public ResponseEntity<FileCompleteResponse> completeUpload(
            @PathVariable String fileId,
            @Valid @RequestBody FileCompleteRequest request
    ) {
        FileCompleteResponse response = fileStorageService.completeUpload(fileId, request.finalChecksum());
        return ResponseEntity.ok(response);
    }
}