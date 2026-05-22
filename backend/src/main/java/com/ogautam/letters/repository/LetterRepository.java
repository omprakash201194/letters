package com.ogautam.letters.repository;

import com.ogautam.letters.model.Letter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LetterRepository extends JpaRepository<Letter, UUID> {

    @Query("SELECT l FROM Letter l WHERE l.userId = :userId ORDER BY l.updatedAt DESC")
    List<Letter> findByUserIdOrderByUpdatedAtDesc(@Param("userId") String userId);

    Optional<Letter> findByIdAndUserId(UUID id, String userId);
}
