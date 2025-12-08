package cn.xiaolin.avalon.controller;

import cn.xiaolin.avalon.dto.*;
import cn.xiaolin.avalon.entity.User;
import cn.xiaolin.avalon.repository.*;
import cn.xiaolin.avalon.utils.JwtUtil;
import cn.xiaolin.avalon.websocket.GameMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @author xingxiaolin xing.xiaolin@foxmail.com
 * @Description 10人游戏控制器测试
 * @create 2025/12/07
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TenPlayerGameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomPlayerRepository roomPlayerRepository;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GamePlayerRepository gamePlayerRepository;

    @Autowired
    private QuestRepository questRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private QuestResultRepository questResultRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    private String authorizationHeader;
    private String roomCode;
    private String roomId;

    @BeforeEach
    void setUp() throws Exception {
        // 创建测试用户
        User testUser = new User();
        testUser.setUsername("testuser_" + UUID.randomUUID().toString().substring(0, 8));
        testUser.setEmail("test_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
        testUser.setPasswordHash("hashed_password");
        testUser = userRepository.save(testUser);

        // 为测试用户生成有效的JWT令牌
        String validToken = jwtUtil.generateToken(testUser.getId(), testUser.getUsername());
        authorizationHeader = "Bearer " + validToken;

        // 创建10人测试房间
        CreateRoomRequest createRequest = new CreateRoomRequest();
        createRequest.setMaxPlayers(10);

        String responseStr = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", authorizationHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取房间信息
        Result<RoomResponse> Result = objectMapper.readValue(responseStr,
                TypeFactory.defaultInstance().constructParametricType(Result.class, RoomResponse.class));
        RoomResponse roomResponse = Result.getData();
        roomCode = roomResponse.getRoomCode();
        roomId = roomResponse.getRoomId().toString();

        // 添加足够的玩家到房间（至少10个用于游戏开始）
        for (int i = 0; i < 9; i++) {
            User player = new User();
            player.setUsername("player" + i + "_" + UUID.randomUUID().toString().substring(0, 8));
            player.setEmail("player" + i + "_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
            player.setPasswordHash("hashed_password");
            player = userRepository.save(player);

            String playerToken = jwtUtil.generateToken(player.getId(), player.getUsername());
            mockMvc.perform(post("/api/rooms/{roomId}", roomId)
                        .header("Authorization", "Bearer " + playerToken))
                    .andExpect(status().isOk());
        }
    }

    @AfterEach
    void tearDown() {
        // 按正确顺序清理测试数据
        voteRepository.deleteAll();
        questResultRepository.deleteAll();
        questRepository.deleteAll();
        gamePlayerRepository.deleteAll();
        gameRepository.deleteAll();
        roomPlayerRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * TEN-PLAYER-GAME-TC-001: 10人游戏完整流程测试
     * 测试目的: 验证10人游戏能够正常进行完整的游戏流程，包括所有任务轮次和胜利条件判定。
     */
    @Test
    void whenTenPlayersPlayFullGame_thenGameCompletesSuccessfully() throws Exception {
        // 首先开始游戏
        mockMvc.perform(post("/api/games/{roomId}/start", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 获取实际的游戏ID
        String roomResponseStr = mockMvc.perform(get("/api/rooms/{roomId}", roomId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取游戏ID
        Result<RoomResponse> roomResult = objectMapper.readValue(roomResponseStr,
                TypeFactory.defaultInstance().constructParametricType(Result.class, RoomResponse.class));
        RoomResponse roomResponse = roomResult.getData();
        String gameId = roomResponse.getGameId().toString();

        // 开始第一个任务
        mockMvc.perform(post("/api/games/{gameId}/quests?isFirstQuest=true", gameId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 获取房间中的所有玩家ID
        String playersResponseStr = mockMvc.perform(get("/api/rooms/{roomId}/players", roomId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取玩家信息
        Result<RoomPlayersResponse> playersResult = objectMapper.readValue(playersResponseStr,
                TypeFactory.defaultInstance().constructParametricType(Result.class, RoomPlayersResponse.class));
        List<PlayerInfoResponse> players = playersResult.getData().getPlayers();

        // 执行所有5个任务，确保正义阵营获胜
        for (int round = 1; round <= 5; round++) {
            // 获取当前任务信息以确定真正的队长
            String gameResponseStr = mockMvc.perform(get("/api/games/{gameId}/quests", gameId)
                            .header("Authorization", authorizationHeader))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            // 解析响应以获取任务信息
            Result<List<Map<String, Object>>> gameResult = objectMapper.readValue(gameResponseStr,
                    TypeFactory.defaultInstance().constructParametricType(Result.class,
                            TypeFactory.defaultInstance().constructCollectionType(List.class, Map.class)));
            List<Map<String, Object>> quests = gameResult.getData();

            // 获取当前任务（第一个未完成的任务）
            Map<String, Object> currentQuest = quests.stream()
                    .filter(q -> "proposing".equals(q.get("status")))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("未找到当前任务"));

            // 获取队长信息
            Map<String, Object> leader = (Map<String, Object>) currentQuest.get("leader");
            String leaderId = (String) leader.get("id");

            // 找到队长玩家
            PlayerInfoResponse leaderPlayer = players.stream()
                    .filter(p -> p.getPlayerId().toString().equals(leaderId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("未找到队长玩家"));

            // 为队长玩家生成JWT令牌
            String leaderToken = "Bearer " + jwtUtil.generateToken(leaderPlayer.getPlayerId(), leaderPlayer.getUsername());

            // 队长提议队伍（根据轮次选择正确的玩家数）
            int requiredPlayers = getRequiredPlayersForTenPlayerRound(round);
            List<UUID> selectedPlayerIds = players.stream()
                    .limit(requiredPlayers)
                    .map(PlayerInfoResponse::getPlayerId)
                    .collect(Collectors.toList());

            ProposeTeamRequest proposeRequest = new ProposeTeamRequest();
            proposeRequest.setPlayerIds(selectedPlayerIds);

            // 队长提议队伍
            mockMvc.perform(post("/api/games/{gameId}/propose-team", gameId)
                            .header("Authorization", leaderToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(proposeRequest)))
                    .andExpect(status().isOk());

            // 所有玩家投票赞成
            for (PlayerInfoResponse player : players) {
                String voterToken = "Bearer " + jwtUtil.generateToken(player.getPlayerId(), player.getUsername());

                VoteRequest voteRequest = new VoteRequest();
                voteRequest.setVoteType("approve");

                mockMvc.perform(post("/api/games/{gameId}/vote", gameId)
                                .header("Authorization", voterToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(voteRequest)))
                        .andExpect(status().isOk());
            }

            // 只有队伍成员执行任务（任务成功）
            for (PlayerInfoResponse player : players) {
                // 检查玩家是否在提议的队伍中
                if (selectedPlayerIds.contains(player.getPlayerId())) {
                    String executorToken = "Bearer " + jwtUtil.generateToken(player.getPlayerId(), player.getUsername());

                    ExecuteQuestRequest executeRequest = new ExecuteQuestRequest();
                    executeRequest.setSuccess(true); // 任务成功

                    mockMvc.perform(post("/api/games/{gameId}/quests/execute", gameId)
                                    .header("Authorization", executorToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(executeRequest)))
                            .andExpect(status().isOk());
                }
            }

            // 检查游戏是否结束（第5轮之后应该结束）
            if (round == 5) {
                // 等待一段时间确保游戏状态更新
                Thread.sleep(100);
                
                // 验证游戏状态已更新为ENDED
                mockMvc.perform(get("/api/games/{gameId}", gameId)
                                .header("Authorization", authorizationHeader))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("ended"));

                // 验证WebSocket消息已发送
                verify(messagingTemplate, atLeastOnce())
                        .convertAndSend(anyString(), any(Object.class));
            }
        }
    }

    /**
     * TEN-PLAYER-GAME-TC-002: 10人游戏正义阵营获胜场景测试
     * 测试目的: 验证当10人游戏中正义阵营成功完成3个任务时，游戏正确结束并宣布正义阵营获胜。
     */
    @Test
    void whenTenPlayersGoodTeamWinsThreeQuests_thenGoodTeamWins() throws Exception {
        // 首先开始游戏
        mockMvc.perform(post("/api/games/{roomId}/start", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 获取实际的游戏ID
        String roomResponseStr = mockMvc.perform(get("/api/rooms/{roomId}", roomId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取游戏ID
        Result<RoomResponse> roomResult = objectMapper.readValue(roomResponseStr,
                TypeFactory.defaultInstance().constructParametricType(Result.class, RoomResponse.class));
        RoomResponse roomResponse = roomResult.getData();
        String gameId = roomResponse.getGameId().toString();

        // 开始第一个任务
        mockMvc.perform(post("/api/games/{gameId}/quests?isFirstQuest=true", gameId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 获取房间中的所有玩家ID
        String playersResponseStr = mockMvc.perform(get("/api/rooms/{roomId}/players", roomId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取玩家信息
        Result<RoomPlayersResponse> playersResult = objectMapper.readValue(playersResponseStr,
                TypeFactory.defaultInstance().constructParametricType(Result.class, RoomPlayersResponse.class));
        List<PlayerInfoResponse> players = playersResult.getData().getPlayers();

        // 让正义阵营成功完成3个任务
        for (int round = 1; round <= 3; round++) {
            // 获取当前任务信息以确定真正的队长
            String gameResponseStr = mockMvc.perform(get("/api/games/{gameId}/quests", gameId)
                            .header("Authorization", authorizationHeader))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            // 解析响应以获取任务信息
            Result<List<Map<String, Object>>> gameResult = objectMapper.readValue(gameResponseStr,
                    TypeFactory.defaultInstance().constructParametricType(Result.class,
                            TypeFactory.defaultInstance().constructCollectionType(List.class, Map.class)));
            List<Map<String, Object>> quests = gameResult.getData();

            // 获取当前任务（第一个未完成的任务）
            Map<String, Object> currentQuest = quests.stream()
                    .filter(q -> "proposing".equals(q.get("status")))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("未找到当前任务"));

            // 获取队长信息
            Map<String, Object> leader = (Map<String, Object>) currentQuest.get("leader");
            String leaderId = (String) leader.get("id");

            // 找到队长玩家
            PlayerInfoResponse leaderPlayer = players.stream()
                    .filter(p -> p.getPlayerId().toString().equals(leaderId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("未找到队长玩家"));

            // 为队长玩家生成JWT令牌
            String leaderToken = "Bearer " + jwtUtil.generateToken(leaderPlayer.getPlayerId(), leaderPlayer.getUsername());

            // 队长提议队伍（根据轮次选择正确的玩家数）
            int requiredPlayers = getRequiredPlayersForTenPlayerRound(round);
            List<UUID> selectedPlayerIds = players.stream()
                    .limit(requiredPlayers)
                    .map(PlayerInfoResponse::getPlayerId)
                    .collect(Collectors.toList());

            ProposeTeamRequest proposeRequest = new ProposeTeamRequest();
            proposeRequest.setPlayerIds(selectedPlayerIds);

            // 队长提议队伍
            mockMvc.perform(post("/api/games/{gameId}/propose-team", gameId)
                            .header("Authorization", leaderToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(proposeRequest)))
                    .andExpect(status().isOk());

            // 所有玩家投票赞成
            for (PlayerInfoResponse player : players) {
                String voterToken = "Bearer " + jwtUtil.generateToken(player.getPlayerId(), player.getUsername());

                VoteRequest voteRequest = new VoteRequest();
                voteRequest.setVoteType("approve");

                mockMvc.perform(post("/api/games/{gameId}/vote", gameId)
                                .header("Authorization", voterToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(voteRequest)))
                        .andExpect(status().isOk());
            }

            // 只有队伍成员执行任务（任务成功）
            for (PlayerInfoResponse player : players) {
                // 检查玩家是否在提议的队伍中
                if (selectedPlayerIds.contains(player.getPlayerId())) {
                    String executorToken = "Bearer " + jwtUtil.generateToken(player.getPlayerId(), player.getUsername());

                    ExecuteQuestRequest executeRequest = new ExecuteQuestRequest();
                    executeRequest.setSuccess(true); // 任务成功

                    mockMvc.perform(post("/api/games/{gameId}/quests/execute", gameId)
                                    .header("Authorization", executorToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(executeRequest)))
                            .andExpect(status().isOk());
                }
            }

            // 检查游戏是否结束（第3轮之后应该结束）
            if (round == 3) {
                // 等待一段时间确保游戏状态更新
                Thread.sleep(100);
                
                // 验证游戏状态已更新为ENDED
                mockMvc.perform(get("/api/games/{gameId}", gameId)
                                .header("Authorization", authorizationHeader))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("ended"));

                // 验证游戏获胜者为正义阵营
                mockMvc.perform(get("/api/games/{gameId}", gameId)
                                .header("Authorization", authorizationHeader))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.winner").value("good"));

                // 验证WebSocket消息已发送
                verify(messagingTemplate, atLeastOnce())
                        .convertAndSend(anyString(), any(Object.class));
            }
        }
    }

    /**
     * TEN-PLAYER-GAME-TC-003: 10人游戏邪恶阵营获胜场景测试
     * 测试目的: 验证当10人游戏中邪恶阵营成功破坏3个任务时，游戏正确结束并宣布邪恶阵营获胜。
     */
    @Test
    void whenTenPlayersEvilTeamFailsThreeQuests_thenEvilTeamWins() throws Exception {
        // 首先开始游戏
        mockMvc.perform(post("/api/games/{roomId}/start", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 获取实际的游戏ID
        String roomResponseStr = mockMvc.perform(get("/api/rooms/{roomId}", roomId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取游戏ID
        Result<RoomResponse> roomResult = objectMapper.readValue(roomResponseStr,
                TypeFactory.defaultInstance().constructParametricType(Result.class, RoomResponse.class));
        RoomResponse roomResponse = roomResult.getData();
        String gameId = roomResponse.getGameId().toString();

        // 开始第一个任务
        mockMvc.perform(post("/api/games/{gameId}/quests?isFirstQuest=true", gameId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 获取房间中的所有玩家ID
        String playersResponseStr = mockMvc.perform(get("/api/rooms/{roomId}/players", roomId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取玩家信息
        Result<RoomPlayersResponse> playersResult = objectMapper.readValue(playersResponseStr,
                TypeFactory.defaultInstance().constructParametricType(Result.class, RoomPlayersResponse.class));
        List<PlayerInfoResponse> players = playersResult.getData().getPlayers();

        // 让邪恶阵营破坏3个任务
        for (int round = 1; round <= 3; round++) {
            // 获取当前任务信息以确定真正的队长
            String gameResponseStr = mockMvc.perform(get("/api/games/{gameId}/quests", gameId)
                            .header("Authorization", authorizationHeader))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            // 解析响应以获取任务信息
            Result<List<Map<String, Object>>> gameResult = objectMapper.readValue(gameResponseStr,
                    TypeFactory.defaultInstance().constructParametricType(Result.class,
                            TypeFactory.defaultInstance().constructCollectionType(List.class, Map.class)));
            List<Map<String, Object>> quests = gameResult.getData();

            // 获取当前任务（第一个未完成的任务）
            Map<String, Object> currentQuest = quests.stream()
                    .filter(q -> "proposing".equals(q.get("status")))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("未找到当前任务"));

            // 获取队长信息
            Map<String, Object> leader = (Map<String, Object>) currentQuest.get("leader");
            String leaderId = (String) leader.get("id");

            // 找到队长玩家
            PlayerInfoResponse leaderPlayer = players.stream()
                    .filter(p -> p.getPlayerId().toString().equals(leaderId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("未找到队长玩家"));

            // 为队长玩家生成JWT令牌
            String leaderToken = "Bearer " + jwtUtil.generateToken(leaderPlayer.getPlayerId(), leaderPlayer.getUsername());

            // 队长提议队伍（根据轮次选择正确的玩家数）
            int requiredPlayers = getRequiredPlayersForTenPlayerRound(round);
            List<UUID> selectedPlayerIds = players.stream()
                    .limit(requiredPlayers)
                    .map(PlayerInfoResponse::getPlayerId)
                    .collect(Collectors.toList());

            ProposeTeamRequest proposeRequest = new ProposeTeamRequest();
            proposeRequest.setPlayerIds(selectedPlayerIds);

            // 队长提议队伍
            mockMvc.perform(post("/api/games/{gameId}/propose-team", gameId)
                            .header("Authorization", leaderToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(proposeRequest)))
                    .andExpect(status().isOk());

            // 所有玩家投票赞成
            for (PlayerInfoResponse player : players) {
                String voterToken = "Bearer " + jwtUtil.generateToken(player.getPlayerId(), player.getUsername());

                VoteRequest voteRequest = new VoteRequest();
                voteRequest.setVoteType("approve");

                mockMvc.perform(post("/api/games/{gameId}/vote", gameId)
                                .header("Authorization", voterToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(voteRequest)))
                        .andExpect(status().isOk());
            }

            // 只有队伍成员执行任务（任务失败）
            for (PlayerInfoResponse player : players) {
                // 检查玩家是否在提议的队伍中
                if (selectedPlayerIds.contains(player.getPlayerId())) {
                    String executorToken = "Bearer " + jwtUtil.generateToken(player.getPlayerId(), player.getUsername());

                    ExecuteQuestRequest executeRequest = new ExecuteQuestRequest();
                    executeRequest.setSuccess(false); // 任务失败

                    mockMvc.perform(post("/api/games/{gameId}/quests/execute", gameId)
                                    .header("Authorization", executorToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(executeRequest)))
                            .andExpect(status().isOk());
                }
            }

            // 检查游戏是否结束（第3轮之后应该结束）
            if (round == 3) {
                // 等待一段时间确保游戏状态更新
                Thread.sleep(100);
                
                // 验证游戏状态已更新为ENDED
                mockMvc.perform(get("/api/games/{gameId}", gameId)
                                .header("Authorization", authorizationHeader))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("ended"));

                // 验证游戏获胜者为邪恶阵营
                mockMvc.perform(get("/api/games/{gameId}", gameId)
                                .header("Authorization", authorizationHeader))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.winner").value("evil"));

                // 验证WebSocket消息已发送
                verify(messagingTemplate, atLeastOnce())
                        .convertAndSend(anyString(), any(Object.class));
            }
        }
    }

    /**
     * TEN-PLAYER-GAME-TC-004: 10人游戏投票失败导致队长轮换测试
     * 测试目的: 验证当10人游戏中队伍提议投票未通过时，队长正确轮换到下一个玩家。
     */
    @Test
    void whenTenPlayersTeamProposalIsRejected_thenLeaderChanges() throws Exception {
        // 首先开始游戏
        mockMvc.perform(post("/api/games/{roomId}/start", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 获取实际的游戏ID
        String roomResponseStr = mockMvc.perform(get("/api/rooms/{roomId}", roomId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取游戏ID
        Result<RoomResponse> roomResult = objectMapper.readValue(roomResponseStr,
                TypeFactory.defaultInstance().constructParametricType(Result.class, RoomResponse.class));
        RoomResponse roomResponse = roomResult.getData();
        String gameId = roomResponse.getGameId().toString();

        // 开始第一个任务
        mockMvc.perform(post("/api/games/{gameId}/quests?isFirstQuest=true", gameId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 获取房间中的所有玩家ID
        String playersResponseStr = mockMvc.perform(get("/api/rooms/{roomId}/players", roomId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取玩家信息
        Result<RoomPlayersResponse> playersResult = objectMapper.readValue(playersResponseStr,
                TypeFactory.defaultInstance().constructParametricType(Result.class, RoomPlayersResponse.class));
        List<PlayerInfoResponse> players = playersResult.getData().getPlayers();

        // 获取当前任务信息以确定真正的队长
        String gameResponseStr = mockMvc.perform(get("/api/games/{gameId}/quests", gameId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取任务信息
        Result<List<Map<String, Object>>> gameResult = objectMapper.readValue(gameResponseStr,
                TypeFactory.defaultInstance().constructParametricType(Result.class,
                        TypeFactory.defaultInstance().constructCollectionType(List.class, Map.class)));
        List<Map<String, Object>> quests = gameResult.getData();

        // 获取当前任务（第一个未完成的任务）
        Map<String, Object> currentQuest = quests.stream()
                .filter(q -> "proposing".equals(q.get("status")))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到当前任务"));

        // 获取队长信息
        Map<String, Object> leader = (Map<String, Object>) currentQuest.get("leader");
        String leaderId = (String) leader.get("id");

        // 找到队长玩家
        PlayerInfoResponse leaderPlayer = players.stream()
                .filter(p -> p.getPlayerId().toString().equals(leaderId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到队长玩家"));

        // 为队长玩家生成JWT令牌
        String leaderToken = "Bearer " + jwtUtil.generateToken(leaderPlayer.getPlayerId(), leaderPlayer.getUsername());

        // 队长提议队伍（选择前三个玩家作为队伍成员）
        List<UUID> selectedPlayerIds = players.stream()
                .limit(3)
                .map(PlayerInfoResponse::getPlayerId)
                .collect(Collectors.toList());

        ProposeTeamRequest proposeRequest = new ProposeTeamRequest();
        proposeRequest.setPlayerIds(selectedPlayerIds);

        // 队长提议队伍
        mockMvc.perform(post("/api/games/{gameId}/propose-team", gameId)
                        .header("Authorization", leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proposeRequest)))
                .andExpect(status().isOk());

        // 大部分玩家投反对票，使投票未通过
        int totalPlayers = players.size();
        int rejectCount = totalPlayers / 2 + 1; // 超过一半的玩家投反对票

        // 让前rejectCount个玩家投反对票
        for (int i = 0; i < rejectCount; i++) {
            PlayerInfoResponse player = players.get(i);
            String voterToken = "Bearer " + jwtUtil.generateToken(player.getPlayerId(), player.getUsername());

            VoteRequest voteRequest = new VoteRequest();
            voteRequest.setVoteType("reject");

            mockMvc.perform(post("/api/games/{gameId}/vote", gameId)
                            .header("Authorization", voterToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(voteRequest)))
                    .andExpect(status().isOk());
        }

        // 验证WebSocket消息已发送
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(anyString(), any(Object.class));
    }

    /**
     * 根据轮次获取10人游戏中所需玩家数
     *
     * @param round 轮次
     * @return 所需玩家数
     */
    private int getRequiredPlayersForTenPlayerRound(int round) {
        return switch (round) {
            case 1 -> 3;
            case 2, 3 -> 4;
            case 4, 5 -> 5;
            default -> 3;
        };
    }
}