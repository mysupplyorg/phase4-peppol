package com.mysupply.phase4.peppolstandalone.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RetrieveSearchResult {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private List<UUID> documentIds;

    public RetrieveSearchResult() {
        documentIds = new ArrayList<UUID>();
    }

    public void addDocumentId(UUID documentId) {
        this.documentIds.add(documentId);
    }

    public List<UUID> getDocumentIds() {
        return this.documentIds;
    }

    public void setDocumentIds(List<UUID> documentIds) {
        this.documentIds = documentIds;
    }

    public String getAsJsonString() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"Failed to serialize result\"}";
        }
    }
}
