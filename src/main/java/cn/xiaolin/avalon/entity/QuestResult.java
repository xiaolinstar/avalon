package cn.xiaolin.avalon.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "quest_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quest_id", nullable = false)
    private Quest quest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private User player;

    @Column(nullable = false)
    private Boolean success;

    @CreationTimestamp
    @Column(name = "executed_at", nullable = false, updatable = false)
    private LocalDateTime executedAt;
}