# 面向对象建模

## 1. 用例模型（Use-Case Model）

| 用例编号 | 用例名称  | 参与者 | 前置条件         | 典型流程（主干）                                         |
| ---- | ----- | --- | ------------ | ------------------------------------------------ |
| UC1  | 注册/登录 | 玩家  | 无            | 1. 输入用户名+密码2. 系统校验并返回JWT3. 进入首页                  |
| UC2  | 创建房间  | 玩家  | 已登录          | 1. 点击"创建房间"2. 设置人数（5-10）3. 系统生成roomCode并返回房间页    |
| UC3  | 加入房间  | 玩家  | 拥有有效roomCode | 1. 输入roomCode2. 系统校验房间状态waiting3. 进入房间页          |
| UC4  | 分配角色  | 系统  | 房主点击"开始游戏"   | 1. 系统按人数与配置随机分配角色2. 生成Game与GamePlayer记录3. 推送角色数据 |
| UC5  | 查看身份  | 玩家  | 角色已分配        | 1. 进入/role-reveal页2. 长按解锁查看身份与可见列表3. 倒计时结束自动遮罩   |
| UC6  | 提议队伍  | 队长  | 轮到该玩家当队长     | 1. 拖拽玩家头像到出征区2. 点击"确认提议"3. 系统进入投票阶段              |
| UC7  | 投票    | 玩家  | 处于投票阶段       | 1. 点击"赞成/反对"2. 系统实时更新投票计数                        |
| UC8  | 计算投票  | 系统  | 所有玩家完成投票     | 1. 若赞成>反对：任务进入执行阶段2. 否则轮次+1，换队长重新提议              |
| UC9  | 执行任务  | 出征者 | 投票通过且轮到出征    | 1. 出征者选择"成功/失败"暗扣2. 提交后系统加密存储结果                  |
| UC10 | 公布结果  | 系统  | 所有出征者已提交     | 1. 按规则计算是否失败2. 更新任务格颜色3. 推送下一阶段                  |
| UC11 | 刺杀    | 刺客  | 好人取得3次任务胜利   | 1. 刺客选择刺杀目标2. 系统验证是否为梅林3. 决定最终胜负                 |
| UC12 | 公布胜负  | 系统  | 刺杀完成或任务失败3次  | 1. 显示胜负海报2. 横向时间轴揭示全部身份3. 生成战绩卡片                 |

## 2. 类模型（Class Model）

```mermaid
classDiagram
    class User {
        -UUID id
        -String username
        -String email
        -String passwordHash
        --register()
        --login()
    }
    class Room {
        -UUID id
        -String roomCode
        -UUID creatorId
        -int maxPlayers
        -RoomStatus status
        --generateCode(): String
    }
    class RoomPlayer {
        -UUID id
        -UUID roomId
        -UUID userId
        -boolean isHost
        -boolean isActive
        -int seatNumber
        -LocalDateTime joinedAt
        -LocalDateTime updatedAt
        --leaveRoom()
        --getActivePlayersInRoom()
    }
    class Game {
        -UUID id
        -UUID roomId
        -GameStatus status
        -int currentRound
        --start()
        --assignRoles()
        --nextRound()
    }
    class GamePlayer {
        -UUID id
        -UUID gameId
        -UUID userId
        -String role
        -String alignment
        -boolean isHost
        -int seatNumber
        -boolean isActive
        --vote()
        --executeQuest()
        --getVisiblePlayers()
        --chooseAssassinationTarget()
        --viewRoleInfo()
    }
    
    class RoleInfo {
        -String role
        -String alignment
        -Map<String, List<String>> visibilityInfo
    }
    
    class Role {
        <<enumeration>>
        MERLIN
        PERCIVAL
        LOYAL_SERVANT
        MORGANA
        ASSASSIN
        MORDRED
        MINION
        OBERON
        -code
        -name
        -alignment
        -description
        +getCode()
        +getName()
        +getAlignment()
        +getDescription()
        +isGood()
        +isEvil()
    }
    
    class Quest {
        -UUID id
        -int roundNumber
        -int requiredPlayers
        -int requiredFails
        -QuestStatus status
        --proposeTeam(List~GamePlayer~)
        --votePassed(): boolean
        --execute(List~QuestResult~)
    }
    class Vote {
        -UUID id
        -UUID questId
        -UUID playerId
        -VoteType voteType
    }
    class QuestResult {
        -UUID id
        -UUID questId
        -UUID playerId
        -boolean success
    }

    User "1" -- "*" RoomPlayer : joins
    Room "1" -- "*" RoomPlayer : contains
    Room "1" -- "1" Game : hosts
    Game "1" -- "*" GamePlayer : contains
    User "1" -- "*" GamePlayer : plays
    Game "1" -- "*" Quest : has
    Quest "1" -- "*" Vote : contains
    Quest "1" -- "*" QuestResult : has
    GamePlayer "1" -- "1" RoleInfo : has
```

## 3. 时序图（Sequence Diagram）

**场景 1：房主开始游戏 → 角色揭示**

