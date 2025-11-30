package cn.xiaolin.avalon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家角色信息响应DTO
 * 包含玩家在游戏中的角色详情和可见性信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleInfoResponse {
    /**
     * 游戏ID
     */
    private UUID gameId;
    
    /**
     * 角色代码（英文标识）
     */
    private String role;
    
    /**
     * 角色名称（中文）
     */
    private String roleName;
    
    /**
     * 阵营（正义/邪恶）
     */
    private String alignment;
    
    /**
     * 角色描述信息
     */
    private String description;
    
    /**
     * 可见性信息，根据不同角色显示不同的可见玩家信息
     * key: 可见性类型
     * value: 玩家用户名列表
     */
    private Map<String, List<String>> visibilityInfo;
}