package cn.xiaolin.avalon.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 房间事件广播控制器
 * 支持简单事件广播和携带完整数据的事件广播
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class RoomEventController {

    private final SimpMessagingTemplate messagingTemplate;



    /**
     * 广播简单房间事件（供服务端主动调用）
     * 用于向后兼容，只包含基本信息
     */
    public void broadcastRoomEvent(String roomId, String eventType, String userId, String username) {
        RoomEvent event = RoomEvent.builder()
            .type(eventType)
            .roomId(roomId)
            .userId(userId)
            .username(username)
            .timestamp(System.currentTimeMillis())
            .build();

        log.info("broadcast room event: {}", event);

        messagingTemplate.convertAndSend(
            "/topic/room/" + roomId, 
            event
        );
    }

    /**
     * 广播携带完整数据的房间事件（供服务端主动调用）
     * 用于推送完整的玩家列表和房间信息
     * 
     * @param roomId 房间ID
     * @param eventType 事件类型（PLAYER_JOINED, PLAYER_LEFT等）
     * @param data 事件数据负载
     */
    public void broadcastRoomEventWithData(String roomId, String eventType, Map<String, Object> data) {
        RoomEvent event = RoomEvent.builder()
            .type(eventType)
            .roomId(roomId)
            .timestamp(System.currentTimeMillis())
            .data(data)
            .build();

        log.info("broadcast room event with data: type={}, roomId={}, data={}", eventType, roomId, data);

        messagingTemplate.convertAndSend(
            "/topic/room/" + roomId, 
            event
        );
    }
}