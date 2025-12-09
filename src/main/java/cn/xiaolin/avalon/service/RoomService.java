package cn.xiaolin.avalon.service;

import cn.xiaolin.avalon.dto.CreateRoomRequest;
import cn.xiaolin.avalon.dto.RoomResponse;
import cn.xiaolin.avalon.dto.RoomPlayersResponse;
import cn.xiaolin.avalon.dto.PlayerInfoResponse;
import cn.xiaolin.avalon.entity.Room;
import cn.xiaolin.avalon.entity.User;
import cn.xiaolin.avalon.entity.RoomPlayer;
import cn.xiaolin.avalon.entity.Game;
import cn.xiaolin.avalon.entity.GamePlayer;
import cn.xiaolin.avalon.enums.RoomStatus;
import cn.xiaolin.avalon.repository.RoomRepository;
import cn.xiaolin.avalon.repository.UserRepository;
import cn.xiaolin.avalon.repository.RoomPlayerRepository;
import cn.xiaolin.avalon.repository.GameRepository;
import cn.xiaolin.avalon.repository.GamePlayerRepository;
import cn.xiaolin.avalon.utils.RoomCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RoomPlayerRepository roomPlayerRepository;
    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;

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
                1,  // 新建房间只有创建者1人
                null  // 新建房间还没有游戏
        );
    }

    @Cacheable(value = "room", key = "#roomCode", unless = "#result == null")
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

        // 获取游戏ID（如果游戏已开始）
        UUID gameId = null;
        Optional<Game> gameOpt = gameRepository.findByRoomId(room.getId());
        if (gameOpt.isPresent()) {
            gameId = gameOpt.get().getId();
        }

        return new RoomResponse(
                room.getId(),
                room.getRoomCode(),
                room.getMaxPlayers(),
                room.getStatus(),
                room.getCreator().getUsername(),
                currentPlayerCount,  // 新增：当前玩家数量
                gameId  // 新增：游戏ID（如果游戏已开始）
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

        // 获取游戏ID（如果游戏已开始）
        UUID gameId = null;
        Optional<Game> gameOpt = gameRepository.findByRoomId(room.getId());
        if (gameOpt.isPresent()) {
            gameId = gameOpt.get().getId();
        }

        return new RoomResponse(
                room.getId(),
                room.getRoomCode(),
                room.getMaxPlayers(),
                room.getStatus(),
                room.getCreator().getUsername(),
                currentPlayerCount,  // 新增：当前玩家数量
                gameId  // 新增：游戏ID（如果游戏已开始）
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
                    .filter(gp -> gp.getIsActive() == true)
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
                    .filter(rp -> rp.getIsActive() == true)
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
    
    // 注意：joinRoom和leaveRoom方法已经移到RoomPlayerService中
}