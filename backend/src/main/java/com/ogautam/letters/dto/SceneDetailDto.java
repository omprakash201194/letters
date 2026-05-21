package com.ogautam.letters.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class SceneDetailDto {
    private UUID id;
    private String name;
    private List<CharacterDto> characters;
    private List<MessageDto> messages;
    private Instant createdAt;
    private Instant updatedAt;
}
