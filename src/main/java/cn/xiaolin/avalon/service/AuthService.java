package cn.xiaolin.avalon.service;

import cn.xiaolin.avalon.dto.AuthResponse;
import cn.xiaolin.avalon.dto.LoginRequest;
import cn.xiaolin.avalon.dto.RegisterRequest;
import cn.xiaolin.avalon.entity.User;
import cn.xiaolin.avalon.repository.UserRepository;
import cn.xiaolin.avalon.utils.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            return new AuthResponse(false, "用户名已存在", null);
        }

        // 检查邮箱是否已存在
        if (userRepository.existsByEmail(request.getEmail())) {
            return new AuthResponse(false, "邮箱已被注册", null);
        }

        // 创建新用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        User savedUser = userRepository.save(user);

        // 生成JWT token
        String token = jwtUtil.generateToken(savedUser.getId(), savedUser.getUsername());

        AuthResponse.UserData userData = new AuthResponse.UserData(
            savedUser.getId(),
            savedUser.getUsername(),
            token
        );

        return new AuthResponse(true, "注册成功", userData);
    }

    public AuthResponse login(LoginRequest request) {
        // 根据用户名或邮箱查找用户
        User user = userRepository.findByUsername(request.getUsername())
            .orElseGet(() -> userRepository.findByEmail(request.getUsername()).orElse(null));

        if (user == null) {
            return new AuthResponse(false, "用户不存在", null);
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return new AuthResponse(false, "用户名/密码错误", null);
        }

        // 生成JWT token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        AuthResponse.UserData userData = new AuthResponse.UserData(
            user.getId(),
            user.getUsername(),
            token
        );

        return new AuthResponse(true, "登录成功", userData);
    }
}