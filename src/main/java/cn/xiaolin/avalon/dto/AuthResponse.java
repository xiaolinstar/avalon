package cn.xiaolin.avalon.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private boolean success;
    private String message;
    private UserData data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserData {
        private UUID userId;
        private String username;
        private String token;
    }
}