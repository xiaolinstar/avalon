package cn.xiaolin.avalon.service;

import cn.xiaolin.avalon.dto.*;
import cn.xiaolin.avalon.entity.*;
import cn.xiaolin.avalon.enums.*;
import cn.xiaolin.avalon.repository.*;
import cn.xiaolin.avalon.websocket.GameMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class GameService {
    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final RoomRepository roomRepository;
    private final RoomPlayerRepository roomPlayerRepository;
    private final QuestRepository questRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    
    @PersistenceContext
    private EntityManager entityManager;

    // 角色配置
    private static final Map<Integer, List<String>> ROLE_CONFIGS = Map.of(
        5, List.of("merlin", "percival", "loyal_servant", "morgana", "assassin"),
        6, List.of("merlin", "percival", "loyal_servant", "loyal_servant", "morgana", "assassin"),
        7, List.of("merlin", "percival", "loyal_servant", "loyal_servant", "morgana", "assassin", "minion"),
        8, List.of("merlin", "percival", "loyal_servant", "loyal_servant", "loyal_servant", "morgana", "assassin", "minion"),
        9, List.of("merlin", "percival", "loyal_servant", "loyal_servant", "loyal_servant", "loyal_servant", "morgana", "assassin", "minion"),
        10, List.of("merlin", "percival", "loyal_servant", "loyal_servant", "loyal_servant", "loyal_servant", "morgana", "assassin", "minion", "oberon")
    );

    @Transactional
    public Game createGame(UUID roomId) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RuntimeException("房间不存在"));

        Game game = new Game();
        game.setRoom(room);
        game.setStatus(GameStatus.PREPARING.getValue());
        game.setCurrentRound(1);
        
        return gameRepository.save(game);
    }

    @Transactional
    public void startGame(UUID roomId) {
        // 首先通过roomId获取房间
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RuntimeException("房间不存在"));
        
        String roomCode = room.getRoomCode();
        
        // 检查房间是否已经有游戏存在
        Optional<Game> existingGameOpt = gameRepository.findByRoomRoomCode(roomCode);
        
        // 如果已有游戏且游戏正在进行中，则返回错误
        if (existingGameOpt.isPresent()) {
            Game existingGame = existingGameOpt.get();
            if (!Objects.equals(existingGame.getStatus(), GameStatus.ENDED.getValue())) {
                throw new RuntimeException("游戏已开始");
            }
        }
        
        room.setStatus(RoomStatus.PLAYING.getValue());
        roomRepository.save(room);

        // 获取房间中的所有活跃玩家
        List<RoomPlayer> roomPlayers = roomPlayerRepository.findByRoomIdAndIsActiveTrue(room.getId());
        List<User> players = roomPlayers.stream()
                .map(RoomPlayer::getUser)
                .collect(Collectors.toList());

        if (players.size() < 5 || players.size() > 10) {
            throw new RuntimeException("游戏人数必须在5-10人之间");
        }

        // 创建新游戏并保存
        Game game = new Game();
        game.setRoom(room);
        game.setStatus(GameStatus.ROLE_VIEWING.getValue());
        game.setStartedAt(LocalDateTime.now());
        game = gameRepository.save(game);

        // 分配角色，并将角色信息存储到数据库
        assignRoles(game, players);

        // 发送WebSocket消息通知所有玩家游戏已开始，可以查看角色
        GameMessage message = new GameMessage();
        message.setType("GAME_STARTED");
        message.setGameId(game.getId());
        message.setRoomId(room.getId());
        message.setContent("游戏已开始，请查看您的角色");
        message.setTimestamp(System.currentTimeMillis());
        
        messagingTemplate.convertAndSend("/topic/room/" + room.getId(), message);
    }

    private void assignRoles(Game game, List<User> players) {
        List<String> roles = new ArrayList<>(ROLE_CONFIGS.get(players.size()));
        Collections.shuffle(roles);
        List<GamePlayer> gamePlayerList = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            GamePlayer gamePlayer = new GamePlayer();
            gamePlayer.setGame(game);
            gamePlayer.setUser(players.get(i));
            gamePlayer.setRole(roles.get(i));
            gamePlayer.setAlignment(getAlignment(roles.get(i)));
            gamePlayer.setSeatNumber(i + 1);
            gamePlayer.setIsHost(i == 0); // 第一个玩家是房主
            gamePlayerList.add(gamePlayer);
        }
        gamePlayerRepository.saveAll(gamePlayerList);
    }

    private String getAlignment(String role) {
        return switch (role) {
            case "morgana", "assassin", "minion", "oberon" -> Alignment.EVIL.getValue();
            default -> Alignment.GOOD.getValue();
        };
    }

    public Game getGameById(UUID gameId) {
        Optional<Game> gameOpt = gameRepository.findById(gameId);
        if (gameOpt.isPresent()) {
            return gameOpt.get();
        } else {
            throw new RuntimeException("游戏不存在");
        }
    }

    public Game getGameByRoomId(UUID roomId) {
        return gameRepository.findByRoomId(roomId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));
    }

    public List<GamePlayer> getGamePlayers(UUID gameId) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));
        
        return gamePlayerRepository.findByGame(game);
    }

    public String getGameStatus(UUID gameId) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));
        return game.getStatus();
    }
}