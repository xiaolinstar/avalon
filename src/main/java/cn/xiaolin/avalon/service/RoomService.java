package cn.xiaolin.avalon.service;

import cn.xiaolin.avalon.dto.CreateRoomRequest;
import cn.xiaolin.avalon.dto.JoinRoomRequest;
import cn.xiaolin.avalon.dto.RoomResponse;
import cn.xiaolin.avalon.dto.RoomPlayersResponse;
import cn.xiaolin.avalon.dto.PlayerInfoResponse;
import cn.xiaolin.avalon.entity.Room;
import cn.xiaolin.avalon.entity.User;
import cn.xiaolin.avalon.entity.Game;
import cn.xiaolin.avalon.entity.GamePlayer;
import cn.xiaolin.avalon.entity.RoomPlayer;
import cn.xiaolin.avalon.enums.RoomStatus;
import cn.xiaolin.avalon.repository.RoomRepository;
import cn.xiaolin.avalon.repository.UserRepository;
import cn.xiaolin.avalon.repository.GameRepository;
import cn.xiaolin.avalon.repository.GamePlayerRepository;
import cn.xiaolin.avalon.repository.RoomPlayerRepository;
import cn.xiaolin.avalon.utils.RoomCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final RoomPlayerRepository roomPlayerRepository;

    @Transactional
    public RoomResponse createRoom(UUID userId, CreateRoomRequest request) {
        User creator = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));

        Room room = new Room();
        room.setCreator(creator);
        room.setRoomCode(RoomCodeGenerator.generateRoomCode());
        room.setMaxPlayers(request.getMaxPlayers());
        room.setStatus(RoomStatus.WAITING.getValue());
        // Temporarily set roleConfig to null to avoid JSONB issues
        // room.setRoleConfig(request.getRoleConfig());

        Room savedRoom = roomRepository.save(room);

        // Create RoomPlayer entry for the creator
        RoomPlayer creatorPlayer = new RoomPlayer();
        creatorPlayer.setRoom(savedRoom);
        creatorPlayer.setUser(creator);
        creatorPlayer.setIsHost(true);
        creatorPlayer.setIsActive(true);
        creatorPlayer.setSeatNumber(1);
        roomPlayerRepository.save(creatorPlayer);

        return new RoomResponse(
            savedRoom.getId(),
            savedRoom.getRoomCode(),
            savedRoom.getMaxPlayers(),
            savedRoom.getStatus(),
            creator.getUsername(),
            1  // 新建房间只有创建者1人
        );
    }

    public RoomResponse getRoomByCode(String roomCode) {
        // 优化查询：使用JOIN FETCH预加载creator，避免N+1问题
        Room room = roomRepository.findByRoomCodeWithCreator(roomCode)
            .orElseThrow(() -> new RuntimeException("房间不存在"));
        
        // Check if room is closed
        if ("closed".equals(room.getStatus())) {
            throw new RuntimeException("房间已关闭");
        }

        // 获取当前玩家数量
        int currentPlayerCount = (int) roomPlayerRepository.countActivePlayersByRoomId(room.getId());

        return new RoomResponse(
            room.getId(),
            room.getRoomCode(),
            room.getMaxPlayers(),
            room.getStatus(),
            room.getCreator().getUsername(),
            currentPlayerCount  // 新增：当前玩家数量
        );
    }

    public RoomResponse getRoomById(UUID roomId) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RuntimeException("房间不存在"));
        
        // Check if room is closed
        if ("closed".equals(room.getStatus())) {
            throw new RuntimeException("房间已关闭");
        }

        // 获取当前玩家数量
        int currentPlayerCount = (int) roomPlayerRepository.countActivePlayersByRoomId(room.getId());

        return new RoomResponse(
            room.getId(),
            room.getRoomCode(),
            room.getMaxPlayers(),
            room.getStatus(),
            room.getCreator().getUsername(),
            currentPlayerCount  // 新增：当前玩家数量
        );
    }

    @Transactional
    public RoomResponse joinRoom(UUID userId, JoinRoomRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        Room room = roomRepository.findByRoomCode(request.getRoomCode())
            .orElseThrow(() -> new RuntimeException("房间不存在"));
        
        // Check if room is closed
        if ("closed".equals(room.getStatus())) {
            throw new RuntimeException("房间已关闭");
        }
        
        // Check if user is already in the room and active
        if (roomPlayerRepository.existsByRoomIdAndUserIdAndIsActiveTrue(room.getId(), userId)) {
            throw new RuntimeException("您已经在该房间中");
        }
        
        // Check if room is full
        long currentPlayerCount = roomPlayerRepository.countActivePlayersByRoomId(room.getId());
        if (currentPlayerCount >= room.getMaxPlayers()) {
            throw new RuntimeException("房间已满");
        }
        
        // Check if user has previously joined and left the room
        Optional<RoomPlayer> existingPlayerOpt = roomPlayerRepository.findByRoomIdAndUserId(room.getId(), userId);
        RoomPlayer roomPlayer;
        
        if (existingPlayerOpt.isPresent()) {
            // Player has previously joined and left, reactivate them
            roomPlayer = existingPlayerOpt.get();
            roomPlayer.setIsActive(true);
            // Keep existing seat number
        } else {
            // New player, create new RoomPlayer entry
            roomPlayer = new RoomPlayer();
            roomPlayer.setRoom(room);
            roomPlayer.setUser(user);
            roomPlayer.setIsHost(false); // Joining players are not hosts
            roomPlayer.setIsActive(true);
            roomPlayer.setSeatNumber((int) (currentPlayerCount + 1));
        }
        
        roomPlayerRepository.save(roomPlayer);
        
        // 获取更新后的玩家数量
        int updatedPlayerCount = (int) roomPlayerRepository.countActivePlayersByRoomId(room.getId());
        
        return new RoomResponse(
            room.getId(),
            room.getRoomCode(),
            room.getMaxPlayers(),
            room.getStatus(),
            room.getCreator().getUsername(),
            updatedPlayerCount  // 新增：当前玩家数量
        );
    }

    @Cacheable(value = "roomPlayers", key = "#roomCode", unless = "#result == null")
    public RoomPlayersResponse getRoomPlayers(String roomCode) {
        // 优化查询：一次性获取房间和玩家信息，避免 N+1 问题
        Room room = roomRepository.findByRoomCode(roomCode)
            .orElseThrow(() -> new RuntimeException("房间不存在"));
        
        // Try to find an active game for this room
        Optional<Game> gameOpt = gameRepository.findByRoomId(room.getId());
        
        List<PlayerInfoResponse> players;
        
        if (gameOpt.isPresent() && !"ended".equals(gameOpt.get().getStatus())) {
            // If there's an active game, get players from game with optimized query
            Game game = gameOpt.get();
            List<GamePlayer> gamePlayers = gamePlayerRepository.findByGameWithUser(game);
            
            players = gamePlayers.stream()
                .map(gp -> new PlayerInfoResponse(
                    gp.getUser().getId(),
                    gp.getUser().getUsername(),
                    gp.getRole(),
                    gp.getAlignment(),
                    gp.getIsHost(),
                    gp.getSeatNumber(),
                    gp.getIsActive()
                ))
                .collect(Collectors.toList());
        } else {
            // If no active game, get players from room_players table with optimized query
            List<RoomPlayer> roomPlayers = roomPlayerRepository.findActivePlayersByRoomCode(roomCode);
            
            players = roomPlayers.stream()
                .map(rp -> new PlayerInfoResponse(
                    rp.getUser().getId(),
                    rp.getUser().getUsername(),
                    "unknown",
                    "unknown",
                    rp.getIsHost(),
                    rp.getSeatNumber(),
                    rp.getIsActive()
                ))
                .collect(Collectors.toList());
        }
        
        return new RoomPlayersResponse(roomCode, players);
    }

    @Transactional
    public RoomPlayersResponse leaveRoom(UUID userId, String roomCode) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        Room room = roomRepository.findByRoomCode(roomCode)
            .orElseThrow(() -> new RuntimeException("房间不存在"));
        
        // Check if user is in the room
        RoomPlayer leavingPlayer = roomPlayerRepository.findByRoomIdAndUserId(room.getId(), userId)
            .orElseThrow(() -> new RuntimeException("您不在该房间中"));
        
        // Check if the leaving player is the host
        boolean wasHost = leavingPlayer.getIsHost();
        String newHostUsername = null;
        
        // Mark player as inactive instead of deleting
        leavingPlayer.setIsActive(false);
        roomPlayerRepository.save(leavingPlayer);
        
        // Get remaining active players
        List<RoomPlayer> remainingPlayers = roomPlayerRepository.findByRoomIdAndIsActiveTrue(room.getId());
        
        // If no players left, update room status
        boolean roomClosed = false;
        if (remainingPlayers.isEmpty()) {
            room.setStatus("closed");
            roomRepository.save(room);
            roomClosed = true;
        }
        
        // If the leaving player was the host, transfer host to the player with the smallest seat number
        if (wasHost && !remainingPlayers.isEmpty()) {
            // Find the player with the smallest seat number
            Optional<RoomPlayer> newHost = remainingPlayers.stream()
                .min(Comparator.comparing(RoomPlayer::getSeatNumber));
            
            if (newHost.isPresent()) {
                RoomPlayer newHostPlayer = newHost.get();
                newHostPlayer.setIsHost(true);
                roomPlayerRepository.save(newHostPlayer);
                newHostUsername = newHostPlayer.getUser().getUsername();
            }
        }
        
        // Convert remaining players to response format
        List<PlayerInfoResponse> players = remainingPlayers.stream()
            .map(rp -> new PlayerInfoResponse(
                rp.getUser().getId(),
                rp.getUser().getUsername(),
                "unknown",
                "unknown",
                rp.getIsHost(),
                rp.getSeatNumber(),
                rp.getIsActive()
            ))
            .collect(Collectors.toList());
        
        // Store room closed and new host info in the response for the controller to use
        RoomPlayersResponse response = new RoomPlayersResponse(roomCode, players);
        // We'll use the room code to indicate special conditions:
        // - "CLOSED" prefix means room was closed
        // - "HOST:" prefix followed by username means host was transferred
        if (roomClosed) {
            response.setRoomCode("CLOSED:" + roomCode);
        } else if (newHostUsername != null) {
            response.setRoomCode("HOST:" + newHostUsername + ":" + roomCode);
        }
        
        return response;
    }
}