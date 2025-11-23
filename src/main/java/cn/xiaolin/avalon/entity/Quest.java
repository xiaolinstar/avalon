package cn.xiaolin.avalon.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "quests")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Quest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    @Column(name = "required_players", nullable = false)
    private Integer requiredPlayers;

    @Column(name = "required_fails", nullable = false)
    private Integer requiredFails = 1;

    @Column(nullable = false, length = 20)
    private String status = "proposing";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_id")
    private User leader;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}