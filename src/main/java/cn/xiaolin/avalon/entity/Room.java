package cn.xiaolin.avalon.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(name = "room_code", unique = true, nullable = false, length = 10)
    private String roomCode;

    @Column(name = "max_players", nullable = false)
    private Integer maxPlayers;

    // Temporarily comment out role_config to avoid JSONB issues
    // @Column(name = "role_config", columnDefinition = "jsonb")
    // private String roleConfig;

    @Column(nullable = false, length = 20)
    private String status = "waiting";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}