package com.npc.kura.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record FileInitiateRequest(
    @NotBlank(message = "File name cannot be empty")
    String originalFileName,

    @Min(value = 1 , message = "Total size must be greater than 0")
    long totalSize,
    @Min(value = 1 , message = "At least 1 chunk is required")
    int totalChunks
){}

