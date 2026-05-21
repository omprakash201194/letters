package com.ogautam.letters.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CharacterDto {
    @NotBlank
    private String id;
    @NotBlank
    private String name;
    private String color;
    private String avatar;
    private int orderIndex;
}
