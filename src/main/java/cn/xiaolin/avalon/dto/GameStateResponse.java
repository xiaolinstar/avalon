package cn.xiaolin.avalon.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameStateResponse {
    private UUID gameId;
    private String status;
    private int currentRound;
    private String currentPhase;
    private UUID currentLeaderId;
    private List<PlayerInfo> players;
    private List<QuestInfo> quests;
    private GameResult result;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerInfo {
        private UUID playerId;
        private String username;
        private String role;
        private String alignment;
        private Boolean isHost;
        private int seatNumber;
        private Boolean isActive;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestInfo {
        private UUID questId;
        private int roundNumber;
        private int requiredPlayers;
        private int requiredFails;
        private String status;
        private UUID leaderId;
        private List<UUID> proposedMembers;
        private Integer approveCount;
        private Integer rejectCount;
        private Boolean questResult;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GameResult {
        private String winner;
        private String winType;
        private String assassinName;
        private UUID assassinatedPlayerId;
        private boolean assassinationSuccess;
    }
}