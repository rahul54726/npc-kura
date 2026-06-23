package com.npc.kura.service;

import com.npc.kura.dto.FileInitiateRequest;
import com.npc.kura.dto.FileInitiateResponse;
import com.npc.kura.entity.FileMetadata;
import com.npc.kura.enums.Status;
import com.npc.kura.repository.FileChunkRepository;
import com.npc.kura.repository.FileMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    @DisplayName("Should successfully initiate upload and return valid FileId")
    void initiateUpload_Success() throws Exception {
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
}