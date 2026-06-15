package com.npc.kura.controller;

import com.npc.kura.entity.FileMetadata;
import com.npc.kura.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/files")
@Slf4j
@RequiredArgsConstructor
public class FileController {
    private final FileStorageService fileStorageService;
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file")MultipartFile file){
        if (file.isEmpty()){
            log.info("Please select a file to upload");
            return ResponseEntity.badRequest().body("Please select a file to upload");
        }
        try {
            FileMetadata savedMetadata = fileStorageService.uploadFile(file);
            return ResponseEntity.ok(savedMetadata);
        }catch (Exception e){
            log.error("Not able to upload file");
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
}
