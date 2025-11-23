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
public class VoteRequest {
    @NotNull(message = "投票类型不能为空")
    private String voteType; // approve 或 reject
    
    private String content; // 投票理由（可选）
}