package cn.xiaolin.avalon.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssassinationRequest {
    private UUID targetPlayerId; // 刺杀目标玩家ID
    private String content; // 刺杀理由（可选）
}