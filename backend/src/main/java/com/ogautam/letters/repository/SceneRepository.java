package com.ogautam.letters.repository;

import com.ogautam.letters.model.Scene;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SceneRepository extends JpaRepository<Scene, UUID> {

    @Query("SELECT s FROM Scene s WHERE s.userId = :userId ORDER BY s.updatedAt DESC")
    List<Scene> findByUserIdOrderByUpdatedAtDesc(@Param("userId") String userId);

    Optional<Scene> findByIdAndUserId(UUID id, String userId);
}
