package com.ogautam.letters.service;

import com.ogautam.letters.dto.*;
import com.ogautam.letters.model.Scene;
import com.ogautam.letters.model.SceneCharacter;
import com.ogautam.letters.model.SceneMessage;
import com.ogautam.letters.repository.SceneRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

@Slf4j
@Service
public class SceneService {

    private final SceneRepository sceneRepository;

    public SceneService(SceneRepository sceneRepository) {
        this.sceneRepository = sceneRepository;
    }

    public List<SceneSummaryDto> listScenes(String userId) {
        return sceneRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public SceneDetailDto createScene(String userId, SaveSceneRequest req) {
        Scene scene = new Scene();
        scene.setUserId(userId);
        scene.setName(req.getName());
        applyCharactersAndMessages(scene, req);
        sceneRepository.save(scene);
        log.info("Created scene {} for user {}", scene.getId(), userId);
        return toDetail(scene);
    }

    public Optional<SceneDetailDto> getScene(String userId, UUID sceneId) {
        return sceneRepository.findByIdAndUserId(sceneId, userId)
                .map(this::toDetail);
    }

    @Transactional
    public Optional<SceneDetailDto> updateScene(String userId, UUID sceneId, SaveSceneRequest req) {
        return sceneRepository.findByIdAndUserId(sceneId, userId).map(scene -> {
            scene.setName(req.getName());
            // reason: clear + replace is simpler than diffing; scenes are small (< 100 messages)
            scene.getCharacters().clear();
            scene.getMessages().clear();
            applyCharactersAndMessages(scene, req);
            sceneRepository.save(scene);
            log.info("Updated scene {} for user {}", sceneId, userId);
            return toDetail(scene);
        });
    }

    @Transactional
    public Optional<SceneSummaryDto> renameScene(String userId, UUID sceneId, String name) {
        return sceneRepository.findByIdAndUserId(sceneId, userId).map(scene -> {
            scene.setName(name);
            sceneRepository.save(scene);
            log.info("Renamed scene {} to '{}' for user {}", sceneId, name, userId);
            return toSummary(scene);
        });
    }

    @Transactional
    public boolean deleteScene(String userId, UUID sceneId) {
        return sceneRepository.findByIdAndUserId(sceneId, userId).map(scene -> {
            sceneRepository.delete(scene);
            log.info("Deleted scene {} for user {}", sceneId, userId);
            return true;
        }).orElse(false);
    }

    // --- mapping helpers ---

    private void applyCharactersAndMessages(Scene scene, SaveSceneRequest req) {
        if (req.getCharacters() != null) {
            List<SceneCharacter> chars = IntStream.range(0, req.getCharacters().size())
                    .mapToObj(i -> {
                        CharacterDto dto = req.getCharacters().get(i);
                        SceneCharacter c = new SceneCharacter();
                        c.setId(dto.getId());
                        c.setScene(scene);
                        c.setName(dto.getName());
                        c.setColor(dto.getColor());
                        c.setAvatar(dto.getAvatar());
                        c.setOrderIndex(i);
                        return c;
                    }).toList();
            scene.getCharacters().addAll(chars);
        }

        if (req.getMessages() != null) {
            List<SceneMessage> msgs = IntStream.range(0, req.getMessages().size())
                    .mapToObj(i -> {
                        MessageDto dto = req.getMessages().get(i);
                        SceneMessage m = new SceneMessage();
                        m.setId(dto.getId());
                        m.setScene(scene);
                        m.setCharId(dto.getCharId());
                        m.setCharName(dto.getCharName());
                        m.setCharColor(dto.getCharColor());
                        m.setCharAvatar(dto.getCharAvatar());
                        m.setText(dto.getText());
                        m.setTime(dto.getTime());
                        m.setOutgoing(dto.isOutgoing());
                        m.setOrderIndex(i);
                        return m;
                    }).toList();
            scene.getMessages().addAll(msgs);
        }
    }

    private SceneSummaryDto toSummary(Scene scene) {
        SceneSummaryDto dto = new SceneSummaryDto();
        dto.setId(scene.getId());
        dto.setName(scene.getName());
        dto.setCharacterCount(scene.getCharacters().size());
        dto.setMessageCount(scene.getMessages().size());
        dto.setCreatedAt(scene.getCreatedAt());
        dto.setUpdatedAt(scene.getUpdatedAt());
        return dto;
    }

    private SceneDetailDto toDetail(Scene scene) {
        SceneDetailDto dto = new SceneDetailDto();
        dto.setId(scene.getId());
        dto.setName(scene.getName());
        dto.setCreatedAt(scene.getCreatedAt());
        dto.setUpdatedAt(scene.getUpdatedAt());

        dto.setCharacters(scene.getCharacters().stream().map(c -> {
            CharacterDto cd = new CharacterDto();
            cd.setId(c.getId());
            cd.setName(c.getName());
            cd.setColor(c.getColor());
            cd.setAvatar(c.getAvatar());
            cd.setOrderIndex(c.getOrderIndex());
            return cd;
        }).toList());

        dto.setMessages(scene.getMessages().stream().map(m -> {
            MessageDto md = new MessageDto();
            md.setId(m.getId());
            md.setCharId(m.getCharId());
            md.setCharName(m.getCharName());
            md.setCharColor(m.getCharColor());
            md.setCharAvatar(m.getCharAvatar());
            md.setText(m.getText());
            md.setTime(m.getTime());
            md.setOutgoing(m.isOutgoing());
            md.setOrderIndex(m.getOrderIndex());
            return md;
        }).toList());

        return dto;
    }
}
