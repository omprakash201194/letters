package com.ogautam.letters.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SaveLetterRequest {

    @NotBlank
    private String recipient;

    private String subject;

    private String content;

    private String mood;

    private LocalDate letterDate;
    private LocalDate sealedUntil;
}
