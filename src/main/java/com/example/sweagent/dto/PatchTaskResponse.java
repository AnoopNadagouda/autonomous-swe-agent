package com.example.sweagent.dto;

import java.util.List;

public record PatchTaskResponse(String plan, List<ProposedFile> proposedFiles, String outputDirectoryPath) {
}
