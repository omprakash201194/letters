package com.ogautam.letters.dto;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class SceneSummaryDto {
    private UUID id;
    private String name;
    private int characterCount;
    private int messageCount;
    private Instant createdAt;
    private Instant updatedAt;
}
