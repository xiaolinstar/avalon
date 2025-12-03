package cn.xiaolin.avalon.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 房间实体类
 * 代表游戏房间，用于玩家加入和开始游戏
 */
@Entity
@Table(name = "rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "gamePlayers", "createdRooms"})
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