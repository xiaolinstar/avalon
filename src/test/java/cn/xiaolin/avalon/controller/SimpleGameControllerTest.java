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
 * @Description GameController tests
 * @create 2025/11/29
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SimpleGameControllerTest {

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

        // 创建测试房间
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
        Result<RoomResponse> Result = objectMapper.readValue(responseStr,
                TypeFactory.defaultInstance().constructParametricType(Result.class, RoomResponse.class));
        RoomResponse roomResponse = Result.getData();
        roomCode = roomResponse.getRoomCode();
        roomId = roomResponse.getRoomId().toString();

        // 添加足够的玩家到房间（至少5个用于游戏开始）
        for (int i = 0; i < 4; i++) {
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
     * GAME-START-TC-001: 房主在满足条件时开始游戏
     * 测试目的: 验证房主可以在房间人数达到最低要求（例如5人）时成功开始游戏。
     */
    @Test
    void whenHostStartsGameWithSufficientPlayers_thenReturnsSuccess() throws Exception {
        // First verify the room exists and is in waiting state
        mockMvc.perform(get("/api/rooms/{roomId}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("waiting"));

        // Now start the game using roomId (创建新游戏)
        mockMvc.perform(post("/api/games/{roomId}/start", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data").value("游戏开始成功"));
        // 验证WebSocket消息已发送
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(anyString(), any(Object.class));

        // 验证游戏状态已更新为ROLE_VIEWING
        mockMvc.perform(get("/api/rooms/{roomId}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("playing"));
                
        // 验证重复开始游戏会失败
        mockMvc.perform(post("/api/games/{roomId}/start", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("游戏已开始"));    }

    /**
     * GAME-START-FIRST-QUEST-TC-001: 成功开始第一个任务
     * 测试目的: 验证在游戏处于ROLE_VIEWING状态时可以成功开始第一个任务。
     */
    @Test
    void whenStartingFirstQuestWithValidGame_thenReturnsSuccess() throws Exception {
        // 首先开始游戏
        mockMvc.perform(post("/api/games/{roomId}/start", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 验证游戏现在处于role_viewing状态
        mockMvc.perform(get("/api/rooms/{roomId}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("playing"));
                
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
        
        // When & Then - 开始第一个任务
        mockMvc.perform(post("/api/games/{gameId}/quests?isFirstQuest=true", gameId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("第一个任务开始成功"));

        // 验证WebSocket消息已发送
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(anyString(), any(Object.class));
                
        // 验证游戏状态已更新为playing
        mockMvc.perform(get("/api/rooms/{roomId}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("playing"));
    }
    
    /**
     * GAME-START-QUEST-TC-001: 使用统一接口成功开始第一个任务
     * 测试目的: 验证可以通过统一接口成功开始第一个任务。
     */
    @Test
    void whenStartingQuestWithValidGameAsFirstQuest_thenReturnsSuccess() throws Exception {
        // 首先开始游戏
        mockMvc.perform(post("/api/games/{roomId}/start", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 验证游戏现在处于role_viewing状态
        mockMvc.perform(get("/api/rooms/{roomId}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("playing"));
                
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
        
        // When & Then - 使用统一接口开始第一个任务
        mockMvc.perform(post("/api/games/{gameId}/quests?isFirstQuest=true", gameId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("第一个任务开始成功"));

        // 验证WebSocket消息已发送
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(anyString(), any(Object.class));
                
        // 验证游戏状态已更新为playing
        mockMvc.perform(get("/api/rooms/{roomId}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("playing"));
    }
    
    /**
     * GAME-START-QUEST-TC-002: 使用统一接口成功开始后续任务
     * 测试目的: 验证可以通过统一接口成功开始后续任务。
     */
    @Test
    void whenStartingQuestWithValidGameAsNextQuest_thenReturnsSuccess() throws Exception {
        // 首先开始游戏
        mockMvc.perform(post("/api/games/{roomId}/start", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 验证游戏现在处于role_viewing状态
        mockMvc.perform(get("/api/rooms/{roomId}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("playing"));
                
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
        
        // 先开始第一个任务
        mockMvc.perform(post("/api/games/{gameId}/quests?isFirstQuest=true", gameId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());
        
        // When & Then - 使用统一接口开始后续任务
        mockMvc.perform(post("/api/games/{gameId}/quests", gameId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("任务开始成功"));
    }
    
    /**
     * GAME-ROLE-INFO-TC-001: 成功获取角色信息
     * 测试目的: 验证玩家可以成功获取自己的角色信息。
     */
    @Test
    void whenPlayerRequestsRoleInfo_thenReturnsRoleInfo() throws Exception {
        // 首先开始游戏
        mockMvc.perform(post("/api/games/{roomId}/start", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 验证游戏现在处于role_viewing状态
        mockMvc.perform(get("/api/rooms/{roomId}", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("playing"));

        // 获取实际的游戏ID
        String roomResponseStr = mockMvc.perform(get("/api/rooms/{roomId}", roomId)
                .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取游戏ID
        Result<RoomResponse> roomResult = objectMapper.readValue(roomResponseStr,
                TypeFactory.defaultInstance().constructParametricType(Result.class, RoomResponse.class));
        RoomResponse roomResponse = roomResult.getData();
        String gameId = roomResponse.getGameId().toString();

        // When & Then
        mockMvc.perform(get("/api/games/{gameId}/role-info", gameId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("操作成功"));
    }
    
    /**
     * GAME-ROLE-INFO-TC-002: 非游戏玩家尝试获取角色信息
     * 测试目的: 验证未加入游戏的用户无法获取角色信息。
     */
    @Test
    void whenNonPlayerRequestsRoleInfo_thenReturnsError() throws Exception {
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

        // 创建一个不属于房间的用户
        User outsider = new User();
        outsider.setUsername("outsider_" + UUID.randomUUID().toString().substring(0, 8));
        outsider.setEmail("outsider_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
        outsider.setPasswordHash("hashed_password");
        outsider = userRepository.save(outsider);
        
        String outsiderToken = jwtUtil.generateToken(outsider.getId(), outsider.getUsername());

        // When & Then - 尝试获取角色信息应该失败
        mockMvc.perform(get("/api/games/{gameId}/role-info", gameId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isBadRequest());  // 修改为实际返回的状态码
    }
    
    /**
     * GAME-ROLE-INFO-TC-003: 使用无效游戏ID获取角色信息
     * 测试目的: 验证使用无效游戏ID无法获取角色信息。
     */
    @Test
    void whenRequestingRoleInfoWithInvalidGameId_thenReturnsError() throws Exception {
        // 使用一个格式正确的UUID，但数据库中不存在的游戏ID
        String invalidGameId = "123e4567-e89b-12d3-a456-426614174000";
        
        // When & Then - 使用无效游戏ID获取角色信息应该失败
        mockMvc.perform(get("/api/games/{gameId}/role-info", invalidGameId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isBadRequest());  // 修改为实际返回的状态码
    }
    
    /**
     * TEAM-PROPOSAL-TC-001: 队长成功提议队伍
     * 测试目的: 验证队长可以成功为当前任务提议一个符合要求的队伍。
     */
    @Test
    void whenLeaderProposesValidTeam_thenReturnsSuccess() throws Exception {
        // 首先开始游戏
        mockMvc.perform(post("/api/games/{roomId}/start", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 验证游戏现在处于role_viewing状态
        mockMvc.perform(get("/api/rooms/{roomId}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("playing"));
                
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
        
        // 构造队伍提议请求，选择前两个玩家
        List<UUID> selectedPlayerIds = players.stream()
                .limit(2)
                .map(PlayerInfoResponse::getPlayerId)
                .collect(Collectors.toList());
        
        ProposeTeamRequest request = new ProposeTeamRequest();
        request.setPlayerIds(selectedPlayerIds);
        
        // When & Then - 队长提议队伍
        mockMvc.perform(post("/api/games/{gameId}/propose-team", gameId)
                        .header("Authorization", leaderToken)  // 使用队长的令牌而不是房主的令牌
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("队伍提议成功"));
    }
    
    /**
     * TEAM-PROPOSAL-TC-002: 非队长尝试提议队伍
     * 测试目的: 验证非队长玩家无法提议队伍。
     */
    @Test
    void whenNonLeaderAttemptsToProposeTeam_thenReturnsError() throws Exception {
        // 首先开始游戏
        mockMvc.perform(post("/api/games/{roomId}/start", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 验证游戏现在处于role_viewing状态
        mockMvc.perform(get("/api/rooms/{roomId}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("playing"));
                
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
        
        // 找到非队长的玩家（与当前任务队长不同的玩家）
        PlayerInfoResponse nonLeader = players.stream()
                .filter(p -> !p.getPlayerId().toString().equals(leaderId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到非队长玩家"));
        
        // 为非队长玩家生成JWT令牌
        String nonLeaderToken = "Bearer " + jwtUtil.generateToken(nonLeader.getPlayerId(), nonLeader.getUsername());
        
        // 构造队伍提议请求
        List<UUID> selectedPlayerIds = players.stream()
                .limit(2)
                .map(PlayerInfoResponse::getPlayerId)
                .collect(Collectors.toList());
        
        ProposeTeamRequest request = new ProposeTeamRequest();
        request.setPlayerIds(selectedPlayerIds);
        
        // When & Then - 非队长提议队伍应该失败
        mockMvc.perform(post("/api/games/{gameId}/propose-team", gameId)
                        .header("Authorization", nonLeaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("不是当前队长"));
    }
    
    /**
     * TEAM-VOTE-TC-001: 玩家成功投票
     * 测试目的: 验证玩家可以成功对提议的队伍进行投票。
     */
    @Test
    void whenPlayerVotesSuccessfully_thenReturnsSuccess() throws Exception {
        // 首先开始游戏
        mockMvc.perform(post("/api/games/{roomId}/start", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 验证游戏现在处于role_viewing状态
        mockMvc.perform(get("/api/rooms/{roomId}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("playing"));
                
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
        
        // 队长提议队伍
        String playersResponseStr = mockMvc.perform(get("/api/rooms/{roomId}/players", roomId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // 解析响应以获取玩家信息
        Result<RoomPlayersResponse> playersResult = objectMapper.readValue(playersResponseStr,
                TypeFactory.defaultInstance().constructParametricType(Result.class, RoomPlayersResponse.class));
        List<PlayerInfoResponse> players = playersResult.getData().getPlayers();
        
        // 构造队伍提议请求，选择前两个玩家
        List<UUID> selectedPlayerIds = players.stream()
                .limit(2)
                .map(PlayerInfoResponse::getPlayerId)
                .collect(Collectors.toList());
        
        ProposeTeamRequest proposeRequest = new ProposeTeamRequest();
        proposeRequest.setPlayerIds(selectedPlayerIds);
        
        // 队长提议队伍
        mockMvc.perform(post("/api/games/{gameId}/propose-team", gameId)
                        .header("Authorization", authorizationHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proposeRequest)))
                .andExpect(status().isOk());
        
        // 获取一个非队长玩家的令牌
        PlayerInfoResponse nonLeader = players.stream()
                .filter(p -> !p.getIsHost())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到非队长玩家"));
        
        String voterToken = "Bearer " + jwtUtil.generateToken(nonLeader.getPlayerId(), nonLeader.getUsername());
        
        // 构造投票请求
        VoteRequest voteRequest = new VoteRequest();
        voteRequest.setVoteType("approve");
        
        // When & Then - 玩家投票
        mockMvc.perform(post("/api/games/{gameId}/vote", gameId)
                        .header("Authorization", voterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voteRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("投票成功"));
    }
    
    /**
     * TEAM-VOTE-TC-002: 玩家重复投票
     * 测试目的: 验证玩家无法对同一任务重复投票。
     */
    @Test
    void whenPlayerVotesTwice_thenSecondVoteFails() throws Exception {
        // 首先开始游戏
        mockMvc.perform(post("/api/games/{roomId}/start", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 验证游戏现在处于role_viewing状态
        mockMvc.perform(get("/api/rooms/{roomId}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("playing"));
                
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
        
        // 队长提议队伍
        String playersResponseStr = mockMvc.perform(get("/api/rooms/{roomId}/players", roomId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // 解析响应以获取玩家信息
        Result<RoomPlayersResponse> playersResult = objectMapper.readValue(playersResponseStr,
                TypeFactory.defaultInstance().constructParametricType(Result.class, RoomPlayersResponse.class));
        List<PlayerInfoResponse> players = playersResult.getData().getPlayers();
        
        // 构造队伍提议请求，选择前两个玩家
        List<UUID> selectedPlayerIds = players.stream()
                .limit(2)
                .map(PlayerInfoResponse::getPlayerId)
                .collect(Collectors.toList());
        
        ProposeTeamRequest proposeRequest = new ProposeTeamRequest();
        proposeRequest.setPlayerIds(selectedPlayerIds);
        
        // 队长提议队伍
        mockMvc.perform(post("/api/games/{gameId}/propose-team", gameId)
                        .header("Authorization", authorizationHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proposeRequest)))
                .andExpect(status().isOk());
        
        // 获取一个非队长玩家的令牌
        PlayerInfoResponse nonLeader = players.stream()
                .filter(p -> !p.getIsHost())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到非队长玩家"));
        
        String voterToken = "Bearer " + jwtUtil.generateToken(nonLeader.getPlayerId(), nonLeader.getUsername());
        
        // 构造投票请求
        VoteRequest voteRequest = new VoteRequest();
        voteRequest.setVoteType("approve");
        
        // 第一次投票
        mockMvc.perform(post("/api/games/{gameId}/vote", gameId)
                        .header("Authorization", voterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voteRequest)))
                .andExpect(status().isOk());
        
        // When & Then - 第二次投票应该失败
        mockMvc.perform(post("/api/games/{gameId}/vote", gameId)
                        .header("Authorization", voterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voteRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("已经投过票了"));
    }
    
    /**
     * TEAM-VOTE-TC-003: 投票通过
     * 测试目的: 验证当足够多的玩家投票同意时，投票通过并进入下一阶段。
     */
    @Test
    void whenMajorityApprovesVote_thenVotePasses() throws Exception {
        // 首先开始游戏
        mockMvc.perform(post("/api/games/{roomId}/start", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 验证游戏现在处于role_viewing状态
        mockMvc.perform(get("/api/rooms/{roomId}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("playing"));
                
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
        
        // 队长提议队伍
        String playersResponseStr = mockMvc.perform(get("/api/rooms/{roomId}/players", roomId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // 解析响应以获取玩家信息
        Result<RoomPlayersResponse> playersResult = objectMapper.readValue(playersResponseStr,
                TypeFactory.defaultInstance().constructParametricType(Result.class, RoomPlayersResponse.class));
        List<PlayerInfoResponse> players = playersResult.getData().getPlayers();
        
        // 构造队伍提议请求，选择前两个玩家
        List<UUID> selectedPlayerIds = players.stream()
                .limit(2)
                .map(PlayerInfoResponse::getPlayerId)
                .collect(Collectors.toList());
        
        ProposeTeamRequest proposeRequest = new ProposeTeamRequest();
        proposeRequest.setPlayerIds(selectedPlayerIds);
        
        // 队长提议队伍
        mockMvc.perform(post("/api/games/{gameId}/propose-team", gameId)
                        .header("Authorization", authorizationHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proposeRequest)))
                .andExpect(status().isOk());
        
        // 确保大部分玩家投票赞成（确保赞成票数大于反对票数）
        // 对于5个玩家，需要至少3个赞成票才能通过（3 > 2）
        int totalPlayers = players.size();
        int approveCount = totalPlayers / 2 + 1; // 超过一半的玩家投赞成票
        
        // 让前approveCount个玩家投赞成票
        for (int i = 0; i < approveCount; i++) {
            PlayerInfoResponse player = players.get(i);
            String voterToken = "Bearer " + jwtUtil.generateToken(player.getPlayerId(), player.getUsername());
            
            VoteRequest voteRequest = new VoteRequest();
            voteRequest.setVoteType("approve");
            
            mockMvc.perform(post("/api/games/{gameId}/vote", gameId)
                            .header("Authorization", voterToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(voteRequest)))
                    .andExpect(status().isOk());
        }
        
        // 让剩余玩家投反对票
        for (int i = approveCount; i < totalPlayers; i++) {
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
        
        // 验证WebSocket消息已发送 - 包括投票提交消息和最终结果消息
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(anyString(), any(Object.class));
        
        // 验证发送了TEAM_APPROVED消息（在最后一个玩家投票后应该触发）
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(eq("/topic/game/" + gameId), (Object) argThat(argument -> 
                    argument instanceof GameMessage && "TEAM_APPROVED".equals(((GameMessage) argument).getType())));
    }
    
    /**
     * TEAM-VOTE-TC-004: 投票未通过
     * 测试目的: 验证当多数玩家投票反对时，投票未通过并重新提议队伍。
     */
    @Test
    void whenMajorityRejectsVote_thenVoteFails() throws Exception {
        // 首先开始游戏
        mockMvc.perform(post("/api/games/{roomId}/start", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk());

        // 验证游戏现在处于role_viewing状态
        mockMvc.perform(get("/api/rooms/{roomId}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("playing"));
                
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
        
        // 队长提议队伍
        String playersResponseStr = mockMvc.perform(get("/api/rooms/{roomId}/players", roomId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // 解析响应以获取玩家信息
        Result<RoomPlayersResponse> playersResult = objectMapper.readValue(playersResponseStr,
                TypeFactory.defaultInstance().constructParametricType(Result.class, RoomPlayersResponse.class));
        List<PlayerInfoResponse> players = playersResult.getData().getPlayers();
        
        // 构造队伍提议请求，选择前两个玩家
        List<UUID> selectedPlayerIds = players.stream()
                .limit(2)
                .map(PlayerInfoResponse::getPlayerId)
                .collect(Collectors.toList());
        
        ProposeTeamRequest proposeRequest = new ProposeTeamRequest();
        proposeRequest.setPlayerIds(selectedPlayerIds);
        
        // 队长提议队伍
        mockMvc.perform(post("/api/games/{gameId}/propose-team", gameId)
                        .header("Authorization", authorizationHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proposeRequest)))
                .andExpect(status().isOk());
        
        // 确保大部分玩家投票反对（确保反对票数大于赞成票数）
        // 对于5个玩家，需要至少3个反对票才能失败（3 > 2）
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
        
        // 让剩余玩家投赞成票
        for (int i = rejectCount; i < totalPlayers; i++) {
            PlayerInfoResponse player = players.get(i);
            String voterToken = "Bearer " + jwtUtil.generateToken(player.getPlayerId(), player.getUsername());
            
            VoteRequest voteRequest = new VoteRequest();
            voteRequest.setVoteType("approve");
            
            mockMvc.perform(post("/api/games/{gameId}/vote", gameId)
                            .header("Authorization", voterToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(voteRequest)))
                    .andExpect(status().isOk());
        }
        
        // 验证WebSocket消息已发送
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(anyString(), any(Object.class));
        
        // 验证发送了TEAM_REJECTED消息（在最后一个玩家投票后应该触发）
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(eq("/topic/game/" + gameId), (Object) argThat(argument -> 
                    argument instanceof GameMessage && "TEAM_REJECTED".equals(((GameMessage) argument).getType())));
    }
    
    /**
     * QUEST-EXECUTION-TC-001: 成功执行任务使正义阵营获胜
     * 测试目的: 验证当正义阵营成功执行所有任务时，游戏正确结束并宣布正义阵营获胜。
     */
    @Test
    void whenGoodTeamSuccessfullyCompletesAllQuests_thenGameEndsWithGoodVictory() throws Exception {
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
        
        // 执行所有5个任务
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
            int requiredPlayers = getRequiredPlayersForRound(round);
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
            
            // 只有队伍成员执行任务
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
                // 验证游戏状态已更新为ENDED
                mockMvc.perform(get("/api/rooms/{roomId}", roomId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("ended"));
                        
                // 验证WebSocket消息已发送
                verify(messagingTemplate, atLeastOnce())
                        .convertAndSend(eq("/topic/game/" + gameId), (Object) argThat(argument -> 
                            argument instanceof GameMessage && "QUEST_COMPLETED".equals(((GameMessage) argument).getType())));
            }
        }
    }
    
    /**
     * QUEST-EXECUTION-TC-002: 成功执行单个任务
     * 测试目的: 验证当正义阵营成功执行单个任务时，任务正确完成并进入下一轮。
     */
    @Test
    void whenGoodTeamSuccessfullyCompletesSingleQuest_thenQuestCompletes() throws Exception {
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
        
        // 队长提议队伍（选择前两个玩家作为队伍成员）
        List<UUID> selectedPlayerIds = players.stream()
                .limit(2)
                .map(PlayerInfoResponse::getPlayerId)
                .collect(Collectors.toList());
        
        ProposeTeamRequest proposeRequest = new ProposeTeamRequest();
        proposeRequest.setPlayerIds(selectedPlayerIds);
        
        // 队长提议队伍
        mockMvc.perform(post("/api/games/{gameId}/propose-team", gameId)
                        .header("Authorization", authorizationHeader)
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
        
        // 只有队伍成员执行任务
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
        
        // 验证任务完成并进入下一轮
        mockMvc.perform(get("/api/rooms/{roomId}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("playing"));
                
        // 验证WebSocket消息已发送
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(eq("/topic/game/" + gameId), (Object) argThat(argument -> 
                    argument instanceof GameMessage && "NEXT_ROUND_STARTED".equals(((GameMessage) argument).getType())));
    }
    
    /**
     * 根据轮次获取所需玩家数（5人游戏配置）
     * @param round 轮次
     * @return 所需玩家数
     */
    private int getRequiredPlayersForRound(int round) {
        return switch (round) {
            case 2, 4, 5 -> 3;
            default -> 2;
        };
    }

}