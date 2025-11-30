package cn.xiaolin.avalon.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private UUID roomId;
    private String roomCode;
    private Integer maxPlayers;
    private String status;
    private String creatorName;
    private Integer currentPlayers; // 新增：当前玩家数量
    @JsonSerialize(using = ToStringSerializer.class)
    private UUID gameId; // 新增：游戏ID（如果游戏已开始）
}