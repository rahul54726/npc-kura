package com.npc.kura.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "file_chunks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"file_id", "sequence_number"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class FileChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "file_id",nullable = false)
    @JsonIgnore
    private FileMetadata fileMetadata;
    @Column(name = "sequence_number")
    private int sequenceNumber;
    private String storegePath;
    private long chunkSize;
    @Column(name = "sha256_checksum", nullable = false, length = 64)
    private String sha256Checksum;
}
