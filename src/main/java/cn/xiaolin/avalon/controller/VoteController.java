package cn.xiaolin.avalon.controller;

import cn.xiaolin.avalon.dto.Result;
import cn.xiaolin.avalon.dto.VoteRequest;
import cn.xiaolin.avalon.entity.Vote;
import cn.xiaolin.avalon.service.VoteService;
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
@Tag(name = "投票接口", description = "游戏投票相关接口")
public class VoteController {
    private final VoteService voteService;
    private final JwtUtil jwtUtil;

    @PostMapping("/{gameId}/votes")
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
            @RequestBody VoteRequest request) {
        try {
            String token = authorizationHeader.substring(7);
            UUID userId = jwtUtil.getUserIdFromToken(token);

            Vote vote = voteService.submitVote(gameId, userId, request);
            return ResponseEntity.ok(Result.success("投票成功", vote));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }
}