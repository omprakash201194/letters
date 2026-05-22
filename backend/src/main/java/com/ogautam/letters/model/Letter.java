package com.ogautam.letters.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "letters", indexes = {
        @Index(name = "idx_letters_user_id", columnList = "user_id")
})
public class Letter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(nullable = false, length = 200)
    private String recipient;

    @Column(length = 300)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String content;

    // reason: emoji-keyed mood slug stored as-is (e.g. "grateful"), never queried beyond list
    @Column(length = 30)
    private String mood;

    @Column(name = "letter_date")
    private LocalDate letterDate;

    // reason: null = not sealed; future date = time capsule, content withheld by frontend until date arrives
    @Column(name = "sealed_until")
    private LocalDate sealedUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
