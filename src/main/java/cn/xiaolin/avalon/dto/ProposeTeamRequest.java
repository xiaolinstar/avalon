package cn.xiaolin.avalon.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProposeTeamRequest {
    @NotNull(message = "队伍成员不能为空")
    private List<UUID> playerIds;
    
    private String content; // 提议理由（可选）
}