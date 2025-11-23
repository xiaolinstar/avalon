package cn.xiaolin.avalon.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameStatisticsResponse {
    private UUID gameId;
    private String winner;
    private String winType;
    private int totalRounds;
    private int successfulQuests;
    private int failedQuests;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private long durationMinutes;
    private List<PlayerStatistics> playerStatistics;
    private List<QuestStatistics> questStatistics;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerStatistics {
        private UUID playerId;
        private String username;
        private String role;
        private String alignment;
        private Boolean isHost;
        private int voteCount;
        private int approveVotes;
        private int rejectVotes;
        private int questParticipations;
        private int questSuccesses;
        private int questFailures;
        private boolean wasAssassinated;
        private boolean wasAssassin;
        private double winRate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestStatistics {
        private UUID questId;
        private int roundNumber;
        private String status;
        private boolean result;
        private int requiredPlayers;
        private int requiredFails;
        private int approveVotes;
        private int rejectVotes;
        private String leaderName;
        private List<String> teamMembers;
        private List<String> questResults;
    }
}