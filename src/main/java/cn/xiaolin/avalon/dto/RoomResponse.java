package cn.xiaolin.avalon.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.UUID;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

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
}