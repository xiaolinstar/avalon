package cn.xiaolin.avalon.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 任务实体类
 * 代表游戏中的一轮任务，每轮任务可能有多个提议，但只有一个会被执行
 */
@Entity
@Table(name = "quests")
@Data
@NoArgsConstructor
@AllArgsConstructor
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

    // 提议的队伍成员（仅用于向后兼容，新逻辑应通过Proposal实体获取）
    @ManyToMany
    @JoinTable(
        name = "quest_proposed_members",
        joinColumns = @JoinColumn(name = "quest_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "gamePlayers", "createdRooms", "proposedQuests"})
    private List<User> proposedMembers;
    
    // 添加与Proposal的关联
    @OneToMany(mappedBy = "quest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "quest"})
    private List<Proposal> proposals;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}