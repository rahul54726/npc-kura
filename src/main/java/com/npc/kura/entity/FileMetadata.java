package com.npc.kura.entity;

import com.npc.kura.enums.Status;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "file-metaData")
@Getter
@Setter
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileMetadata {

    @Id
    private String id;
    private String originalFileName;
    private long totalSize;
    private int totalChunks;

    private Status status;

    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "fileMetadata" , cascade = CascadeType.ALL)
    private List<FileChunk> chunks;

    @PrePersist
    public void prePersist(){
        if (this.id == null){
            this.id = UUID.randomUUID().toString();
        }
        this.createdAt = LocalDateTime.now();
    }

}
