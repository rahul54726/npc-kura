package com.npc.kura.dto;

public record FileCompleteResponse(
        String fileId,
        String finalStoragePath,
        String status
) {}