package com.npc.kura.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record FileCompleteRequest(
        @NotBlank(message = "finalChecksum cannot be empty")
        @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "finalChecksum must be a valid SHA-256 hex string")
        String finalChecksum
) {
}
