package cn.xiaolin.avalon.websocket;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;

/**
 * 房间事件 - 支持携带完整数据的事件结构
 * 包含事件类型、房间ID、时间戳和可选的数据负载
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomEvent {
    /**
     * 事件类型：PLAYER_JOINED, PLAYER_LEFT
     */
    private String type;
    
    /**
     * 房间ID
     */
    private String roomId;
    
    /**
     * 时间戳
     */
    private Long timestamp;
    
    /**
     * 事件数据负载（可选）
     * 用于携带完整的玩家列表、房间信息等
     */
    private Map<String, Object> data;
    
    // 保留向后兼容的字段
    /**
     * 用户ID（向后兼容）
     */
    private String userId;
    
    /**
     * 用户名（向后兼容）
     */
    private String username;
}