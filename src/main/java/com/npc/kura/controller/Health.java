package com.npc.kura.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class Health {
    @GetMapping
    public ResponseEntity<?> health(){
        Map<?,?> map = Map.of("Status" , "System is running fine",
                "checking time" , LocalDateTime.now());
        return new ResponseEntity<>(map , HttpStatus.OK);
    }
}
