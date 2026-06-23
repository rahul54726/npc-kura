package com.npc.kura.dto;

public record ChunkUploadResponse(
        String fileId,
        int sequenceNumber,
        String message
) {}