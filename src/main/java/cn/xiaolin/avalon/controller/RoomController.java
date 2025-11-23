package cn.xiaolin.avalon.controller;

import cn.xiaolin.avalon.dto.ApiResponse;
import cn.xiaolin.avalon.dto.CreateRoomRequest;
import cn.xiaolin.avalon.dto.JoinRoomRequest;
import cn.xiaolin.avalon.dto.RoomResponse;
import cn.xiaolin.avalon.dto.RoomPlayersResponse;
import cn.xiaolin.avalon.dto.PlayerInfoResponse;
import cn.xiaolin.avalon.service.RoomService;
import cn.xiaolin.avalon.utils.JwtUtil;
import cn.xiaolin.avalon.websocket.RoomEventController;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {
    private final RoomService roomService;
    private final JwtUtil jwtUtil;
    private final RoomEventController roomEventController;

    @PostMapping
    public ResponseEntity<ApiResponse<RoomResponse>> createRoom(
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody CreateRoomRequest request) {
        try {
            String token = authorizationHeader.substring(7);
            UUID userId = jwtUtil.getUserIdFromToken(token);
            
            RoomResponse roomResponse = roomService.createRoom(userId, request);
            return ResponseEntity.ok(ApiResponse.success("房间创建成功", roomResponse));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{roomCode}")
    public ResponseEntity<ApiResponse<RoomResponse>> getRoom(@PathVariable String roomCode) {
        try {
            RoomResponse roomResponse = roomService.getRoomByCode(roomCode);
            return ResponseEntity.ok(ApiResponse.success("获取房间信息成功", roomResponse));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/id/{roomId}")
    public ResponseEntity<ApiResponse<RoomResponse>> getRoomById(@PathVariable String roomId) {
        try {
            UUID roomUuid = UUID.fromString(roomId);
            RoomResponse roomResponse = roomService.getRoomById(roomUuid);
            return ResponseEntity.ok(ApiResponse.success("获取房间信息成功", roomResponse));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("房间ID格式错误"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{roomCode}/players")
    public ResponseEntity<ApiResponse<RoomPlayersResponse>> getRoomPlayers(@PathVariable String roomCode) {
        try {
            RoomPlayersResponse playersResponse = roomService.getRoomPlayers(roomCode);
            return ResponseEntity.ok(ApiResponse.success("获取房间玩家列表成功", playersResponse));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{roomCode}/join")
    public ResponseEntity<ApiResponse<RoomResponse>> joinRoom(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable String roomCode) {
        try {
            String token = authorizationHeader.substring(7);
            UUID userId = jwtUtil.getUserIdFromToken(token);
            
            JoinRoomRequest request = new JoinRoomRequest();
            request.setRoomCode(roomCode);
            
            RoomResponse roomResponse = roomService.joinRoom(userId, request);
            
            // 获取当前用户的用户名
            String username = jwtUtil.getUsernameFromToken(token);
            
            // 广播玩家加入房间
            roomEventController.broadcastRoomEvent(
                roomResponse.getRoomId().toString(),
                "PLAYER_JOINED",
                userId.toString(),
                username
            );
            
            return ResponseEntity.ok(ApiResponse.success("加入房间成功", roomResponse));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{roomCode}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveRoom(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable String roomCode) {
        try {
            String token = authorizationHeader.substring(7);
            UUID userId = jwtUtil.getUserIdFromToken(token);
            String username = jwtUtil.getUsernameFromToken(token);
            
            // 获取房间信息（用于获取roomId）
            RoomResponse room = roomService.getRoomByCode(roomCode);
            String roomId = room.getRoomId().toString();
            
            // 执行离开房间逻辑，获取更新后的玩家列表
            RoomPlayersResponse playersResponse = roomService.leaveRoom(userId, roomCode);
            
            // 解析特殊房间代码以确定是否房间关闭或主机转移
            String originalRoomCode = roomCode;
            boolean roomClosed = false;
            String newHostUsername = null;
            
            if (playersResponse.getRoomCode().startsWith("CLOSED:")) {
                roomClosed = true;
                originalRoomCode = playersResponse.getRoomCode().substring(7); // Remove "CLOSED:" prefix
            } else if (playersResponse.getRoomCode().startsWith("HOST:")) {
                String[] parts = playersResponse.getRoomCode().split(":");
                if (parts.length >= 3) {
                    newHostUsername = parts[1];
                    originalRoomCode = parts[2];
                }
            }
            
            // 如果房间关闭，广播 ROOM_CLOSED 事件
            if (roomClosed) {
                Map<String, Object> closedEventData = new HashMap<>();
                closedEventData.put("roomId", roomId);
                closedEventData.put("roomCode", originalRoomCode);
                closedEventData.put("timestamp", System.currentTimeMillis());
                
                roomEventController.broadcastRoomEventWithData(
                    roomId,
                    "ROOM_CLOSED",
                    closedEventData
                );
                
                return ResponseEntity.ok(ApiResponse.success("房间已关闭", null));
            }
            
            // 如果房主转移，广播 HOST_TRANSFERRED 事件
            if (newHostUsername != null) {
                Map<String, Object> hostTransferData = new HashMap<>();
                hostTransferData.put("roomId", roomId);
                hostTransferData.put("newHost", newHostUsername);
                hostTransferData.put("roomCode", originalRoomCode);
                hostTransferData.put("timestamp", System.currentTimeMillis());
                
                roomEventController.broadcastRoomEventWithData(
                    roomId,
                    "HOST_TRANSFERRED",
                    hostTransferData
                );
            }
            
            // 构建 PLAYER_LEFT 广播数据
            Map<String, Object> eventData = new HashMap<>();
            
            // leftPlayer 信息
            Map<String, Object> leftPlayer = new HashMap<>();
            leftPlayer.put("userId", userId.toString());
            leftPlayer.put("username", username);
            eventData.put("leftPlayer", leftPlayer);
            
            // 完整玩家列表
            List<PlayerInfoResponse> players = playersResponse.getPlayers();
            eventData.put("players", players);
            
            // 房间信息
            Map<String, Object> roomInfo = new HashMap<>();
            roomInfo.put("currentPlayers", players.size());
            roomInfo.put("maxPlayers", room.getMaxPlayers());
            roomInfo.put("status", room.getStatus());
            eventData.put("roomInfo", roomInfo);
            
            // 广播 PLAYER_LEFT 事件（包含完整玩家列表）
            roomEventController.broadcastRoomEventWithData(
                roomId,
                "PLAYER_LEFT",
                eventData
            );
            
            return ResponseEntity.ok(ApiResponse.success("离开房间成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}