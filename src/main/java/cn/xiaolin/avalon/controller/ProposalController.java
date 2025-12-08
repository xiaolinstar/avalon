package cn.xiaolin.avalon.controller;

import cn.xiaolin.avalon.dto.Result;
import cn.xiaolin.avalon.dto.ProposeTeamRequest;
import cn.xiaolin.avalon.entity.Quest;
import cn.xiaolin.avalon.service.ProposalService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
@Tag(name = "提案接口", description = "队伍提案相关接口")
public class ProposalController {
    private final ProposalService proposalService;
    private final JwtUtil jwtUtil;

    @PostMapping("/{gameId}/proposals")
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
            @RequestBody ProposeTeamRequest request) {
        try {
            String token = authorizationHeader.substring(7);
            UUID userId = jwtUtil.getUserIdFromToken(token);

            Quest quest = proposalService.proposeTeam(gameId, userId, request);
            return ResponseEntity.ok(Result.success("队伍提议成功", quest));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }
}