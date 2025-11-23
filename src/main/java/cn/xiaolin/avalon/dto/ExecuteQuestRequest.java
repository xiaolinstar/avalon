package cn.xiaolin.avalon.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteQuestRequest {
    @NotNull(message = "任务执行结果不能为空")
    private Boolean success; // true: 任务成功, false: 任务失败
    
    private String content; // 执行描述（可选）
}