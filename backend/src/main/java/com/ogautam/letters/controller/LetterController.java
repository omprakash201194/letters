package com.ogautam.letters.controller;

import com.ogautam.letters.dto.LetterDetailDto;
import com.ogautam.letters.dto.LetterSummaryDto;
import com.ogautam.letters.dto.SaveLetterRequest;
import com.ogautam.letters.security.FirebaseUserPrincipal;
import com.ogautam.letters.service.LetterService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/letters")
public class LetterController {

    private final LetterService letterService;

    public LetterController(LetterService letterService) {
        this.letterService = letterService;
    }

    @GetMapping
    public ResponseEntity<List<LetterSummaryDto>> listLetters(
            @AuthenticationPrincipal FirebaseUserPrincipal principal) {
        return ResponseEntity.ok(letterService.listLetters(principal.getUid()));
    }

    @PostMapping
    public ResponseEntity<LetterDetailDto> createLetter(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @Valid @RequestBody SaveLetterRequest req) {
        LetterDetailDto created = letterService.createLetter(principal.getUid(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LetterDetailDto> getLetter(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable UUID id) {
        return letterService.getLetter(principal.getUid(), id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<LetterDetailDto> updateLetter(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody SaveLetterRequest req) {
        return letterService.updateLetter(principal.getUid(), id, req)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLetter(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable UUID id) {
        boolean deleted = letterService.deleteLetter(principal.getUid(), id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
