package cn.xiaolin.avalon.controller;

import cn.xiaolin.avalon.dto.*;
import cn.xiaolin.avalon.entity.Game;
import cn.xiaolin.avalon.entity.GamePlayer;
import cn.xiaolin.avalon.entity.Quest;
import cn.xiaolin.avalon.entity.Vote;
import cn.xiaolin.avalon.service.GameService;
import cn.xiaolin.avalon.service.GameStateService;
import cn.xiaolin.avalon.service.AssassinationService;
import cn.xiaolin.avalon.service.GameStatisticsService;
import cn.xiaolin.avalon.utils.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {
    private final GameService gameService;
    private final GameStateService gameStateService;
    private final AssassinationService assassinationService;
    private final GameStatisticsService gameStatisticsService;
    private final JwtUtil jwtUtil;

    @PostMapping("/{roomId}/start")
    public ResponseEntity<ApiResponse<String>> startGame(@PathVariable UUID roomId) {
        try {
            gameService.startGame(roomId);
            return ResponseEntity.ok(ApiResponse.success("游戏开始成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 开始任务接口
     * @param gameId 游戏ID
     * @param isFirstQuest 是否为第一个任务（可选参数）
     * @return 启动结果
     */
    @PostMapping("/{gameId}/quests")
    public ResponseEntity<ApiResponse<String>> startQuest(
            @PathVariable UUID gameId,
            @RequestParam(required = false, defaultValue = "false") boolean isFirstQuest) {
        try {
            gameService.startQuest(gameId, isFirstQuest);
            if (isFirstQuest) {
                return ResponseEntity.ok(ApiResponse.success("第一个任务开始成功", ""));
            } else {
                return ResponseEntity.ok(ApiResponse.success("任务开始成功", ""));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{gameId}/players")
    public ResponseEntity<ApiResponse<List<GamePlayer>>> getGamePlayers(@PathVariable UUID gameId) {
        try {
            List<GamePlayer> players = gameService.getGamePlayers(gameId);
            return ResponseEntity.ok(ApiResponse.success(players));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{gameId}/quests")
    public ResponseEntity<ApiResponse<List<Quest>>> getGameQuests(@PathVariable UUID gameId) {
        try {
            List<Quest> quests = gameService.getGameQuests(gameId);
            return ResponseEntity.ok(ApiResponse.success(quests));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{gameId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getGameStatus(@PathVariable UUID gameId) {
        try {
            Game game = gameService.getGameByRoomId(gameId);
            Map<String, Object> status = Map.of(
                "gameId", game.getId(),
                "status", game.getStatus(),
                "currentRound", game.getCurrentRound(),
                "startedAt", game.getStartedAt()
            );
            return ResponseEntity.ok(ApiResponse.success(status));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{gameId}/state")
    public ResponseEntity<ApiResponse<GameStateResponse>> getGameState(
            @PathVariable UUID gameId,
            @RequestHeader("Authorization") String authorizationHeader) {
        try {
            String token = authorizationHeader.substring(7);
            UUID userId = jwtUtil.getUserIdFromToken(token);
            
            GameStateResponse gameState = gameStateService.getGameState(gameId, userId);
            return ResponseEntity.ok(ApiResponse.success(gameState));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{gameId}/role-info")
    public ResponseEntity<ApiResponse<RoleInfoResponse>> getRoleInfo(
            @PathVariable UUID gameId,
            @RequestHeader("Authorization") String authorizationHeader) {
        try {
            String token = authorizationHeader.substring(7); // Remove "Bearer " prefix
            UUID userId = jwtUtil.getUserIdFromToken(token);
            
            RoleInfoResponse roleInfo = gameStateService.getRoleInfo(gameId, userId);
            return ResponseEntity.ok(ApiResponse.success(roleInfo));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{gameId}/propose-team")
    public ResponseEntity<ApiResponse<Quest>> proposeTeam(
            @PathVariable UUID gameId,
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody ProposeTeamRequest request) {
        try {
            String token = authorizationHeader.substring(7);
            UUID userId = jwtUtil.getUserIdFromToken(token);
            
            Quest quest = gameService.proposeTeam(gameId, userId, request);
            return ResponseEntity.ok(ApiResponse.success("队伍提议成功", quest));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{gameId}/vote")
    public ResponseEntity<ApiResponse<Vote>> submitVote(
            @PathVariable UUID gameId,
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody VoteRequest request) {
        try {
            String token = authorizationHeader.substring(7);
            UUID userId = jwtUtil.getUserIdFromToken(token);
            
            Vote vote = gameService.submitVote(gameId, userId, request);
            return ResponseEntity.ok(ApiResponse.success("投票成功", vote));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
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
    public ResponseEntity<ApiResponse<String>> executeQuest(
            @PathVariable UUID gameId,
            @RequestParam Integer round,
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody ExecuteQuestRequest request) {
        try {
            String token = authorizationHeader.substring(7);
            UUID userId = jwtUtil.getUserIdFromToken(token);
            
            gameService.executeQuest(gameId, userId, request);
            return ResponseEntity.ok(ApiResponse.success("任务执行成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{gameId}/assassinate")
    public ResponseEntity<ApiResponse<Boolean>> assassinate(
            @PathVariable UUID gameId,
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody AssassinationRequest request) {
        try {
            String token = authorizationHeader.substring(7);
            UUID userId = jwtUtil.getUserIdFromToken(token);
            
            boolean success = assassinationService.processAssassination(gameId, userId, request);
            return ResponseEntity.ok(ApiResponse.success("刺杀处理成功", success));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{gameId}/assassination-candidates")
    public ResponseEntity<ApiResponse<List<GamePlayer>>> getAssassinationCandidates(
            @PathVariable UUID gameId) {
        try {
            List<GamePlayer> candidates = assassinationService.getAssassinationCandidates(gameId);
            return ResponseEntity.ok(ApiResponse.success(candidates));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{gameId}/statistics")
    public ResponseEntity<ApiResponse<GameStatisticsResponse>> getGameStatistics(
            @PathVariable UUID gameId) {
        try {
            GameStatisticsResponse statistics = gameStatisticsService.getGameStatistics(gameId);
            return ResponseEntity.ok(ApiResponse.success(statistics));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}