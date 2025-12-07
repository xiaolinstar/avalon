package cn.xiaolin.avalon.controller;

import cn.xiaolin.avalon.dto.Result;
import cn.xiaolin.avalon.dto.CreateRoomRequest;
import cn.xiaolin.avalon.dto.JoinRoomRequest;
import cn.xiaolin.avalon.dto.RoomResponse;
import cn.xiaolin.avalon.dto.RoomPlayersResponse;
import cn.xiaolin.avalon.dto.PlayerInfoResponse;
import cn.xiaolin.avalon.service.RoomService;
import cn.xiaolin.avalon.utils.JwtUtil;
import cn.xiaolin.avalon.websocket.RoomEventController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "房间接口", description = "游戏房间管理相关接口")
public class RoomController {
    private final RoomService roomService;
    private final JwtUtil jwtUtil;
    private final RoomEventController roomEventController;

    @PostMapping
    @Operation(summary = "创建房间", description = "创建一个新的游戏房间")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "房间创建成功",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))}),
            @ApiResponse(responseCode = "400", description = "房间创建失败",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<RoomResponse>> createRoom(
            @Parameter(description = "JWT Token", required = true)
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody CreateRoomRequest request) {
        try {
            String token = authorizationHeader.substring(7);
            UUID userId = jwtUtil.getUserIdFromToken(token);

            RoomResponse roomResponse = roomService.createRoom(userId, request);
            return ResponseEntity.ok(Result.success("房间创建成功", roomResponse));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @GetMapping("/{roomCode}")
    @Operation(summary = "获取房间信息", description = "根据房间代码获取房间详细信息")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取房间信息成功",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))}),
            @ApiResponse(responseCode = "400", description = "获取房间信息失败",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<RoomResponse>> getRoom(
            @Parameter(description = "房间代码", required = true)
            @PathVariable String roomCode) {
        try {
            RoomResponse roomResponse = roomService.getRoomByCode(roomCode);
            return ResponseEntity.ok(Result.success("获取房间信息成功", roomResponse));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @GetMapping("/id/{roomId}")
    @Operation(summary = "根据ID获取房间信息", description = "根据房间UUID获取房间详细信息")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取房间信息成功",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))}),
            @ApiResponse(responseCode = "400", description = "获取房间信息失败",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<RoomResponse>> getRoomById(
            @Parameter(description = "房间ID", required = true)
            @PathVariable String roomId) {
        try {
            UUID roomUuid = UUID.fromString(roomId);
            RoomResponse roomResponse = roomService.getRoomById(roomUuid);
            return ResponseEntity.ok(Result.success("获取房间信息成功", roomResponse));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Result.error("房间ID格式错误"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @GetMapping("/{roomCode}/players")
    @Operation(summary = "获取房间玩家列表", description = "获取指定房间内的所有玩家信息")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "获取房间玩家列表成功",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))}),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "获取房间玩家列表失败",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<RoomPlayersResponse>> getRoomPlayers(
            @Parameter(description = "房间代码", required = true)
            @PathVariable String roomCode) {
        try {
            RoomPlayersResponse playersResponse = roomService.getRoomPlayers(roomCode);
            return ResponseEntity.ok(Result.success("获取房间玩家列表成功", playersResponse));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @PostMapping("/{roomCode}")
    @Operation(summary = "加入房间", description = "用户加入指定的游戏房间")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "加入房间成功",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))}),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "加入房间失败",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<RoomResponse>> joinRoom(
            @Parameter(description = "JWT Token", required = true)
            @RequestHeader("Authorization") String authorizationHeader,
            @Parameter(description = "房间代码", required = true)
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

            return ResponseEntity.ok(Result.success("加入房间成功", roomResponse));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{roomCode}")
    @Operation(summary = "离开房间", description = "用户离开指定的游戏房间")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "离开房间成功",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))}),
            @ApiResponse(responseCode = "400", description = "离开房间失败",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<Void>> leaveRoom(
            @Parameter(description = "JWT Token", required = true)
            @RequestHeader("Authorization") String authorizationHeader,
            @Parameter(description = "房间代码", required = true)
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

                return ResponseEntity.ok(Result.success("房间已关闭", null));
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

            return ResponseEntity.ok(Result.success("离开房间成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }
}