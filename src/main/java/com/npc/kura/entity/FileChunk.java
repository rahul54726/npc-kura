package com.npc.kura.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "file_chunks")
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
    private int sequenceNumber;
    private String storegePath;
    private  long chunkSize;
}
