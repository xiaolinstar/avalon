package cn.xiaolin.avalon.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JoinRoomRequest {
    @NotBlank(message = "房间代码不能为空")
    private String roomCode;
}