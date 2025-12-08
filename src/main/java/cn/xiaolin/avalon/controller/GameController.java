package cn.xiaolin.avalon.controller;

import cn.xiaolin.avalon.dto.Result;
import cn.xiaolin.avalon.dto.AssassinationRequest;
import cn.xiaolin.avalon.dto.GameStateResponse;
import cn.xiaolin.avalon.dto.RoleInfoResponse;
import cn.xiaolin.avalon.dto.GameStatisticsResponse;
import cn.xiaolin.avalon.entity.Game;
import cn.xiaolin.avalon.entity.GamePlayer;
import cn.xiaolin.avalon.service.GameService;
import cn.xiaolin.avalon.service.GameStateService;
import cn.xiaolin.avalon.service.AssassinationService;
import cn.xiaolin.avalon.service.GameStatisticsService;
import cn.xiaolin.avalon.utils.JwtUtil;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
@Tag(name = "游戏接口", description = "游戏核心逻辑相关接口")
public class GameController {
    private final GameService gameService;
    private final GameStateService gameStateService;
    private final AssassinationService assassinationService;
    private final GameStatisticsService gameStatisticsService;
    private final JwtUtil jwtUtil;

    @PostMapping("/{roomId}/start")
    @Operation(summary = "开始游戏", description = "在指定房间内开始游戏")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "游戏开始成功",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))}),
            @ApiResponse(responseCode = "400", description = "游戏开始失败",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<String>> startGame(
            @Parameter(description = "房间ID", required = true)
            @PathVariable UUID roomId) {
        try {
            // 根据roomId获取房间，然后获取roomCode
            // 这里需要修改GameService中的startGame方法来接受roomId而不是roomCode
            gameService.startGame(roomId);
            return ResponseEntity.ok(Result.success("操作成功", "游戏开始成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @GetMapping("/{gameId}")
    @Operation(summary = "获取游戏信息", description = "获取指定游戏的完整信息，体现资源状态转移的理念")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取游戏信息成功",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))}),
            @ApiResponse(responseCode = "400", description = "获取游戏信息失败",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<Game>> getGame(
            @Parameter(description = "游戏ID", required = true)
            @PathVariable UUID gameId) {
        try {
            Game game = gameService.getGameById(gameId);
            return ResponseEntity.ok(Result.success("获取游戏信息成功", game));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @GetMapping("/{gameId}/players")
    @Operation(summary = "获取游戏参与者", description = "获取指定游戏的所有参与者信息")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取游戏参与者成功",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))}),
            @ApiResponse(responseCode = "400", description = "获取游戏参与者失败",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<List<GamePlayer>>> getGamePlayers(
            @Parameter(description = "游戏ID", required = true)
            @PathVariable UUID gameId) {
        try {
            List<GamePlayer> players = gameService.getGamePlayers(gameId);
            return ResponseEntity.ok(Result.success("获取游戏参与者成功", players));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @GetMapping("/{gameId}/state")
    @Operation(summary = "获取游戏状态详情", description = "获取指定游戏的详细状态信息，包括玩家视角的特定信息")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取游戏状态详情成功",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))}),
            @ApiResponse(responseCode = "400", description = "获取游戏状态详情失败",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<GameStateResponse>> getGameState(
            @Parameter(description = "游戏ID", required = true)
            @PathVariable UUID gameId,
            @Parameter(description = "JWT Token", required = true)
            @RequestHeader("Authorization") String authorizationHeader) {
        try {
            String token = authorizationHeader.substring(7);
            UUID userId = jwtUtil.getUserIdFromToken(token);

            GameStateResponse gameState = gameStateService.getGameState(gameId, userId);
            return ResponseEntity.ok(Result.success("获取游戏状态详情成功", gameState));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @GetMapping("/{gameId}/role-info")
    @Operation(summary = "获取角色信息", description = "获取当前用户在指定游戏中的角色信息")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取角色信息成功",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))}),
            @ApiResponse(responseCode = "400", description = "获取角色信息失败",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<RoleInfoResponse>> getRoleInfo(
            @Parameter(description = "游戏ID", required = true)
            @PathVariable UUID gameId,
            @Parameter(description = "JWT Token", required = true)
            @RequestHeader("Authorization") String authorizationHeader) {
        try {
            String token = authorizationHeader.substring(7); // Remove "Bearer " prefix
            UUID userId = jwtUtil.getUserIdFromToken(token);

            RoleInfoResponse roleInfo = gameStateService.getRoleInfo(gameId, userId);
            return ResponseEntity.ok(Result.success("操作成功", roleInfo));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @PostMapping("/{gameId}/assassinate")
    @Operation(summary = "执行刺杀", description = "邪恶阵营玩家在游戏结束时尝试刺杀梅林")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "刺杀处理成功",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))}),
            @ApiResponse(responseCode = "400", description = "刺杀处理失败",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<Boolean>> assassinate(
            @Parameter(description = "游戏ID", required = true)
            @PathVariable UUID gameId,
            @Parameter(description = "JWT Token", required = true)
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody AssassinationRequest request) {
        try {
            String token = authorizationHeader.substring(7);
            UUID userId = jwtUtil.getUserIdFromToken(token);

            boolean success = assassinationService.processAssassination(gameId, userId, request);
            return ResponseEntity.ok(Result.success("刺杀处理成功", success));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @GetMapping("/{gameId}/assassination-candidates")
    @Operation(summary = "获取刺杀候选人", description = "获取可被刺杀的玩家候选人列表")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取刺杀候选人成功",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))}),
            @ApiResponse(responseCode = "400", description = "获取刺杀候选人失败",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<List<GamePlayer>>> getAssassinationCandidates(
            @Parameter(description = "游戏ID", required = true)
            @PathVariable UUID gameId) {
        try {
            List<GamePlayer> candidates = assassinationService.getAssassinationCandidates(gameId);
            return ResponseEntity.ok(Result.success("获取刺杀候选人成功", candidates));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @GetMapping("/{gameId}/statistics")
    @Operation(summary = "获取游戏统计信息", description = "获取指定游戏的统计信息")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取游戏统计信息成功",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))}),
            @ApiResponse(responseCode = "400", description = "获取游戏统计信息失败",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<GameStatisticsResponse>> getGameStatistics(
            @Parameter(description = "游戏ID", required = true)
            @PathVariable UUID gameId) {
        try {
            GameStatisticsResponse statistics = gameStatisticsService.getGameStatistics(gameId);
            return ResponseEntity.ok(Result.success("获取游戏统计信息成功", statistics));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }
}