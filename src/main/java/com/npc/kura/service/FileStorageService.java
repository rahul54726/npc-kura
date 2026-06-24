package com.npc.kura.service;

import com.npc.kura.dto.ChunkUploadResponse;
import com.npc.kura.dto.FileCompleteResponse;
import com.npc.kura.dto.FileInitiateRequest;
import com.npc.kura.dto.FileInitiateResponse;
import com.npc.kura.entity.FileChunk;
import com.npc.kura.entity.FileMetadata;
import com.npc.kura.enums.Status;
import com.npc.kura.exception.DuplicateChunkException;
import com.npc.kura.exception.IntegrityCheckFailedException;
import com.npc.kura.exception.InvalidUploadStateException;
import com.npc.kura.exception.ResourceNotFoundException;
import com.npc.kura.repository.FileChunkRepository;
import com.npc.kura.repository.FileMetadataRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final FileMetadataRepository metadataRepository;
    private final FileChunkRepository chunkRepository;

    private final String UPLOAD_DIR = "storage/kura_nodes/";

    // 1. INITIATE UPLOAD
    @Transactional(rollbackOn = Exception.class)
    public FileInitiateResponse initiateUpload(FileInitiateRequest request) {
        Path storagePath = Paths.get(UPLOAD_DIR);
        try {
            if (!Files.exists(storagePath)) {
                Files.createDirectories(storagePath);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize upload directory", e);
        }

        FileMetadata metadata = FileMetadata.builder()
                .originalFileName(request.originalFileName())
                .totalSize(request.totalSize())
                .totalChunks(request.totalChunks())
                .status(Status.UPLOADING)
                .chunks(new ArrayList<>())
                .build();

        metadata = metadataRepository.save(metadata);

        return new FileInitiateResponse(metadata.getId(), "Upload initialized");
    }

    // 2. UPLOAD CHUNK
    @Transactional(rollbackOn = Exception.class)
    public ChunkUploadResponse uploadChunk(
            String fileId,
            int sequenceNumber,
            String chunkChecksum,
            MultipartFile chunkFile
    ) {
        FileMetadata metadata = metadataRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File ID not found: " + fileId));

        if (metadata.getStatus() != Status.UPLOADING) {
            throw new InvalidUploadStateException("File is not in UPLOADING state");
        }

        if (sequenceNumber > metadata.getTotalChunks()) {
            throw new IntegrityCheckFailedException(
                    "sequenceNumber cannot be greater than totalChunks (" + metadata.getTotalChunks() + ")"
            );
        }

        if (chunkRepository.existsByFileMetadataIdAndSequenceNumber(fileId, sequenceNumber)) {
            throw new DuplicateChunkException("Chunk already uploaded for sequenceNumber: " + sequenceNumber);
        }

        verifyChunkChecksum(chunkFile, chunkChecksum);

        // Save chunk to disk
        String chunkFileName = fileId + "_part" + sequenceNumber;
        Path chunkPath = Paths.get(UPLOAD_DIR, chunkFileName).toAbsolutePath();
        try {
            chunkFile.transferTo(chunkPath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist chunk to disk", e);
        }

        // Save chunk info to DB
        FileChunk chunk = FileChunk.builder()
                .fileMetadata(metadata)
                .sequenceNumber(sequenceNumber)
                .storegePath(chunkPath.toAbsolutePath().toString())
                .chunkSize(chunkFile.getSize())
                .sha256Checksum(normalizeChecksum(chunkChecksum))
                .build();

        chunkRepository.save(chunk);

        return new ChunkUploadResponse(fileId, sequenceNumber, "Chunk uploaded successfully");
    }

    // 3. COMPLETE AND MERGE
    @Transactional(rollbackOn = Exception.class)
    public FileCompleteResponse completeUpload(String fileId, String finalChecksum) {
        FileMetadata metadata = metadataRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File ID not found: " + fileId));

        if (metadata.getStatus() != Status.UPLOADING) {
            throw new InvalidUploadStateException("File is not in UPLOADING state");
        }

        List<FileChunk> chunks = chunkRepository.findByFileMetadataIdOrderBySequenceNumberAsc(fileId);
        if (chunks.size() != metadata.getTotalChunks()) {
            throw new IntegrityCheckFailedException(
                    "Missing chunks! Expected " + metadata.getTotalChunks() + " but got " + chunks.size()
            );
        }

        chunks.sort(Comparator.comparingInt(FileChunk::getSequenceNumber));
        validateContiguousSequence(chunks, metadata.getTotalChunks());

        Path finalFilePath = Paths.get(UPLOAD_DIR, fileId + "_" + metadata.getOriginalFileName());

        // Java NIO for high-speed, low-memory merging
        try (FileChannel destChannel = FileChannel.open(finalFilePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            for (FileChunk chunk : chunks) {
                Path chunkPath = Paths.get(chunk.getStoregePath());
                validateChunkFileChecksum(chunkPath, chunk.getSha256Checksum(), chunk.getSequenceNumber());
                try (FileChannel srcChannel = FileChannel.open(chunkPath, StandardOpenOption.READ)) {
                    srcChannel.transferTo(0, srcChannel.size(), destChannel);
                }
                Files.deleteIfExists(chunkPath);
            }
        } catch (IOException e) {
            metadata.setStatus(Status.FAILED);
            metadataRepository.save(metadata);
            throw new IllegalStateException("File merging failed: " + e.getMessage(), e);
        }

        String computedFinalChecksum = computeSha256(finalFilePath);
        if (!normalizeChecksum(finalChecksum).equals(computedFinalChecksum)) {
            metadata.setStatus(Status.FAILED);
            metadataRepository.save(metadata);
            throw new IntegrityCheckFailedException("Final file checksum mismatch");
        }

        metadata.setStatus(Status.COMPLETED);
        metadataRepository.save(metadata);

        return new FileCompleteResponse(fileId, finalFilePath.toString(), "COMPLETED");
    }

    private void verifyChunkChecksum(MultipartFile chunkFile, String expectedChecksum) {
        String computedChecksum;
        try {
            computedChecksum = computeSha256(chunkFile.getBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read chunk payload", e);
        }

        if (!normalizeChecksum(expectedChecksum).equals(computedChecksum)) {
            throw new IntegrityCheckFailedException("Chunk checksum mismatch");
        }
    }

    private void validateChunkFileChecksum(Path chunkPath, String expectedChecksum, int sequenceNumber) {
        String computedChecksum = computeSha256(chunkPath);
        if (!normalizeChecksum(expectedChecksum).equals(computedChecksum)) {
            throw new IntegrityCheckFailedException(
                    "Stored chunk checksum mismatch at sequenceNumber: " + sequenceNumber
            );
        }
    }

    private void validateContiguousSequence(List<FileChunk> chunks, int totalChunks) {
        for (int i = 0; i < totalChunks; i++) {
            int expectedSequence = i + 1;
            if (chunks.get(i).getSequenceNumber() != expectedSequence) {
                throw new IntegrityCheckFailedException("Chunk sequence is not contiguous from 1..totalChunks");
            }
        }
    }

    private String computeSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }

    private String computeSha256(Path filePath) {
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            return computeSha256(bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to compute SHA-256 for path: " + filePath, e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String normalizeChecksum(String checksum) {
        return checksum.toLowerCase(Locale.ROOT).trim();
    }
}