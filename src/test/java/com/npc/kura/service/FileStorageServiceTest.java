package com.npc.kura.service;

import com.npc.kura.dto.ChunkUploadResponse;
import com.npc.kura.dto.FileCompleteResponse;
import com.npc.kura.dto.FileInitiateRequest;
import com.npc.kura.dto.FileInitiateResponse;
import com.npc.kura.entity.FileChunk;
import com.npc.kura.entity.FileMetadata;
import com.npc.kura.enums.Status;
import com.npc.kura.exception.IntegrityCheckFailedException;
import com.npc.kura.repository.FileChunkRepository;
import com.npc.kura.repository.FileMetadataRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FileStorageService using Mockito.
 * Ensures business logic integrity without requiring an actual database or file system.
 */
@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private FileMetadataRepository metadataRepository;

    @Mock
    private FileChunkRepository chunkRepository;

    @InjectMocks
    private FileStorageService fileStorageService;

    private FileInitiateRequest mockRequest;
    private FileMetadata mockSavedMetadata;
    private final List<Path> pathsToDelete = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Prepare mock data before each test runs
        mockRequest = new FileInitiateRequest("test-movie-trailer.mp4", 5000000L, 5);

        mockSavedMetadata = FileMetadata.builder()
                .id("test-uuid-1234")
                .originalFileName("test-movie-trailer.mp4")
                .totalSize(5000000L)
                .totalChunks(5)
                .status(Status.UPLOADING)
                .chunks(new ArrayList<>())
                .build();
    }

    @AfterEach
    void cleanUpCreatedFiles() throws Exception {
        for (Path path : pathsToDelete) {
            Files.deleteIfExists(path);
        }
    }

    @Test
    @DisplayName("Should successfully initiate upload and return valid FileId")
    void initiateUpload_Success() {
        // Arrange: When repository saves any metadata, return our pre-defined mock data
        when(metadataRepository.save(any(FileMetadata.class))).thenReturn(mockSavedMetadata);

        // Act: Call the actual service method
        FileInitiateResponse response = fileStorageService.initiateUpload(mockRequest);

        // Assert: Verify the outcomes
        assertNotNull(response);
        assertEquals("test-uuid-1234", response.fileId());
        assertEquals("Upload initialized", response.message());

        // Verify that the repository's save method was called exactly once
        verify(metadataRepository, times(1)).save(any(FileMetadata.class));
    }

    @Test
    @DisplayName("Should upload chunk successfully when checksum is valid")
    void uploadChunk_Success() throws Exception {
        String fileId = "test-uuid-1234";
        byte[] chunkBytes = "hello-chunk".getBytes();
        String checksum = sha256Hex(chunkBytes);
        MockMultipartFile chunkFile = new MockMultipartFile("chunk", "part1.bin", "application/octet-stream", chunkBytes);

        FileMetadata metadata = FileMetadata.builder()
                .id(fileId)
                .totalChunks(5)
                .status(Status.UPLOADING)
                .build();

        when(metadataRepository.findById(fileId)).thenReturn(Optional.of(metadata));
        when(chunkRepository.existsByFileMetadataIdAndSequenceNumber(fileId, 1)).thenReturn(false);

        ChunkUploadResponse response = fileStorageService.uploadChunk(fileId, 1, checksum, chunkFile);

        assertNotNull(response);
        assertEquals(fileId, response.fileId());
        assertEquals(1, response.sequenceNumber());
        assertEquals("Chunk uploaded successfully", response.message());

        ArgumentCaptor<FileChunk> fileChunkCaptor = ArgumentCaptor.forClass(FileChunk.class);
        verify(chunkRepository).save(fileChunkCaptor.capture());
        FileChunk savedChunk = fileChunkCaptor.getValue();

        assertEquals(1, savedChunk.getSequenceNumber());
        assertEquals(checksum, savedChunk.getSha256Checksum());
        assertTrue(Files.exists(Paths.get(savedChunk.getStoregePath())));
        pathsToDelete.add(Paths.get(savedChunk.getStoregePath()));
    }

    @Test
    @DisplayName("Should reject upload when chunk checksum is invalid")
    void uploadChunk_ChecksumMismatch_ThrowsException() {
        String fileId = "test-uuid-1234";
        byte[] chunkBytes = "tampered-content".getBytes();
        MockMultipartFile chunkFile = new MockMultipartFile("chunk", "part1.bin", "application/octet-stream", chunkBytes);

        FileMetadata metadata = FileMetadata.builder()
                .id(fileId)
                .totalChunks(5)
                .status(Status.UPLOADING)
                .build();

        when(metadataRepository.findById(fileId)).thenReturn(Optional.of(metadata));
        when(chunkRepository.existsByFileMetadataIdAndSequenceNumber(fileId, 1)).thenReturn(false);

        assertThrows(
                IntegrityCheckFailedException.class,
                () -> fileStorageService.uploadChunk(fileId, 1, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", chunkFile)
        );

        verify(chunkRepository, never()).save(any(FileChunk.class));
    }

    @Test
    @DisplayName("Should complete upload and merge chunks when checksums are valid")
    void completeUpload_Success() throws Exception {
        String fileId = "complete-uuid-1";
        String originalName = "movie.bin";
        Path storageDir = Paths.get("storage/kura_nodes");
        Files.createDirectories(storageDir);

        byte[] part1 = "hello ".getBytes();
        byte[] part2 = "world".getBytes();
        Path part1Path = storageDir.resolve(fileId + "_part1_test");
        Path part2Path = storageDir.resolve(fileId + "_part2_test");
        Files.write(part1Path, part1);
        Files.write(part2Path, part2);
        pathsToDelete.add(part1Path);
        pathsToDelete.add(part2Path);

        FileMetadata metadata = FileMetadata.builder()
                .id(fileId)
                .originalFileName(originalName)
                .totalChunks(2)
                .status(Status.UPLOADING)
                .build();

        FileChunk chunk1 = FileChunk.builder()
                .sequenceNumber(1)
                .storegePath(part1Path.toAbsolutePath().toString())
                .sha256Checksum(sha256Hex(part1))
                .build();
        FileChunk chunk2 = FileChunk.builder()
                .sequenceNumber(2)
                .storegePath(part2Path.toAbsolutePath().toString())
                .sha256Checksum(sha256Hex(part2))
                .build();

        when(metadataRepository.findById(fileId)).thenReturn(Optional.of(metadata));
        when(chunkRepository.findByFileMetadataIdOrderBySequenceNumberAsc(fileId))
                .thenReturn(new ArrayList<>(List.of(chunk1, chunk2)));

        String finalChecksum = sha256Hex(concat(part1, part2));
        FileCompleteResponse response = fileStorageService.completeUpload(fileId, finalChecksum);

        assertEquals(fileId, response.fileId());
        assertEquals("COMPLETED", response.status());
        assertTrue(Files.exists(Paths.get(response.finalStoragePath())));
        pathsToDelete.add(Paths.get(response.finalStoragePath()));

        verify(metadataRepository).save(argThat(fileMetadata -> fileMetadata.getStatus() == Status.COMPLETED));
        assertFalse(Files.exists(part1Path));
        assertFalse(Files.exists(part2Path));
    }

    @Test
    @DisplayName("Should mark upload failed when final checksum does not match")
    void completeUpload_FinalChecksumMismatch_ThrowsAndMarksFailed() throws Exception {
        String fileId = "complete-uuid-2";
        String originalName = "clip.bin";
        Path storageDir = Paths.get("storage/kura_nodes");
        Files.createDirectories(storageDir);

        byte[] part1 = "abc".getBytes();
        Path part1Path = storageDir.resolve(fileId + "_part1_test");
        Files.write(part1Path, part1);
        pathsToDelete.add(part1Path);

        FileMetadata metadata = FileMetadata.builder()
                .id(fileId)
                .originalFileName(originalName)
                .totalChunks(1)
                .status(Status.UPLOADING)
                .build();

        FileChunk chunk1 = FileChunk.builder()
                .sequenceNumber(1)
                .storegePath(part1Path.toAbsolutePath().toString())
                .sha256Checksum(sha256Hex(part1))
                .build();

        when(metadataRepository.findById(fileId)).thenReturn(Optional.of(metadata));
        when(chunkRepository.findByFileMetadataIdOrderBySequenceNumberAsc(fileId))
                .thenReturn(new ArrayList<>(List.of(chunk1)));

        assertThrows(
                IntegrityCheckFailedException.class,
                () -> fileStorageService.completeUpload(fileId, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
        );

        verify(metadataRepository).save(argThat(fileMetadata -> fileMetadata.getStatus() == Status.FAILED));
        Path finalPath = Paths.get("storage/kura_nodes", fileId + "_" + originalName).toAbsolutePath();
        pathsToDelete.add(finalPath);
    }

    private String sha256Hex(byte[] input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}