package cn.xiaolin.avalon.controller;

import cn.xiaolin.avalon.dto.*;
import cn.xiaolin.avalon.entity.GamePlayer;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @author xingxiaolin xing.xiaolin@foxmail.com
 * @Description 5人游戏控制器测试
 * @create 2025/12/07
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FivePlayerGameControllerTest {

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
    private List<String> playerTokens;

    private String roomCode;
    private String roomId;

    @BeforeEach
    void setUp() throws Exception {
        playerTokens = new ArrayList<>();
        // 添加足够的玩家到房间（至少5个用于游戏开始）
        for (int i = 0; i < 5; i++) {
            // 创建测试用户
            User player = new User();
            player.setUsername("player" + i + "_" + UUID.randomUUID().toString().substring(0, 8));
            player.setEmail("player" + i + "_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
            player.setPasswordHash("hashed_password");
            player = userRepository.save(player);

            String playerToken = jwtUtil.generateToken(player.getId(), player.getUsername());

            playerTokens.add("Bearer " + playerToken);
        }


        // 创建测试房间
        authorizationHeader = playerTokens.get(0);
        CreateRoomRequest createRequest = new CreateRoomRequest();
        createRequest.setMaxPlayers(5);

        String responseStr = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", authorizationHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取房间信息
        ApiResponse<RoomResponse> apiResponse = objectMapper.readValue(responseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, RoomResponse.class));
        RoomResponse roomResponse = apiResponse.getData();
        roomCode = roomResponse.getRoomCode();
        roomId = roomResponse.getRoomId().toString();

        for(int i=1; i<5; i++) {
            mockMvc.perform(post("/api/rooms/{roomCode}/join", roomCode)
                            .header("Authorization", playerTokens.get(i)))
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
     * MULTI-GAME-RUN-TC-001: 5人游戏完整流程测试
     * 测试目的: 验证5人游戏能够正常进行完整的游戏流程，包括所有任务轮次和胜利条件判定。
     */
    @Test
    void whenFivePlayersPlayFullGame_thenGameCompletesSuccessfully() throws Exception {
        // 首先开始游戏
        mockMvc.perform(post("/api/games/{roomId}/start", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 获取实际的游戏ID
        String roomResponseStr = mockMvc.perform(get("/api/rooms/{roomCode}", roomCode)
                .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取游戏ID
        ApiResponse<RoomResponse> roomApiResponse = objectMapper.readValue(roomResponseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, RoomResponse.class));
        RoomResponse roomResponse = roomApiResponse.getData();
        String gameId = roomResponse.getGameId().toString();

        // 开始第一个任务
        mockMvc.perform(post("/api/games/{gameId}/quests?isFirstQuest=true", gameId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 获取房间中的所有玩家ID
        String playersResponseStr = mockMvc.perform(get("/api/rooms/{roomCode}/players", roomCode)
                .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取玩家信息
        ApiResponse<RoomPlayersResponse> playersApiResponse = objectMapper.readValue(playersResponseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, RoomPlayersResponse.class));
        List<PlayerInfoResponse> players = playersApiResponse.getData().getPlayers();

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
            ApiResponse<List<Map<String, Object>>> gameApiResponse = objectMapper.readValue(gameResponseStr,
                    TypeFactory.defaultInstance().constructParametricType(ApiResponse.class,
                            TypeFactory.defaultInstance().constructCollectionType(List.class, Map.class)));
            List<Map<String, Object>> quests = gameApiResponse.getData();

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
            int requiredPlayers = getRequiredPlayersForFivePlayerRound(round);
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
                Thread.sleep(200);
                
                // 验证游戏状态已更新为ENDED，胜利者为good阵营
                mockMvc.perform(get("/api/games/{gameId}/status", gameId)
                        .header("Authorization", authorizationHeader))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("ended"))
                        .andExpect(jsonPath("$.data.winner").value("good"));

                // 注释掉WebSocket验证以避免测试不稳定
                 verify(messagingTemplate, atLeastOnce())
                         .convertAndSend(anyString(), any(Object.class));

            }
        }
    }

    /**
     * MULTI-GAME-RUN-TC-007: 刺客刺杀成功
     * 测试目的: 验证当正义阵营成功完成3个任务时，刺客刺杀成功
     */
    @Test
    void whenGoodTeamWinsByCompletingThreeQuests_thenGameEndsWithGoodVictory() throws Exception {
        // 首先开始游戏
        mockMvc.perform(post("/api/games/{roomId}/start", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 获取实际的游戏ID
        String roomResponseStr = mockMvc.perform(get("/api/rooms/{roomCode}", roomCode)
                .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取游戏ID
        ApiResponse<RoomResponse> roomApiResponse = objectMapper.readValue(roomResponseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, RoomResponse.class));
        RoomResponse roomResponse = roomApiResponse.getData();
        String gameId = roomResponse.getGameId().toString();

        // 开始第一个任务
        mockMvc.perform(post("/api/games/{gameId}/quests?isFirstQuest=true", gameId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 获取房间中的所有玩家ID
        String playersResponseStr = mockMvc.perform(get("/api/rooms/{roomCode}/players", roomCode)
                .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取玩家信息
        ApiResponse<RoomPlayersResponse> playersApiResponse = objectMapper.readValue(playersResponseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, RoomPlayersResponse.class));
        List<PlayerInfoResponse> players = playersApiResponse.getData().getPlayers();

        // 找到刺客玩家
        PlayerInfoResponse assassinPlayer = players.stream()
                .filter(p -> "assassin".equals(p.getRole()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到刺客玩家"));

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
            ApiResponse<List<Map<String, Object>>> gameApiResponse = objectMapper.readValue(gameResponseStr,
                    TypeFactory.defaultInstance().constructParametricType(ApiResponse.class,
                            TypeFactory.defaultInstance().constructCollectionType(List.class, Map.class)));
            List<Map<String, Object>> quests = gameApiResponse.getData();

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
            int requiredPlayers = getRequiredPlayersForFivePlayerRound(round);
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
                Thread.sleep(200);
                
                // 验证游戏状态已更新为ENDED，胜利者为good阵营
                mockMvc.perform(get("/api/games/{gameId}/status", gameId)
                        .header("Authorization", authorizationHeader))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("ended"))
                        .andExpect(jsonPath("$.data.winner").value("good"));

                // 获取刺客的JWT令牌
                String assassinToken = "Bearer " + jwtUtil.generateToken(assassinPlayer.getPlayerId(), assassinPlayer.getUsername());

                // 获取梅林玩家信息
                PlayerInfoResponse merlinPlayer = players.stream()
                        .filter(p -> "merlin".equals(p.getRole()))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("未找到梅林玩家"));

                // 获取游戏中的梅林玩家（GamePlayer）
                String gamePlayersResponseStr = mockMvc.perform(get("/api/games/{gameId}/players", gameId)
                        .header("Authorization", authorizationHeader))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

                // 解析响应以获取游戏中的玩家信息
                ApiResponse<List<GamePlayer>> gamePlayersApiResponse = objectMapper.readValue(gamePlayersResponseStr,
                        TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, 
                                TypeFactory.defaultInstance().constructCollectionType(List.class, GamePlayer.class)));
                List<GamePlayer> gamePlayers = gamePlayersApiResponse.getData();

                // 找到游戏中的梅林玩家ID
                UUID merlinGamePlayerId = gamePlayers.stream()
                        .filter(gp -> gp.getUser().getId().equals(merlinPlayer.getPlayerId()))
                        .findFirst()
                        .map(GamePlayer::getId)
                        .orElseThrow(() -> new RuntimeException("未找到游戏中的梅林玩家"));

                // 刺客执行刺杀梅林
                AssassinationRequest assassinationRequest = new AssassinationRequest();
                assassinationRequest.setTargetPlayerId(merlinGamePlayerId);

                mockMvc.perform(post("/api/games/{gameId}/assassinate", gameId)
                                .header("Authorization", assassinToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(assassinationRequest)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data").value(true)); // 刺杀成功

                // 验证游戏结果更新为邪恶阵营获胜
                mockMvc.perform(get("/api/games/{gameId}/status", gameId)
                        .header("Authorization", authorizationHeader))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("ended"))
                        .andExpect(jsonPath("$.data.winner").value("evil"));

                // 注释掉WebSocket验证以避免测试不稳定
                 verify(messagingTemplate, atLeastOnce())
                         .convertAndSend(anyString(), any(Object.class));
            }
        }
    }

    /**
     * MULTI-GAME-RUN-TC-008: 邪恶阵营获胜场景测试
     * 测试目的: 验证当邪恶阵营成功破坏3个任务时，游戏正确结束并宣布邪恶阵营获胜。
     */
    @Test
    void whenEvilTeamWinsByFailingThreeQuests_thenGameEndsWithEvilVictory() throws Exception {
        // 首先开始游戏
        mockMvc.perform(post("/api/games/{roomId}/start", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 获取实际的游戏ID
        String roomResponseStr = mockMvc.perform(get("/api/rooms/{roomCode}", roomCode)
                .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取游戏ID
        ApiResponse<RoomResponse> roomApiResponse = objectMapper.readValue(roomResponseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, RoomResponse.class));
        RoomResponse roomResponse = roomApiResponse.getData();
        String gameId = roomResponse.getGameId().toString();

        // 开始第一个任务
        mockMvc.perform(post("/api/games/{gameId}/quests?isFirstQuest=true", gameId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 获取房间中的所有玩家ID
        String playersResponseStr = mockMvc.perform(get("/api/rooms/{roomCode}/players", roomCode)
                .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取玩家信息
        ApiResponse<RoomPlayersResponse> playersApiResponse = objectMapper.readValue(playersResponseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, RoomPlayersResponse.class));
        List<PlayerInfoResponse> players = playersApiResponse.getData().getPlayers();

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
            ApiResponse<List<Map<String, Object>>> gameApiResponse = objectMapper.readValue(gameResponseStr,
                    TypeFactory.defaultInstance().constructParametricType(ApiResponse.class,
                            TypeFactory.defaultInstance().constructCollectionType(List.class, Map.class)));
            List<Map<String, Object>> quests = gameApiResponse.getData();

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
            int requiredPlayers = getRequiredPlayersForFivePlayerRound(round);
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
                Thread.sleep(200);
                
                // 验证游戏状态已更新为ENDED，胜利者为evil阵营
                mockMvc.perform(get("/api/games/{gameId}/status", gameId)
                        .header("Authorization", authorizationHeader))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("ended"))
                        .andExpect(jsonPath("$.data.winner").value("evil"));

                // 验证WebSocket消息已发送
                verify(messagingTemplate, atLeastOnce())
                        .convertAndSend(anyString(), any(Object.class));

            }
        }
    }

    /**
     * MULTI-GAME-RUN-TC-009: 投票失败导致队长轮换测试
     * 测试目的: 验证当队伍提议投票未通过时，队长正确轮换到下一个玩家。
     */
    @Test
    void whenTeamProposalIsRejected_thenLeaderRotatesToNextPlayer() throws Exception {
        // 首先开始游戏
        mockMvc.perform(post("/api/games/{roomId}/start", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 获取实际的游戏ID
        String roomResponseStr = mockMvc.perform(get("/api/rooms/{roomCode}", roomCode)
                .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取游戏ID
        ApiResponse<RoomResponse> roomApiResponse = objectMapper.readValue(roomResponseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, RoomResponse.class));
        RoomResponse roomResponse = roomApiResponse.getData();
        String gameId = roomResponse.getGameId().toString();

        // 开始第一个任务
        mockMvc.perform(post("/api/games/{gameId}/quests?isFirstQuest=true", gameId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 获取房间中的所有玩家ID
        String playersResponseStr = mockMvc.perform(get("/api/rooms/{roomCode}/players", roomCode)
                .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取玩家信息
        ApiResponse<RoomPlayersResponse> playersApiResponse = objectMapper.readValue(playersResponseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, RoomPlayersResponse.class));
        List<PlayerInfoResponse> players = playersApiResponse.getData().getPlayers();

        // 获取当前任务信息以确定真正的队长
        String gameResponseStr = mockMvc.perform(get("/api/games/{gameId}/quests", gameId)
                .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取任务信息
        ApiResponse<List<Map<String, Object>>> gameApiResponse = objectMapper.readValue(gameResponseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class,
                        TypeFactory.defaultInstance().constructCollectionType(List.class, Map.class)));
        List<Map<String, Object>> quests = gameApiResponse.getData();

        // 获取当前任务（第一个未完成的任务）
        Map<String, Object> currentQuest = quests.stream()
                .filter(q -> "proposing".equals(q.get("status")))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到当前任务"));

        // 获取初始队长信息
        Map<String, Object> initialLeader = (Map<String, Object>) currentQuest.get("leader");
        String initialLeaderId = (String) initialLeader.get("id");

        // 找到初始队长玩家
        PlayerInfoResponse initialLeaderPlayer = players.stream()
                .filter(p -> p.getPlayerId().toString().equals(initialLeaderId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到初始队长玩家"));

        // 为初始队长玩家生成JWT令牌
        String initialLeaderToken = "Bearer " + jwtUtil.generateToken(initialLeaderPlayer.getPlayerId(), initialLeaderPlayer.getUsername());

        // 找到另一个玩家用于组成2人队伍（5人游戏第一轮需要2人）
        PlayerInfoResponse otherPlayer = players.stream()
                .filter(p -> !p.getPlayerId().toString().equals(initialLeaderId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到其他玩家"));

        // 初始队长提议一个合理的队伍（2人），但是我们会让投票失败
        ProposeTeamRequest proposeRequest = new ProposeTeamRequest();
        // 提议一个合理的队伍（2人）
        proposeRequest.setPlayerIds(List.of(initialLeaderPlayer.getPlayerId(), otherPlayer.getPlayerId()));

        mockMvc.perform(post("/api/games/{gameId}/propose-team", gameId)
                        .header("Authorization", initialLeaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proposeRequest)))
                .andExpect(status().isOk());

        // 大部分玩家投反对票，使投票未通过
        for (PlayerInfoResponse player : players) {
            // 排除初始队长自己
            if (!player.getPlayerId().toString().equals(initialLeaderId)) {
                String voterToken = "Bearer " + jwtUtil.generateToken(player.getPlayerId(), player.getUsername());

                VoteRequest voteRequest = new VoteRequest();
                voteRequest.setVoteType("reject"); // 投反对票

                mockMvc.perform(post("/api/games/{gameId}/vote", gameId)
                                .header("Authorization", voterToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(voteRequest)))
                        .andExpect(status().isOk());
            }
        }

        // 等待一段时间确保状态更新
        Thread.sleep(200);

        // 再次获取任务信息以验证队长已经轮换
        String updatedGameResponseStr = mockMvc.perform(get("/api/games/{gameId}/quests", gameId)
                .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取更新后的任务信息
        ApiResponse<List<Map<String, Object>>> updatedGameApiResponse = objectMapper.readValue(updatedGameResponseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class,
                        TypeFactory.defaultInstance().constructCollectionType(List.class, Map.class)));
        List<Map<String, Object>> updatedQuests = updatedGameApiResponse.getData();

        // 获取当前任务（仍然是第一个未完成的任务）
        Map<String, Object> updatedCurrentQuest = updatedQuests.stream()
                .filter(q -> "proposing".equals(q.get("status")))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到当前任务"));

        // 获取更新后的队长信息
        Map<String, Object> updatedLeader = (Map<String, Object>) updatedCurrentQuest.get("leader");
        String updatedLeaderId = (String) updatedLeader.get("id");

        // 验证队长已经轮换（不是原来的队长）
        assertNotEquals(initialLeaderId, updatedLeaderId, "队长应该已经轮换到下一个玩家");

        // 注释掉WebSocket验证以避免测试不稳定
         verify(messagingTemplate, atLeastOnce())
                 .convertAndSend(eq("/topic/game/" + gameId), any(GameMessage.class));
    }

    /**
     * MULTI-GAME-RUN-TC-010: 连续失败任务导致邪恶阵营获胜测试
     * 测试目的: 验证当连续失败任务达到限制时，游戏正确结束并宣布邪恶阵营获胜。
     */
    @Test
    void whenThreeConsecutiveQuestsFail_thenEvilTeamWins() throws Exception {
        // 首先开始游戏
        mockMvc.perform(post("/api/games/{roomId}/start", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 获取实际的游戏ID
        String roomResponseStr = mockMvc.perform(get("/api/rooms/{roomCode}", roomCode)
                .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取游戏ID
        ApiResponse<RoomResponse> roomApiResponse = objectMapper.readValue(roomResponseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, RoomResponse.class));
        RoomResponse roomResponse = roomApiResponse.getData();
        String gameId = roomResponse.getGameId().toString();

        // 开始第一个任务
        mockMvc.perform(post("/api/games/{gameId}/quests?isFirstQuest=true", gameId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 获取房间中的所有玩家ID
        String playersResponseStr = mockMvc.perform(get("/api/rooms/{roomCode}/players", roomCode)
                .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取玩家信息
        ApiResponse<RoomPlayersResponse> playersApiResponse = objectMapper.readValue(playersResponseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, RoomPlayersResponse.class));
        List<PlayerInfoResponse> players = playersApiResponse.getData().getPlayers();

        // 连续失败3个任务
        for (int round = 1; round <= 3; round++) {
            // 获取当前任务信息以确定真正的队长
            String gameResponseStr = mockMvc.perform(get("/api/games/{gameId}/quests", gameId)
                    .header("Authorization", authorizationHeader))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            // 解析响应以获取任务信息
            ApiResponse<List<Map<String, Object>>> gameApiResponse = objectMapper.readValue(gameResponseStr,
                    TypeFactory.defaultInstance().constructParametricType(ApiResponse.class,
                            TypeFactory.defaultInstance().constructCollectionType(List.class, Map.class)));
            List<Map<String, Object>> quests = gameApiResponse.getData();

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
            int requiredPlayers = getRequiredPlayersForFivePlayerRound(round);
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
                Thread.sleep(200);
                
                // 验证游戏状态已更新为ENDED，胜利者为evil阵营
                mockMvc.perform(get("/api/games/{gameId}/status", gameId)
                        .header("Authorization", authorizationHeader))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("ended"))
                        .andExpect(jsonPath("$.data.winner").value("evil"));

                // 注释掉WebSocket验证以避免测试不稳定
                 verify(messagingTemplate, atLeastOnce())
                         .convertAndSend(anyString(), any(Object.class));

            }
        }
    }

    /**
     * 根据轮次获取5人游戏中所需玩家数
     *
     * @param round 轮次
     * @return 所需玩家数
     */
    private int getRequiredPlayersForFivePlayerRound(int round) {
        return switch (round) {
            case 2, 4, 5 -> 3;
            default -> 2;
        };
    }


}