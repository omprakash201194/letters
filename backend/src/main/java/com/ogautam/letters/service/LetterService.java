package com.ogautam.letters.service;

import com.ogautam.letters.dto.LetterDetailDto;
import com.ogautam.letters.dto.LetterSummaryDto;
import com.ogautam.letters.dto.SaveLetterRequest;
import com.ogautam.letters.model.Letter;
import com.ogautam.letters.repository.LetterRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class LetterService {

    private final LetterRepository letterRepository;

    public LetterService(LetterRepository letterRepository) {
        this.letterRepository = letterRepository;
    }

    public List<LetterSummaryDto> listLetters(String userId) {
        return letterRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public LetterDetailDto createLetter(String userId, SaveLetterRequest req) {
        Letter letter = new Letter();
        letter.setUserId(userId);
        applyFields(letter, req);
        letterRepository.save(letter);
        log.info("Created letter {} for user {}", letter.getId(), userId);
        return toDetail(letter);
    }

    public Optional<LetterDetailDto> getLetter(String userId, UUID letterId) {
        return letterRepository.findByIdAndUserId(letterId, userId)
                .map(this::toDetail);
    }

    @Transactional
    public Optional<LetterDetailDto> updateLetter(String userId, UUID letterId, SaveLetterRequest req) {
        return letterRepository.findByIdAndUserId(letterId, userId).map(letter -> {
            applyFields(letter, req);
            letterRepository.save(letter);
            log.info("Updated letter {} for user {}", letterId, userId);
            return toDetail(letter);
        });
    }

    @Transactional
    public boolean deleteLetter(String userId, UUID letterId) {
        return letterRepository.findByIdAndUserId(letterId, userId).map(letter -> {
            letterRepository.delete(letter);
            log.info("Deleted letter {} for user {}", letterId, userId);
            return true;
        }).orElse(false);
    }

    // --- helpers ---

    private void applyFields(Letter letter, SaveLetterRequest req) {
        letter.setRecipient(req.getRecipient());
        letter.setSubject(req.getSubject());
        letter.setContent(req.getContent());
        letter.setMood(req.getMood());
        letter.setLetterDate(req.getLetterDate());
        letter.setSealedUntil(req.getSealedUntil());
    }

    private LetterSummaryDto toSummary(Letter letter) {
        LetterSummaryDto dto = new LetterSummaryDto();
        dto.setId(letter.getId());
        dto.setRecipient(letter.getRecipient());
        dto.setSubject(letter.getSubject());
        dto.setMood(letter.getMood());
        dto.setLetterDate(letter.getLetterDate());
        dto.setSealedUntil(letter.getSealedUntil());
        dto.setCreatedAt(letter.getCreatedAt());
        dto.setUpdatedAt(letter.getUpdatedAt());
        return dto;
    }

    private LetterDetailDto toDetail(Letter letter) {
        LetterDetailDto dto = new LetterDetailDto();
        dto.setId(letter.getId());
        dto.setRecipient(letter.getRecipient());
        dto.setSubject(letter.getSubject());
        dto.setContent(letter.getContent());
        dto.setMood(letter.getMood());
        dto.setLetterDate(letter.getLetterDate());
        dto.setSealedUntil(letter.getSealedUntil());
        dto.setCreatedAt(letter.getCreatedAt());
        dto.setUpdatedAt(letter.getUpdatedAt());
        return dto;
    }
}
