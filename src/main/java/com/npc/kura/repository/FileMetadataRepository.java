package com.npc.kura.repository;

import com.npc.kura.entity.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileMetadataRepository extends JpaRepository<FileMetadata,String> {
}
