package com.ogautam.letters.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PatchSceneNameRequest {
    @NotBlank
    private String name;
}
