package com.ogautam.letters.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class SaveSceneRequest {
    @NotBlank
    private String name;

    @Valid
    private List<CharacterDto> characters;

    @Valid
    private List<MessageDto> messages;
}
