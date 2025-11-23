package cn.xiaolin.avalon.websocket;

import cn.xiaolin.avalon.dto.GameStateResponse;
import cn.xiaolin.avalon.service.GameStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Controller
@RequiredArgsConstructor
public class GameWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    
    private final GameStateService gameStateService;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<UUID, Boolean> pendingBroadcasts = new ConcurrentHashMap<>();


    @MessageMapping("/game.join")
    public void joinGame(@Payload GameMessage message) {
        message.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/game/" + message.getGameId(), message);
        
        // 延迟广播游戏状态更新，避免频繁调用
        scheduleDelayedBroadcast(message.getGameId());
    }

    @MessageMapping("/game.vote")
    public void processVote(@Payload GameMessage message) {
        message.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/game/" + message.getGameId(), message);
        
        // 延迟广播游戏状态更新，避免频繁调用
        scheduleDelayedBroadcast(message.getGameId());
    }

    @MessageMapping("/game.quest")
    public void processQuest(@Payload GameMessage message) {
        message.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/game/" + message.getGameId(), message);
        
        // 延迟广播游戏状态更新，避免频繁调用
        scheduleDelayedBroadcast(message.getGameId());
    }

    @MessageMapping("/game.team-proposed")
    public void teamProposed(@Payload GameMessage message) {
        message.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/game/" + message.getGameId(), message);
        
        // 延迟广播游戏状态更新，避免频繁调用
        scheduleDelayedBroadcast(message.getGameId());
    }

    @MessageMapping("/game.phase-changed")
    public void phaseChanged(@Payload GameMessage message) {
        message.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/game/" + message.getGameId(), message);
        
        // 延迟广播游戏状态更新，避免频繁调用
        scheduleDelayedBroadcast(message.getGameId());
    }

    @MessageMapping("/room.join")
    public void joinRoom(@Payload GameMessage message) {
        message.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/room/" + message.getRoomId(), message);
    }

    @MessageMapping("/room.leave")
    public void leaveRoom(@Payload GameMessage message) {
        message.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/room/" + message.getRoomId(), message);
    }

    @MessageMapping("/room.ready")
    public void playerReady(@Payload GameMessage message) {
        message.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/room/" + message.getRoomId(), message);
    }

    @MessageMapping("/room.game-started")
    public void gameStarted(@Payload GameMessage message) {
        message.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/room/" + message.getRoomId(), message);
    }

    @MessageMapping("/test")
    public void testMessage(@Payload GameMessage message) {
        message.setTimestamp(System.currentTimeMillis());
        System.out.println("收到测试消息: " + message.getContent());
        messagingTemplate.convertAndSend("/topic/test", message);
    }
    
    private void scheduleDelayedBroadcast(UUID gameId) {
        // 如果已经有待处理的广播，跳过
        if (pendingBroadcasts.putIfAbsent(gameId, true) != null) {
            return;
        }
        
        // 延迟1秒后执行广播
        scheduler.schedule(() -> {
            try {
                broadcastGameState(gameId);
            } finally {
                pendingBroadcasts.remove(gameId);
            }
        }, 1, TimeUnit.SECONDS);
    }

    // 广播游戏状态的方法
    public void broadcastGameState(UUID gameId) {
        try {
            // 获取所有玩家的游戏状态
            Map<UUID, GameStateResponse> allPlayerStates = gameStateService.getGameStatesForAllPlayers(gameId);
            
            // 为每个玩家发送个性化的游戏状态
            for (Map.Entry<UUID, GameStateResponse> entry : allPlayerStates.entrySet()) {
                UUID userId = entry.getKey();
                GameStateResponse gameState = entry.getValue();
                
                GameMessage stateMessage = new GameMessage();
                stateMessage.setType("GAME_STATE_UPDATE");
                stateMessage.setGameId(gameId);
                stateMessage.setUserId(userId);
                stateMessage.setContent("游戏状态更新");
                stateMessage.setTimestamp(System.currentTimeMillis());
                
                // 发送给特定用户的游戏状态
                messagingTemplate.convertAndSendToUser(userId.toString(), "/topic/game/" + gameId + "/state", stateMessage);
            }
            
        } catch (Exception e) {
            // 记录错误但不影响主要功能
            System.err.println("广播游戏状态失败: " + e.getMessage());
        }
    }
}