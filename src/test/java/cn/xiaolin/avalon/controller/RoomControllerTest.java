package cn.xiaolin.avalon.controller;

import cn.xiaolin.avalon.dto.ApiResponse;
import cn.xiaolin.avalon.dto.CreateRoomRequest;
import cn.xiaolin.avalon.dto.RoomPlayersResponse;
import cn.xiaolin.avalon.dto.RoomResponse;
import cn.xiaolin.avalon.entity.Room;
import cn.xiaolin.avalon.entity.RoomPlayer;
import cn.xiaolin.avalon.entity.User;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoomControllerTest {

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
    private JwtUtil jwtUtil;

    private User testUser;
    private User secondUser;
    private String validToken;
    private String secondUserToken;
    private String authorizationHeader;
    private String secondAuthorizationHeader;

    @BeforeEach
    void setUp() {
        // Create test users
        testUser = new User();
        testUser.setUsername("testuser_" + UUID.randomUUID().toString().substring(0, 8));
        testUser.setEmail("test_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
        testUser.setPasswordHash("hashed_password");
        testUser = userRepository.save(testUser);

        secondUser = new User();
        secondUser.setUsername("seconduser_" + UUID.randomUUID().toString().substring(0, 8));
        secondUser.setEmail("second_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
        secondUser.setPasswordHash("hashed_password");
        secondUser = userRepository.save(secondUser);

        // Generate valid JWT tokens for test users
        validToken = jwtUtil.generateToken(testUser.getId(), testUser.getUsername());
        secondUserToken = jwtUtil.generateToken(secondUser.getId(), secondUser.getUsername());
        authorizationHeader = "Bearer " + validToken;
        secondAuthorizationHeader = "Bearer " + secondUserToken;
    }

    @AfterEach
    void tearDown() {
        // Clean up test data in correct order to respect foreign key constraints
        roomPlayerRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void whenAuthenticatedUserCreatesRoom_thenReturnsSuccess() throws Exception {
        // ROOM-CREATE-TC-001: Authenticated user creates room
        // Given
        CreateRoomRequest request = new CreateRoomRequest();
        request.setMaxPlayers(8);

        // When & Then
        String responseStr = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", authorizationHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("房间创建成功"))
                .andExpect(jsonPath("$.data.roomCode").isString())
                .andExpect(jsonPath("$.data.maxPlayers").value(8))
                .andExpect(jsonPath("$.data.status").value("waiting"))
                .andExpect(jsonPath("$.data.creatorName").value(testUser.getUsername()))
                .andExpect(jsonPath("$.data.currentPlayers").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Validate room code format (6 characters, uppercase letters and digits)
        ApiResponse<RoomResponse> apiResponse = objectMapper.readValue(responseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, RoomResponse.class));
        RoomResponse roomResponse = apiResponse.getData();
        String roomCode = roomResponse.getRoomCode();
        
        // Verify room code format
        assert roomCode.matches("[A-Z0-9]{6}");
    }

    @Test
    void whenUserJoinsExistingRoom_thenReturnsSuccess() throws Exception {
        // ROOM-JOIN-TC-001: Normal join room
        // First create a room
        CreateRoomRequest createRequest = new CreateRoomRequest();
        createRequest.setMaxPlayers(5);

        String createResponseStr = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", authorizationHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Parse the response to get the room code
        ApiResponse<RoomResponse> createApiResponse = objectMapper.readValue(createResponseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, RoomResponse.class));
        RoomResponse roomResponse = createApiResponse.getData();
        String roomCode = roomResponse.getRoomCode();

        // Second user joins the room
        mockMvc.perform(post("/api/rooms/{roomCode}/join", roomCode)
                        .header("Authorization", secondAuthorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("加入房间成功"))
                .andExpect(jsonPath("$.data.roomCode").value(roomCode))
                .andExpect(jsonPath("$.data.currentPlayers").value(2));
    }

    @Test
    void whenUserJoinsFullRoom_thenReturnsError() throws Exception {
        // ROOM-JOIN-TC-002: Join full room
        // First create a room with max 5 players (minimum allowed)
        CreateRoomRequest createRequest = new CreateRoomRequest();
        createRequest.setMaxPlayers(5);

        String createResponseStr = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", authorizationHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Parse the response to get the room code
        ApiResponse<RoomResponse> createApiResponse = objectMapper.readValue(createResponseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, RoomResponse.class));
        RoomResponse roomResponse = createApiResponse.getData();
        String roomCode = roomResponse.getRoomCode();

        // Create 4 more users to fill the room to capacity (5 players total)
        User user2 = new User();
        user2.setUsername("user2_" + UUID.randomUUID().toString().substring(0, 8));
        user2.setEmail("user2_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
        user2.setPasswordHash("hashed_password");
        user2 = userRepository.save(user2);
        
        User user3 = new User();
        user3.setUsername("user3_" + UUID.randomUUID().toString().substring(0, 8));
        user3.setEmail("user3_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
        user3.setPasswordHash("hashed_password");
        user3 = userRepository.save(user3);
        
        User user4 = new User();
        user4.setUsername("user4_" + UUID.randomUUID().toString().substring(0, 8));
        user4.setEmail("user4_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
        user4.setPasswordHash("hashed_password");
        user4 = userRepository.save(user4);
        
        User user5 = new User();
        user5.setUsername("user5_" + UUID.randomUUID().toString().substring(0, 8));
        user5.setEmail("user5_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
        user5.setPasswordHash("hashed_password");
        user5 = userRepository.save(user5);

        // Join with 4 additional users (creator is already in the room)
        String token2 = jwtUtil.generateToken(user2.getId(), user2.getUsername());
        mockMvc.perform(post("/api/rooms/{roomCode}/join", roomCode)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk());
                
        String token3 = jwtUtil.generateToken(user3.getId(), user3.getUsername());
        mockMvc.perform(post("/api/rooms/{roomCode}/join", roomCode)
                        .header("Authorization", "Bearer " + token3))
                .andExpect(status().isOk());
                
        String token4 = jwtUtil.generateToken(user4.getId(), user4.getUsername());
        mockMvc.perform(post("/api/rooms/{roomCode}/join", roomCode)
                        .header("Authorization", "Bearer " + token4))
                .andExpect(status().isOk());
                
        String token5 = jwtUtil.generateToken(user5.getId(), user5.getUsername());
        mockMvc.perform(post("/api/rooms/{roomCode}/join", roomCode)
                        .header("Authorization", "Bearer " + token5))
                .andExpect(status().isOk());

        // Sixth user tries to join (should fail)
        User sixthUser = new User();
        sixthUser.setUsername("sixthuser_" + UUID.randomUUID().toString().substring(0, 8));
        sixthUser.setEmail("sixth_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
        sixthUser.setPasswordHash("hashed_password");
        sixthUser = userRepository.save(sixthUser);

        String sixthUserToken = jwtUtil.generateToken(sixthUser.getId(), sixthUser.getUsername());
        String sixthAuthorizationHeader = "Bearer " + sixthUserToken;

        mockMvc.perform(post("/api/rooms/{roomCode}/join", roomCode)
                        .header("Authorization", sixthAuthorizationHeader))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("房间已满"));
    }

    @Test
    void whenUserCreatesRoomWithInvalidToken_thenReturnsError() throws Exception {
        // Given
        CreateRoomRequest request = new CreateRoomRequest();
        request.setMaxPlayers(5);

        // When & Then
        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer invalid_token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void whenUserCreatesRoomWithInvalidMaxPlayers_thenReturnsError() throws Exception {
        // Test with too few players
        CreateRoomRequest request = new CreateRoomRequest();
        request.setMaxPlayers(3); // Invalid - less than minimum

        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", authorizationHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        // Test with too many players
        request.setMaxPlayers(15); // Invalid - more than maximum
        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", authorizationHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenUserGetsRoomByValidCode_thenReturnsRoomData() throws Exception {
        // First create a room
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

        // Parse the response to get the room code
        ApiResponse<RoomResponse> apiResponse = objectMapper.readValue(responseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, RoomResponse.class));
        RoomResponse roomResponse = apiResponse.getData();
        String roomCode = roomResponse.getRoomCode();

        // Now test getting the room by code
        mockMvc.perform(get("/api/rooms/{roomCode}", roomCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("获取房间信息成功"))
                .andExpect(jsonPath("$.data.roomCode").value(roomCode))
                .andExpect(jsonPath("$.data.maxPlayers").value(5))
                .andExpect(jsonPath("$.data.status").value("waiting"))
                .andExpect(jsonPath("$.data.creatorName").value(testUser.getUsername()))
                .andExpect(jsonPath("$.data.currentPlayers").value(1));
    }

    @Test
    void whenUserGetsRoomByInvalidCode_thenReturnsError() throws Exception {
        mockMvc.perform(get("/api/rooms/{roomCode}", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void whenUserGetsRoomByIdWithValidId_thenReturnsRoomData() throws Exception {
        // First create a room
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

        // Parse the response to get the room ID
        ApiResponse<RoomResponse> apiResponse = objectMapper.readValue(responseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, RoomResponse.class));
        RoomResponse roomResponse = apiResponse.getData();
        String roomId = roomResponse.getRoomId().toString();

        // Now test getting the room by ID
        mockMvc.perform(get("/api/rooms/id/{roomId}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("获取房间信息成功"))
                .andExpect(jsonPath("$.data.roomId").value(roomId))
                .andExpect(jsonPath("$.data.maxPlayers").value(5))
                .andExpect(jsonPath("$.data.status").value("waiting"))
                .andExpect(jsonPath("$.data.creatorName").value(testUser.getUsername()))
                .andExpect(jsonPath("$.data.currentPlayers").value(1));
    }

    @Test
    void whenUserGetsRoomByIdWithInvalidId_thenReturnsError() throws Exception {
        mockMvc.perform(get("/api/rooms/id/{roomId}", "invalid-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("房间ID格式错误"));
    }

    @Test
    void whenUserGetsRoomPlayersWithValidCode_thenReturnsPlayersList() throws Exception {
        // First create a room
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

        // Parse the response to get the room code
        ApiResponse<RoomResponse> apiResponse = objectMapper.readValue(responseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, RoomResponse.class));
        RoomResponse roomResponse = apiResponse.getData();
        String roomCode = roomResponse.getRoomCode();

        // Now test getting the room players
        mockMvc.perform(get("/api/rooms/{roomCode}/players", roomCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("获取房间玩家列表成功"))
                .andExpect(jsonPath("$.data.roomCode").value(roomCode))
                .andExpect(jsonPath("$.data.players").isArray())
                .andExpect(jsonPath("$.data.players.length()").value(1)); // Creator is the only player
    }

    @Test
    void whenRoomCreatorLeavesRoom_thenReturnsSuccess() throws Exception {
        // First create a room
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

        // Parse the response to get the room code
        ApiResponse<RoomResponse> apiResponse = objectMapper.readValue(responseStr,
                TypeFactory.defaultInstance().constructParametricType(ApiResponse.class, RoomResponse.class));
        RoomResponse roomResponse = apiResponse.getData();
        String roomCode = roomResponse.getRoomCode();

        // Second user joins the room
        mockMvc.perform(post("/api/rooms/{roomCode}/join", roomCode)
                        .header("Authorization", secondAuthorizationHeader))
                .andExpect(status().isOk());

        // Now test leaving the room (creator leaves)
        mockMvc.perform(delete("/api/rooms/{roomCode}/leave", roomCode)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("离开房间成功"));
    }
}