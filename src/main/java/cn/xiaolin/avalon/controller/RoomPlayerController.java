package cn.xiaolin.avalon.controller;

import cn.xiaolin.avalon.dto.Result;
import cn.xiaolin.avalon.dto.JoinRoomRequest;
import cn.xiaolin.avalon.dto.RoomResponse;
import cn.xiaolin.avalon.dto.RoomPlayersResponse;
import cn.xiaolin.avalon.dto.PlayerInfoResponse;
import cn.xiaolin.avalon.service.RoomPlayerService;
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
@RequestMapping("/api/room-players")
@RequiredArgsConstructor
@Tag(name = "房间玩家接口", description = "房间玩家关系管理相关接口")
public class RoomPlayerController {
    private final RoomPlayerService roomPlayerService;
    private final RoomService roomService;
    private final JwtUtil jwtUtil;
    private final RoomEventController roomEventController;

    @PostMapping
    @Operation(summary = "创建房间玩家关系", description = "在房间中创建或激活玩家状态")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "玩家状态更新成功",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))}),
            @ApiResponse(responseCode = "400", description = "玩家状态更新失败",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<RoomResponse>> createRoomPlayer(
            @Parameter(description = "JWT Token", required = true)
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody JoinRoomRequest request) {
        try {
            String token = authorizationHeader.substring(7);
            UUID userId = jwtUtil.getUserIdFromToken(token);

            // 使用RoomPlayerService处理加入房间逻辑
            cn.xiaolin.avalon.entity.RoomPlayer roomPlayer = roomPlayerService.joinRoom(userId, request);

            // 获取房间信息用于返回
            RoomResponse roomResponse = roomService.getRoomByCode(request.getRoomCode());

            // 获取当前用户的用户名
            String username = jwtUtil.getUsernameFromToken(token);

            // 广播玩家加入房间
            roomEventController.broadcastRoomEvent(
                roomResponse.getRoomId().toString(),
                "PLAYER_JOINED",
                userId.toString(),
                username
            );

            return ResponseEntity.ok(Result.success("玩家状态更新成功", roomResponse));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{roomPlayerId}")
    @Operation(summary = "删除房间玩家关系", description = "从房间中移除玩家（设置为非活跃状态）")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "玩家状态更新成功",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))}),
            @ApiResponse(responseCode = "400", description = "玩家状态更新失败",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<Void>> deleteRoomPlayer(
            @Parameter(description = "JWT Token", required = true)
            @RequestHeader("Authorization") String authorizationHeader,
            @Parameter(description = "房间玩家ID", required = true)
            @PathVariable UUID roomPlayerId) {
        try {
            String token = authorizationHeader.substring(7);
            UUID userId = jwtUtil.getUserIdFromToken(token);
            String username = jwtUtil.getUsernameFromToken(token);

            // 使用RoomPlayerService通过roomPlayerId离开房间
            RoomPlayersResponse playersResponse = roomPlayerService.leaveRoomByRoomPlayerId(userId, roomPlayerId);

            // 获取房间信息用于广播
            // 注意：这部分实现需要根据实际情况调整，可能需要在服务层返回更多信息
            
            return ResponseEntity.ok(Result.success("玩家状态更新成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }
}