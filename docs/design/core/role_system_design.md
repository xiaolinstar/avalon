# 角色系统设计

## Role类（角色系统）

职能：能把"看见谁"、"能否投失败卡"等**角色差异规则**封装成枚举，方便访问角色属性和规则。

* 使用枚举类型来定义所有可用角色，每个角色包含其基本属性和规则

```java
public enum Role {
    // 正义阵营
    MERLIN("merlin", "梅林", "正义", "你知道邪恶阵营的所有成员，除了莫德雷德"),
    PERCIVAL("percival", "派西维尔", "正义", "你知道梅林和莫甘娜，但不知道谁是谁"),
    LOYAL_SERVANT("loyal_servant", "亚瑟的忠臣", "正义", "你是忠诚的骑士，目标是完成神圣任务"),
    
    // 邪恶阵营
    MORGANA("morgana", "莫甘娜", "邪恶", "你出现在派西维尔的视野中，看起来像梅林"),
    ASSASSIN("assassin", "刺客", "邪恶", "游戏结束时，你可以尝试刺杀梅林"),
    MORDRED("mordred", "莫德雷德", "邪恶", "梅林不知道你的身份"),
    MINION("minion", "间谍", "邪恶", "你是邪恶阵营的普通成员"),
    OBERON("oberon", "奥伯伦", "邪恶", "其他邪恶成员不知道你的身份，你也不知道他们");

    private final String code;
    private final String name;
    private final String alignment;
    private final String description;
}
```

## GamePlayer角色查看方法设计

### 方法名: `viewRoleInfo()`

### 职责: 当游戏开始后，允许GamePlayer查看自己的角色信息及相关可见信息

### 实现细节:

1. **角色信息获取**:
   - GamePlayer可以通过调用GameStateService的getGameState()方法获取自己的角色信息
   - 返回信息包括:
     * 自身角色名称 (role)
     * 所属阵营 (alignment)
     * 可见的其他玩家信息 (visibilityInfo)

2. **可见性信息处理**:
   - 使用RoleVisibilityService来计算当前玩家可以看到的其他玩家信息
   - 不同角色有不同的可见性规则:
     * 梅林(Merlin): 可以看到除莫德雷德外的所有邪恶阵营玩家
     * 派西维尔(Percival): 可以看到梅林和莫甘娜，但不知道具体谁是誰
     * 邪恶阵营(Evil): 可以看到其他邪恶阵营成员(奥伯伦除外)
     * 忠臣(Loyal Servant): 无特殊可见信息

3. **API接口设计**:
   - Endpoint: `GET /api/games/{gameId}/state`
   - 请求头需要包含JWT Token用于身份验证
   - 返回GameStateResponse对象，其中包含玩家的角色信息和可见性信息

4. **安全考虑**:
   - 只有游戏参与者才能查看自己的角色信息
   - 角色信息通过WebSocket在游戏开始时推送一次，之后通过API请求获取
   - 敏感信息(如其他玩家的真实角色)只提供给有权查看的玩家

5. **前端交互**:
   - 前端在游戏状态变为ROLE_VIEWING时，自动调用此方法获取角色信息
   - 角色揭示页显示玩家自己的角色和可见信息
   - 玩家确认查看角色后，可以继续游戏流程
