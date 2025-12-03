package cn.xiaolin.avalon.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 任务实体类
 * 代表阿瓦隆游戏中的一个任务，包含任务的相关信息和状态
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "quests")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Quest {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "players", "quests"})
    private Game game;

    @Column(name = "round_number")
    private Integer roundNumber;

    @Column(name = "required_players")
    private Integer requiredPlayers;

    @Column(name = "required_fails")
    private Integer requiredFails;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "gamePlayers", "createdRooms"})
    private User leader;

    // 提议的队伍成员
    @ManyToMany
    @JoinTable(
        name = "quest_proposed_members",
        joinColumns = @JoinColumn(name = "quest_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "gamePlayers", "createdRooms", "proposedQuests"})
    private List<User> proposedMembers;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}