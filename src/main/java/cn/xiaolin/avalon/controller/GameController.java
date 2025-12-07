package cn.xiaolin.avalon.controller;

import cn.xiaolin.avalon.dto.Result;
import cn.xiaolin.avalon.dto.ProposeTeamRequest;
import cn.xiaolin.avalon.dto.ExecuteQuestRequest;
import cn.xiaolin.avalon.dto.VoteRequest;
import cn.xiaolin.avalon.dto.AssassinationRequest;
import cn.xiaolin.avalon.dto.GameStateResponse;
import cn.xiaolin.avalon.dto.RoleInfoResponse;
import cn.xiaolin.avalon.dto.GameStatisticsResponse;
import cn.xiaolin.avalon.entity.Game;
import cn.xiaolin.avalon.entity.GamePlayer;
import cn.xiaolin.avalon.entity.Quest;
import cn.xiaolin.avalon.entity.Vote;
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

    /**
     * 开始任务接口
     * @param gameId 游戏ID
     * @param isFirstQuest 是否为第一个任务（可选参数）
     * @return 启动结果
     */
    @PostMapping("/{gameId}/quests")
    @Operation(summary = "开始任务", description = "开始一个新的任务")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "任务开始成功",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))}),
            @ApiResponse(responseCode = "400", description = "任务开始失败",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<Void>> startQuest(
            @Parameter(description = "游戏ID", required = true)
            @PathVariable UUID gameId,
            @Parameter(description = "是否为第一个任务")
            @RequestParam(required = false, defaultValue = "false") boolean isFirstQuest) {
        try {
            gameService.startQuest(gameId, isFirstQuest);
            
            if (isFirstQuest) {
                return ResponseEntity.ok(Result.success("第一个任务开始成功", null));
            } else {
                return ResponseEntity.ok(Result.success("任务开始成功", null));
            }
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

    @GetMapping("/{gameId}/quests")
    @Operation(summary = "获取游戏任务列表", description = "获取指定游戏的所有任务信息")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取游戏任务列表成功",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))}),
            @ApiResponse(responseCode = "400", description = "获取游戏任务列表失败",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<List<Quest>>> getGameQuests(
            @Parameter(description = "游戏ID", required = true)
            @PathVariable UUID gameId) {
        try {
            List<Quest> quests = gameService.getGameQuests(gameId);
            return ResponseEntity.ok(Result.success("获取游戏任务列表成功", quests));
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

    @PostMapping("/{gameId}/propose-team")
    @Operation(summary = "提议队伍", description = "当前队长为当前任务提议执行队伍")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "队伍提议成功",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))}),
            @ApiResponse(responseCode = "400", description = "队伍提议失败",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<Quest>> proposeTeam(
            @Parameter(description = "游戏ID", required = true)
            @PathVariable UUID gameId,
            @Parameter(description = "JWT Token", required = true)
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody ProposeTeamRequest request) {
        try {
            String token = authorizationHeader.substring(7);
            UUID userId = jwtUtil.getUserIdFromToken(token);

            Quest quest = gameService.proposeTeam(gameId, userId, request);
            return ResponseEntity.ok(Result.success("队伍提议成功", quest));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @PostMapping("/{gameId}/vote")
    @Operation(summary = "提交投票", description = "玩家对当前提议的队伍进行投票")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "投票成功",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))}),
            @ApiResponse(responseCode = "400", description = "投票失败",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<Vote>> submitVote(
            @Parameter(description = "游戏ID", required = true)
            @PathVariable UUID gameId,
            @Parameter(description = "JWT Token", required = true)
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody VoteRequest request) {
        try {
            String token = authorizationHeader.substring(7);
            UUID userId = jwtUtil.getUserIdFromToken(token);

            Vote vote = gameService.submitVote(gameId, userId, request);
            return ResponseEntity.ok(Result.success("投票成功", vote));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 执行任务接口
     * @param gameId 游戏ID
     * @param round 回合数
     * @param authorizationHeader 授权头
     * @param request 请求体
     * @return 执行结果
     */
    @PostMapping("/{gameId}/quests/execute")
    @Operation(summary = "执行任务", description = "当前任务的参与者执行任务")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "任务执行成功",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))}),
            @ApiResponse(responseCode = "400", description = "任务执行失败",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<Void>> executeQuest(
            @Parameter(description = "游戏ID", required = true)
            @PathVariable UUID gameId,
//            @RequestParam Integer round,
            @Parameter(description = "JWT Token", required = true)
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody ExecuteQuestRequest request) {
        try {
            String token = authorizationHeader.substring(7);
            UUID userId = jwtUtil.getUserIdFromToken(token);

            gameService.executeQuest(gameId, userId, request);
            return ResponseEntity.ok(Result.success("任务执行成功", null));
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