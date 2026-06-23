package com.npc.kura.service;

import com.npc.kura.dto.ChunkUploadResponse;
import com.npc.kura.dto.FileCompleteResponse;
import com.npc.kura.dto.FileInitiateRequest;
import com.npc.kura.dto.FileInitiateResponse;
import com.npc.kura.entity.FileChunk;
import com.npc.kura.entity.FileMetadata;
import com.npc.kura.enums.Status;
import com.npc.kura.repository.FileChunkRepository;
import com.npc.kura.repository.FileMetadataRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final FileMetadataRepository metadataRepository;
    private final FileChunkRepository chunkRepository;

    private final String UPLOAD_DIR = "storage/kura_nodes/";

    // 1. INITIATE UPLOAD
    @Transactional(rollbackOn = Exception.class)
    public FileInitiateResponse initiateUpload(FileInitiateRequest request) throws Exception {
        Path storagePath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(storagePath)) {
            Files.createDirectories(storagePath);
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
    public ChunkUploadResponse uploadChunk(String fileId, int sequenceNumber, MultipartFile chunkFile) throws Exception {
        FileMetadata metadata = metadataRepository.findById(fileId)
                .orElseThrow(() -> new Exception("File ID not found"));

        if (metadata.getStatus() != Status.UPLOADING) {
            throw new Exception("File is not in UPLOADING state");
        }

        // Save chunk to disk
        String chunkFileName = fileId + "_part" + sequenceNumber;
//        Path chunkPath = Paths.get(UPLOAD_DIR, chunkFileName);
//        chunkFile.transferTo(chunkPath.toFile());

        Path chunkPath = Paths.get(UPLOAD_DIR, chunkFileName).toAbsolutePath();
        chunkFile.transferTo(chunkPath.toFile());

        // Save chunk info to DB
        FileChunk chunk = FileChunk.builder()
                .fileMetadata(metadata)
                .sequenceNumber(sequenceNumber)
                .storegePath(chunkPath.toAbsolutePath().toString())
                .chunkSize(chunkFile.getSize())
                .build();

        chunkRepository.save(chunk);

        return new ChunkUploadResponse(fileId, sequenceNumber, "Chunk uploaded successfully");
    }

    // 3. COMPLETE AND MERGE
    @Transactional(rollbackOn = Exception.class)
    public FileCompleteResponse completeUpload(String fileId) throws Exception {
        FileMetadata metadata = metadataRepository.findById(fileId)
                .orElseThrow(() -> new Exception("File ID not found"));

        List<FileChunk> chunks = metadata.getChunks();
        if (chunks.size() != metadata.getTotalChunks()) {
            throw new Exception("Missing chunks! Expected " + metadata.getTotalChunks() + " but got " + chunks.size());
        }

        chunks.sort(Comparator.comparingInt(FileChunk::getSequenceNumber));

        Path finalFilePath = Paths.get(UPLOAD_DIR, fileId + "_" + metadata.getOriginalFileName());

        // Java NIO for high-speed, low-memory merging
        try (FileChannel destChannel = FileChannel.open(finalFilePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            for (FileChunk chunk : chunks) {
                Path chunkPath = Paths.get(chunk.getStoregePath());
                try (FileChannel srcChannel = FileChannel.open(chunkPath, StandardOpenOption.READ)) {
                    srcChannel.transferTo(0, srcChannel.size(), destChannel);
                }
                Files.deleteIfExists(chunkPath);
            }
        } catch (IOException e) {
            metadata.setStatus(Status.FAILED);
            metadataRepository.save(metadata);
            throw new Exception("File merging failed: " + e.getMessage());
        }

        metadata.setStatus(Status.COMPLETED);
        metadataRepository.save(metadata);

        return new FileCompleteResponse(fileId, finalFilePath.toString(), "COMPLETED");
    }
}