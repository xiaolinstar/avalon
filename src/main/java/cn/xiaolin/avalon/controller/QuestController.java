package cn.xiaolin.avalon.controller;

import cn.xiaolin.avalon.dto.Result;
import cn.xiaolin.avalon.entity.Quest;
import cn.xiaolin.avalon.service.QuestService;
import cn.xiaolin.avalon.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
@Tag(name = "任务接口", description = "游戏任务相关接口")
public class QuestController {
    private final QuestService questService;
    private final JwtUtil jwtUtil;

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
            questService.startQuest(gameId, isFirstQuest);
            
            if (isFirstQuest) {
                return ResponseEntity.ok(Result.success("第一个任务开始成功", null));
            } else {
                return ResponseEntity.ok(Result.success("任务开始成功", null));
            }
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
            List<Quest> quests = questService.getGameQuests(gameId);
            return ResponseEntity.ok(Result.success("获取游戏任务列表成功", quests));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 执行任务接口
     * @param gameId 游戏ID
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
            @Parameter(description = "JWT Token", required = true)
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody cn.xiaolin.avalon.dto.ExecuteQuestRequest request) {
        try {
            String token = authorizationHeader.substring(7);
            UUID userId = jwtUtil.getUserIdFromToken(token);

            questService.executeQuest(gameId, userId, request);
            return ResponseEntity.ok(Result.success("任务执行成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }
}