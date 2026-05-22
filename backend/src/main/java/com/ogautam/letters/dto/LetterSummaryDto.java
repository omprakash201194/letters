package com.ogautam.letters.dto;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class LetterSummaryDto {
    private UUID id;
    private String recipient;
    private String subject;
    private String mood;
    private LocalDate letterDate;
    private LocalDate sealedUntil;
    private Instant createdAt;
    private Instant updatedAt;
}
