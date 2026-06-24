package com.npc.kura.repository;

import com.npc.kura.entity.FileChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileChunkRepository extends JpaRepository<FileChunk , Long> {
    List<FileChunk> findByFileMetadataIdOrderBySequenceNumberAsc(String fileMetadataId);
    boolean existsByFileMetadataIdAndSequenceNumber(String fileMetadataId, int sequenceNumber);
}
