package cn.xiaolin.avalon.controller;

import cn.xiaolin.avalon.dto.ApiResponse;
import cn.xiaolin.avalon.dto.CreateRoomRequest;
import cn.xiaolin.avalon.dto.RoomResponse;
import cn.xiaolin.avalon.entity.User;
import cn.xiaolin.avalon.repository.GamePlayerRepository;
import cn.xiaolin.avalon.repository.GameRepository;
import cn.xiaolin.avalon.repository.RoomPlayerRepository;
import cn.xiaolin.avalon.repository.RoomRepository;
import cn.xiaolin.avalon.repository.UserRepository;
import cn.xiaolin.avalon.utils.JwtUtil;
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

import java.util.UUID;

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
class GameControllerTest {

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
        ApiResponse<RoomResponse> apiResponse = objectMapper.readValue(responseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, RoomResponse.class));
        RoomResponse roomResponse = apiResponse.getData();
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
            mockMvc.perform(post("/api/rooms/{roomCode}/join", roomCode)
                            .header("Authorization", "Bearer " + playerToken))
                    .andExpect(status().isOk());
        }
    }

    @AfterEach
    void tearDown() {
        // 按正确顺序清理测试数据
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
        mockMvc.perform(get("/api/rooms/{roomCode}", roomCode))
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
        mockMvc.perform(get("/api/rooms/{roomCode}", roomCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("playing"));
                
        // 验证重复开始游戏会失败
        mockMvc.perform(post("/api/games/{roomId}/start", roomId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("游戏已开始"));
    }

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
        mockMvc.perform(get("/api/rooms/{roomCode}", roomCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("playing"));
                
        // 获取实际的游戏ID
        String roomResponseStr = mockMvc.perform(get("/api/rooms/{roomCode}", roomCode))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取游戏ID
        ApiResponse<RoomResponse> roomApiResponse = objectMapper.readValue(roomResponseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, RoomResponse.class));
        RoomResponse roomResponse = roomApiResponse.getData();
        String gameId = roomResponse.getGameId().toString();
        
        // When & Then - 开始第一个任务
        mockMvc.perform(post("/api/games/{gameId}/start-first-quest", gameId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("第一个任务开始成功"));

        // 验证WebSocket消息已发送
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(anyString(), any(Object.class));
                
        // 验证游戏状态已更新为playing
        mockMvc.perform(get("/api/rooms/{roomCode}", roomCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("playing"));
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

        // 获取实际的游戏ID
        // 我们需要先获取房间信息，然后从中提取游戏ID
        String roomResponseStr = mockMvc.perform(get("/api/rooms/{roomCode}", roomCode))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取游戏ID
        ApiResponse<RoomResponse> roomApiResponse = objectMapper.readValue(roomResponseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, RoomResponse.class));
        RoomResponse roomResponse = roomApiResponse.getData();
        String gameId = roomResponse.getGameId().toString();

        // When & Then - 获取角色信息
        mockMvc.perform(get("/api/games/{gameId}/role-info", gameId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data.gameId").value(gameId))
                .andExpect(jsonPath("$.data.role").isNotEmpty())
                .andExpect(jsonPath("$.data.roleName").isNotEmpty())
                .andExpect(jsonPath("$.data.alignment").isNotEmpty())
                .andExpect(jsonPath("$.data.description").isNotEmpty())
                .andExpect(jsonPath("$.data.visibilityInfo").isNotEmpty());
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
        String roomResponseStr = mockMvc.perform(get("/api/rooms/{roomCode}", roomCode))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 解析响应以获取游戏ID
        ApiResponse<RoomResponse> roomApiResponse = objectMapper.readValue(roomResponseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, RoomResponse.class));
        RoomResponse roomResponse = roomApiResponse.getData();
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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("玩家不在游戏中"));
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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("游戏不存在"));
    }
}