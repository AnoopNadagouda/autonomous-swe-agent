package com.example.sweagent.dto;

import java.util.List;

public record LlmPatchResponse(String summary, List<ProposedFile> proposedFiles) {
}
