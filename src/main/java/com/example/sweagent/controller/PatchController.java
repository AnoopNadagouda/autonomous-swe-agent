package com.example.sweagent.controller;

import com.example.sweagent.dto.PatchTaskRequest;
import com.example.sweagent.dto.PatchTaskResponse;
import com.example.sweagent.service.PatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class PatchController {

    private final PatchService patchService;

    @PostMapping("/patch")
    public ResponseEntity<PatchTaskResponse> patch(@RequestBody PatchTaskRequest request) {
        return ResponseEntity.ok(patchService.createPatch(request));
    }
}
