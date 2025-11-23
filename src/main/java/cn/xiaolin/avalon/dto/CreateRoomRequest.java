package cn.xiaolin.avalon.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoomRequest {
    @NotNull(message = "最大玩家数不能为空")
    @Min(value = 5, message = "最少需要5个玩家")
    @Max(value = 10, message = "最多支持10个玩家")
    private Integer maxPlayers;

    private String roleConfig;
}