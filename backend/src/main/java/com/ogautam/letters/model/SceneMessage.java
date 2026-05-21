package com.ogautam.letters.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "scene_messages")
public class SceneMessage {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    @Column(name = "char_id", nullable = false, length = 36)
    private String charId;

    @Column(name = "char_name", nullable = false, length = 100)
    private String charName;

    @Column(name = "char_color", length = 20)
    private String charColor;

    // reason: avatar snapshot on the message so playback is correct even if
    // the character is later edited or deleted
    @Column(name = "char_avatar", columnDefinition = "TEXT")
    private String charAvatar;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(length = 10)
    private String time;

    @Column(name = "is_outgoing", nullable = false)
    private boolean outgoing;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;
}
