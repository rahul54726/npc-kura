package com.npc.kura.service;

import com.npc.kura.entity.FileChunk;
import com.npc.kura.entity.FileMetadata;
import com.npc.kura.enums.Status;
import com.npc.kura.repository.FileChunkRepository;
import com.npc.kura.repository.FileMetadataRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class FileStorageService {
    private final FileMetadataRepository metadataRepository;
    private final FileChunkRepository chunkRepository;

    private final String UPLOAD_DIR = "storage/kura_nodes/";

    private final int CHUNK_SIZE = 5 * 1024 * 1024 ;

    @Transactional(rollbackOn = Exception.class)
    public FileMetadata uploadFile(MultipartFile file) throws Exception{
        Path storagePath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(storagePath)){
            Files.createDirectories(storagePath);
        }
        FileMetadata metadata = FileMetadata.builder()
                .originalFileName(file.getOriginalFilename())
                .totalSize(file.getSize())
                .status(Status.UPLOADING)
                .chunks(new ArrayList<>())
                .build();

        metadata = metadataRepository.save(metadata);

        try (InputStream inputStream = file.getInputStream()){
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            int sequence = 1;

            while ((bytesRead = inputStream.read(buffer)) > 0){
                String chunkFileName = metadata.getId() + "_part"+sequence;
                File chunkFile = new File(UPLOAD_DIR + chunkFileName);

                try (FileOutputStream fos = new FileOutputStream(chunkFile)){
                    fos.write(buffer , 0 , bytesRead);
                }
                FileChunk chunk = FileChunk.builder()
                        .fileMetadata(metadata)
                        .sequenceNumber(sequence)
                        .storegePath(chunkFile.getAbsolutePath())
                        .chunkSize(bytesRead)
                        .build();
                chunkRepository.save(chunk);
                metadata.getChunks().add(chunk);
                sequence++;
            }

            metadata.setStatus(Status.COMPLETED);
            metadata.setTotalChunks(sequence - 1);
            return metadataRepository.save(metadata);
        }catch (Exception e){
            metadata.setStatus(Status.FAILED);
            metadataRepository.save(metadata);
            throw new Exception("FIle upload failed during chunking:" + e.getMessage());
        }
    }
}
