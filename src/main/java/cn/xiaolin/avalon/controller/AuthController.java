package cn.xiaolin.avalon.controller;

import cn.xiaolin.avalon.dto.AuthResponse;
import cn.xiaolin.avalon.dto.LoginRequest;
import cn.xiaolin.avalon.dto.RegisterRequest;
import cn.xiaolin.avalon.dto.Result;
import cn.xiaolin.avalon.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证接口", description = "用户注册和登录相关接口")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "注册新用户账户")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "注册成功",
                    content = {@Content(mediaType = "application/json", 
                            schema = @Schema(implementation = Result.class))}),
            @ApiResponse(responseCode = "400", description = "注册失败",
                    content = {@Content(mediaType = "application/json", 
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        if (response.isSuccess()) {
            return ResponseEntity.ok(Result.success("注册成功", response));
        } else {
            return ResponseEntity.badRequest().body(Result.error(response.getMessage()));
        }
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户使用用户名和密码登录系统")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "登录成功",
                    content = {@Content(mediaType = "application/json", 
                            schema = @Schema(implementation = Result.class))}),
            @ApiResponse(responseCode = "400", description = "登录失败",
                    content = {@Content(mediaType = "application/json", 
                            schema = @Schema(implementation = Result.class))})
    })
    public ResponseEntity<Result<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        if (response.isSuccess()) {
            return ResponseEntity.ok(Result.success("登录成功", response));
        } else {
            return ResponseEntity.badRequest().body(Result.error(response.getMessage()));
        }
    }
}