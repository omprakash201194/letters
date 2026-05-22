package com.ogautam.letters.controller;

import com.ogautam.letters.dto.PatchSceneNameRequest;
import com.ogautam.letters.dto.SaveSceneRequest;
import com.ogautam.letters.dto.SceneDetailDto;
import com.ogautam.letters.dto.SceneSummaryDto;
import com.ogautam.letters.security.FirebaseUserPrincipal;
import com.ogautam.letters.service.SceneService;
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
@RequestMapping("/scenes")
public class SceneController {

    private final SceneService sceneService;

    public SceneController(SceneService sceneService) {
        this.sceneService = sceneService;
    }

    @GetMapping
    public ResponseEntity<List<SceneSummaryDto>> listScenes(
            @AuthenticationPrincipal FirebaseUserPrincipal principal) {
        return ResponseEntity.ok(sceneService.listScenes(principal.getUid()));
    }

    @PostMapping
    public ResponseEntity<SceneDetailDto> createScene(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @Valid @RequestBody SaveSceneRequest req) {
        SceneDetailDto created = sceneService.createScene(principal.getUid(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SceneDetailDto> getScene(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable UUID id) {
        return sceneService.getScene(principal.getUid(), id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<SceneDetailDto> updateScene(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody SaveSceneRequest req) {
        return sceneService.updateScene(principal.getUid(), id, req)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/name")
    public ResponseEntity<SceneSummaryDto> renameScene(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody PatchSceneNameRequest req) {
        return sceneService.renameScene(principal.getUid(), id, req.getName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteScene(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable UUID id) {
        boolean deleted = sceneService.deleteScene(principal.getUid(), id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
