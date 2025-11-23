package cn.xiaolin.avalon.controller;

import cn.xiaolin.avalon.dto.ApiResponse;
import cn.xiaolin.avalon.entity.Game;
import cn.xiaolin.avalon.entity.GamePlayer;
import cn.xiaolin.avalon.entity.Quest;
import cn.xiaolin.avalon.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TestController {
    
    private final GameService gameService;
    
    @GetMapping("/health")
    public ApiResponse<String> healthCheck() {
        return ApiResponse.success("Backend is running successfully!");
    }
    
    @PostMapping("/games/{roomId}/start")
    public ApiResponse<String> startTestGame(@PathVariable String roomId) {
        try {
            // 创建或返回测试游戏
            return ApiResponse.success("test-game-" + roomId);
        } catch (Exception e) {
            return ApiResponse.error("Failed to start test game: " + e.getMessage());
        }
    }
    
    @GetMapping("/games/{gameId}/state")
    public ApiResponse<TestGameState> getTestGameState(@PathVariable String gameId) {
        try {
            // 返回模拟的游戏状态
            TestGameState state = new TestGameState();
            state.setGameId(gameId);
            state.setStatus("playing");
            state.setCurrentRound(1);
            state.setCurrentPhase("team_building");
            
            // 模拟玩家列表
            List<TestPlayer> players = new ArrayList<>();
            TestPlayer player1 = new TestPlayer();
            player1.setPlayerId("player-1");
            player1.setUsername("测试玩家1");
            player1.setAlignment("good");
            player1.setRole("merlin");
            player1.setHost(true);
            player1.setActive(true);
            player1.setSeatNumber(1);
            players.add(player1);
            
            TestPlayer player2 = new TestPlayer();
            player2.setPlayerId("player-2");
            player2.setUsername("测试玩家2");
            player2.setAlignment("evil");
            player2.setRole("assassin");
            player2.setHost(false);
            player2.setActive(true);
            player2.setSeatNumber(2);
            players.add(player2);
            
            state.setPlayers(players);
            
            // 模拟任务列表
            List<TestQuest> quests = new ArrayList<>();
            TestQuest quest1 = new TestQuest();
            quest1.setId("quest-1");
            quest1.setRoundNumber(1);
            quest1.setRequiredPlayers(2);
            quest1.setRequiredFails(1);
            quest1.setStatus("proposing");
            quest1.setLeaderId("player-1");
            quests.add(quest1);
            
            state.setQuests(quests);
            
            return ApiResponse.success(state);
        } catch (Exception e) {
            return ApiResponse.error("Failed to get test game state: " + e.getMessage());
        }
    }
    
    @PostMapping("/games/{gameId}/propose-team")
    public ApiResponse<String> proposeTestTeam(@PathVariable String gameId, @RequestBody ProposeTeamRequest request) {
        try {
            // 模拟队伍组建
            return ApiResponse.success("Team proposed successfully");
        } catch (Exception e) {
            return ApiResponse.error("Failed to propose test team: " + e.getMessage());
        }
    }
    
    @PostMapping("/games/{gameId}/vote")
    public ApiResponse<String> submitTestVote(@PathVariable String gameId, @RequestBody VoteRequest request) {
        try {
            // 模拟投票
            return ApiResponse.success("Vote submitted successfully");
        } catch (Exception e) {
            return ApiResponse.error("Failed to submit test vote: " + e.getMessage());
        }
    }
    
    @PostMapping("/games/{gameId}/execute-quest")
    public ApiResponse<String> executeTestQuest(@PathVariable String gameId, @RequestBody ExecuteQuestRequest request) {
        try {
            // 模拟任务执行
            return ApiResponse.success("Quest executed successfully");
        } catch (Exception e) {
            return ApiResponse.error("Failed to execute test quest: " + e.getMessage());
        }
    }
    
    // 内部DTO类
    public static class TestGameState {
        private String gameId;
        private String status;
        private int currentRound;
        private String currentPhase;
        private List<TestPlayer> players;
        private List<TestQuest> quests;
        
        // getters and setters
        public String getGameId() { return gameId; }
        public void setGameId(String gameId) { this.gameId = gameId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getCurrentRound() { return currentRound; }
        public void setCurrentRound(int currentRound) { this.currentRound = currentRound; }
        public String getCurrentPhase() { return currentPhase; }
        public void setCurrentPhase(String currentPhase) { this.currentPhase = currentPhase; }
        public List<TestPlayer> getPlayers() { return players; }
        public void setPlayers(List<TestPlayer> players) { this.players = players; }
        public List<TestQuest> getQuests() { return quests; }
        public void setQuests(List<TestQuest> quests) { this.quests = quests; }
    }
    
    public static class TestPlayer {
        private String playerId;
        private String username;
        private String role;
        private String alignment;
        private boolean isHost;
        private boolean isActive;
        private int seatNumber;
        
        // getters and setters
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getAlignment() { return alignment; }
        public void setAlignment(String alignment) { this.alignment = alignment; }
        public boolean isHost() { return isHost; }
        public void setHost(boolean host) { isHost = host; }
        public boolean isActive() { return isActive; }
        public void setActive(boolean active) { isActive = active; }
        public int getSeatNumber() { return seatNumber; }
        public void setSeatNumber(int seatNumber) { this.seatNumber = seatNumber; }
    }
    
    public static class TestQuest {
        private String id;
        private int roundNumber;
        private int requiredPlayers;
        private int requiredFails;
        private String status;
        private String leaderId;
        
        // getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public int getRoundNumber() { return roundNumber; }
        public void setRoundNumber(int roundNumber) { this.roundNumber = roundNumber; }
        public int getRequiredPlayers() { return requiredPlayers; }
        public void setRequiredPlayers(int requiredPlayers) { this.requiredPlayers = requiredPlayers; }
        public int getRequiredFails() { return requiredFails; }
        public void setRequiredFails(int requiredFails) { this.requiredFails = requiredFails; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getLeaderId() { return leaderId; }
        public void setLeaderId(String leaderId) { this.leaderId = leaderId; }
    }
    
    public static class ProposeTeamRequest {
        private List<String> playerIds;
        
        public List<String> getPlayerIds() { return playerIds; }
        public void setPlayerIds(List<String> playerIds) { this.playerIds = playerIds; }
    }
    
    public static class VoteRequest {
        private String voteType;
        
        public String getVoteType() { return voteType; }
        public void setVoteType(String voteType) { this.voteType = voteType; }
    }
    
    public static class ExecuteQuestRequest {
        private boolean success;
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
    }
}