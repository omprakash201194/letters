package com.ogautam.letters.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MessageDto {
    @NotBlank
    private String id;
    @NotBlank
    private String charId;
    @NotBlank
    private String charName;
    private String charColor;
    private String charAvatar;
    @NotBlank
    private String text;
    private String time;
    private boolean isOutgoing;
    private int orderIndex;
}
