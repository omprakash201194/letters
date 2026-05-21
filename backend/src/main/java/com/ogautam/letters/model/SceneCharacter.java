package com.ogautam.letters.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "scene_characters")
public class SceneCharacter {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 20)
    private String color;

    // reason: base64 data URL from FileReader — can be several KB for small avatars
    @Column(columnDefinition = "TEXT")
    private String avatar;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;
}
