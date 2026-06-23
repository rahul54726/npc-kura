package com.npc.kura.controller;

import com.npc.kura.dto.ChunkUploadResponse;
import com.npc.kura.dto.FileCompleteResponse;
import com.npc.kura.dto.FileInitiateRequest;
import com.npc.kura.dto.FileInitiateResponse;
import com.npc.kura.service.FileStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST Controller for handling resumable chunk-based file uploads.
 * Exposes endpoints to initiate, process chunks, and finalize file assembly.
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    /**
     * Initializes the file upload process and registers the metadata in the database.
     * * @param request Contains original file name, total size, and total expected chunks.
     * @return ResponseEntity containing the generated unique file ID.
     */
    @PostMapping("/initiate")
    public ResponseEntity<?> initiateUpload(@Valid @RequestBody FileInitiateRequest request) {
        try {
            FileInitiateResponse response = fileStorageService.initiateUpload(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            // In a strict production environment, this would be handled by a @ControllerAdvice GlobalExceptionHandler
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Initialization failed: " + e.getMessage());
        }
    }

    /**
     * Processes and stores an individual binary chunk for a specific file.
     * * @param fileId         The unique identifier generated during initialization.
     * @param sequenceNumber The ordered sequence number of the current chunk (1-based index).
     * @param chunk          The binary multipart payload.
     * @return ResponseEntity confirming the successful storage of the chunk.
     */
    @PostMapping("/{fileId}/chunk")
    public ResponseEntity<?> uploadChunk(
            @PathVariable String fileId,
            @RequestParam("sequenceNumber") int sequenceNumber,
            @RequestParam("chunk") MultipartFile chunk) {
        try {
            ChunkUploadResponse response = fileStorageService.uploadChunk(fileId, sequenceNumber, chunk);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Chunk upload failed: " + e.getMessage());
        }
    }

    /**
     * Finalizes the upload process by verifying chunk integrity and merging them into a single file via Java NIO.
     * * @param fileId The unique identifier of the target file.
     * @return ResponseEntity containing the final storage path and status.
     */
    @PostMapping("/{fileId}/complete")
    public ResponseEntity<?> completeUpload(@PathVariable String fileId) {
        try {
            FileCompleteResponse response = fileStorageService.completeUpload(fileId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("File completion failed: " + e.getMessage());
        }
    }
}