```mermaid
sequenceDiagram
  participant P as 房主(前端)
  participant C as RoomController
  participant S as GameService
  participant R as RoleService
  participant DB as PostgreSQL
  participant W as WebSocket

  P->>C: POST /api/games/{gameId}/start
  C->>S: startGame(roomId)
  S->>DB: select room_players where room_id = roomId and is_active = true
  S->>R: assignRoles(playerList)
  R-->>S: Map<playerId, Role>
  S->>DB: insert game_players
  S->>W:W->>S: broadcast /topic/games/{gameId} GameStarted
  W->>P: 推送 GameStarted + roleData
  P->>P: 跳转 /games/{gameId}/role-reveal
  P->>P: 长按解锁查看身份
  P->>W: send /app/game.role-confirmed
  W->>S: 收到全部 confirmed
  S->>W:W->>S: broadcast /topic/games/{gameId} PhaseChanged(PROPOSING)
```

**场景 2：队长提议队伍 → 投票 → 任务执行**

```mermaid
sequenceDiagram
  participant P as 队长(前端)
  participant C as GameController
  participant S as GameService
  participant Q as QuestService
  participant DB as PostgreSQL
  participant W as WebSocket

  P->>C: POST /api/games/{gameId}/quests/{questNumber}/proposals
  C->>S: createProposal(questNumber, members)
  S->>Q: createProposal(questNumber, leader, members)
  Q->>DB: insert proposal
  S->>W:W->>S: broadcast /topic/games/{gameId} ProposalCreated
  W->>P: 显示投票按钮
  loop 每位玩家
    P->>C: POST /api/games/{gameId}/proposals/{proposalId}/votes
    C->>S: vote(proposalId, playerId, approve)
    S->>DB: insert vote
  end
  S->>Q: isProposalApproved()
  Q-->>S: true
  S->>W:W->>S: broadcast /topic/games/{gameId} VoteResult(APPROVED)
  S->>Q: startQuestExecution()
  Q->>W: private /topic/games/{gameId}/quest/execution 给出征者发执行页
  loop 出征者
    P->>C: POST /api/games/{gameId}/quests/{questNumber}/execution
    C->>S: submitQuestExecution(playerId, success)
    S->>DB: insert quest_execution(encrypted)
  end
  S->>Q: calculateQuestExecutionResult()
  Q-->>S: 任务成功/失败
  S->>W: broadcast /topic/games/{gameId} QuestExecutionCompleted(result)
```

## 4. 游戏阶段定义与流转

| 阶段代码          | 阶段名称 | 触发条件        | 前端页面         | 后端行为         |
| ------------- | ---- | ----------- | ------------ | ------------ |
| PREPARING     | 角色揭示 | 房主点击开始      | /role-reveal | 分配角色，推送角色数据  |
| PROPOSING     | 队长提议 | 上轮投票失败或新回合  | /game        | 标记当前队长，等待提议  |
| VOTING        | 全员投票 | 队长已提交名单     | /game        | 收集赞成/反对，实时计数 |
| VOTE_RESULT  | 投票结果 | 所有人已投票      | /game        | 计算通过/失败，推送结果 |
| EXECUTING     | 任务执行 | 投票通过        | /game        | 仅给出征者推送执行页   |
| QUEST_RESULT | 任务结果 | 出征者全部提交     | /game        | 解密计算成败，更新进度条 |
| ASSASSINATION | 刺杀   | 好人3次任务胜     | /game        | 仅刺客可见刺杀按钮    |
| FINISHED      | 终局结算 | 刺杀完成或坏人3任务胜 | /result      | 揭示身份，生成战绩卡片  |

> 阶段推进由后端 `GameStateMachine` 统一驱动，前端仅订阅 `PhaseChangedEvent` 并路由到对应子页面。

阶段流转图：

```mermaid
stateDiagram-v2
  [*] --> PREPARING : startGame
  PREPARING --> PROPOSING : allRoleConfirmed
  PROPOSING --> VOTING : proposalCreated
  VOTING --> VOTE_RESULT : allVoteReceived
  VOTE_RESULT --> EXECUTING : proposalApproved
  VOTE_RESULT --> PROPOSING : proposalRejected
  EXECUTING --> QUEST_RESULT : allExecutionsReceived
  QUEST_RESULT --> ASSASSINATION : goodQuestWins==3
  QUEST_RESULT --> PROPOSING : questContinue
  ASSASSINATION --> FINISHED : assassinationDone
  QUEST_RESULT --> FINISHED : evilQuestWins==3
  FINISHED --> [*]
```

## 5. 设计约束与约定

1. **可见性规则**由 `Role.getVisiblePlayers()` 统一封装，禁止在 UI 层硬编码角色列表。
2. **所有写操作**（投票、任务结果、刺杀）必须走数据库事务，防止并发重复提交。
3. **WebSocket 消息**按 `games/{gameId}` 分区，前端订阅 `/topic/games/{gameId}` 公共频道与 `/user/{userId}/games/{gameId}` 私有频道。
4. **roomCode** 仅用于外部输入/分享，内部一律使用 `roomId` 与 `gameId` 主键，避免主键遍历攻击。
5. **角色揭示页**数据一次性推送后前端本地保存，不再向后端请求，减少泄露面。