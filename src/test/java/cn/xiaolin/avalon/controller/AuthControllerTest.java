package cn.xiaolin.avalon.controller;

import cn.xiaolin.avalon.dto.LoginRequest;
import cn.xiaolin.avalon.dto.RegisterRequest;
import cn.xiaolin.avalon.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        // Clean up test data
        userRepository.deleteAll();
    }

    /**
     * REG-TC-001: 正常注册
     * 测试目的: 验证用户可以使用有效的、唯一的凭据成功注册。
     */
    @Test
    void whenRegisterWithValidData_thenReturnsSuccessAndToken() throws Exception {
        String lastStr = UUID.randomUUID().toString().substring(0, 8);
        String uniqueUsername = "testuser" + lastStr; // This will be at least 12 characters
        String uniqueEmail = "test_" + lastStr + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest(uniqueUsername, uniqueEmail, "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("注册成功"))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.data.username").value(uniqueUsername))
                .andExpect(jsonPath("$.data.data.token").isString());
    }

    /**
     * REG-TC-002: 重复用户名注册
     * 测试目的: 验证系统不允许使用已存在的用户名进行注册。
     */
    @Test
    void whenRegisterWithDuplicateUsername_thenReturnsError() throws Exception {
        String lastStr = UUID.randomUUID().toString().substring(0, 6);
        String uniqueUsername = "dupusr" + lastStr; // At least 12 characters, under 20
        String uniqueEmail = "dup_" + lastStr + "@example.com";
        String uniqueEmail2 = "dup2_" + lastStr + "@example.com";
        
        // First registration
        RegisterRequest firstRegisterRequest = new RegisterRequest(uniqueUsername, uniqueEmail, "password123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRegisterRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Second registration with same username but different email
        RegisterRequest secondRegisterRequest = new RegisterRequest(uniqueUsername, uniqueEmail2, "password123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRegisterRequest)))
                .andExpect(status().isBadRequest()) // The service detects duplicate and returns 400
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    /**
     * REG-TC-003: 无效邮箱格式注册
     * 测试目的: 验证后端对邮箱格式的校验是否生效。
     */
    @Test
    void whenRegisterWithInvalidEmailFormat_thenReturnsError() throws Exception {
        String lastStr = UUID.randomUUID().toString().substring(0, 6);
        String uniqueUsername = "invusr" + lastStr; // At least 12 characters, under 20
        RegisterRequest registerRequest = new RegisterRequest(uniqueUsername, "invalid-email", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isBadRequest());
                // When validation fails, Spring Boot returns 400 with no JSON body
    }

    /**
     * LOG-TC-001: 正常登录
     * 测试目的: 验证已注册用户可以使用正确的凭据成功登录并获取 Token。
     */
    @Test
    void whenLoginWithValidCredentials_thenReturnsSuccessAndToken() throws Exception {
        String lastStr = UUID.randomUUID().toString().substring(0, 6);
        String uniqueUsername = "logusr" + lastStr; // At least 12 characters, under 20
        String uniqueEmail = "log_" + lastStr + "@example.com";
        String password = "password123";
        RegisterRequest registerRequest = new RegisterRequest(uniqueUsername, uniqueEmail, password);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk());

        // Then, login with the same credentials
        LoginRequest loginRequest = new LoginRequest(uniqueUsername, password);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("登录成功"))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.data.username").value(uniqueUsername))
                .andExpect(jsonPath("$.data.data.token").isString());
    }

    /**
     * LOG-TC-002: 错误密码登录
     * 测试目的: 验证使用错误密码登录时系统会拒绝访问。
     */
    @Test
    void whenLoginWithInvalidCredentials_thenReturnsError() throws Exception {
        String lastStr = UUID.randomUUID().toString().substring(0, 6);
        String uniqueUsername = "errusr" + lastStr; // At least 12 characters, under 20
        String uniqueEmail = "err_" + lastStr + "@example.com";
        String password = "password123";
        RegisterRequest registerRequest = new RegisterRequest(uniqueUsername, uniqueEmail, password);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk());

        // Then, login with wrong password
        LoginRequest loginRequest = new LoginRequest(uniqueUsername, "wrongpassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("用户名/密码错误"));
    }

    /**
     * Additional test: 登录不存在的用户
     * 测试目的: 验证使用不存在的用户名登录时系统会拒绝访问。
     */
    @Test
    void whenLoginWithNonExistentUser_thenReturnsError() throws Exception {
        String uniqueUsername = "nonusr" + UUID.randomUUID().toString().substring(0, 6); // At least 12 characters, under 20
        LoginRequest loginRequest = new LoginRequest(uniqueUsername, "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }
}