# API设计文档

## 1. 路由约定

- `/home`：首页
- `/rooms`：房间页
- `/games/{game_id}`：游戏页面（等待开始）
- `/games/{game_id}/role-reveal`：角色揭示页
- `/games/{game_id}/playing`：游戏进行页
- `/games/{game_id}/result`：结果页

访问校验：`{game_id}` 无效或无权限时，重定向 `/rooms` 或 `/home`。

## 2. 状态机摘要

- `LOBBY → ROOM_WAITING → ROLE_VIEWING → PLAYING → (ASSASSINATION) → RESULT`
- 触发方：房主（开始）、队长（提案）、全员（投票）、任务队员（执行）、邪恶阵营（刺杀）

## 3. 数据对象最小集

- Player：`id`、`name`、`role`(仅本人可见)
- Room：`code`、`ownerId`、`players[]`
- Game：`id`、`roomCode`、`state`、`round`、`leaderId`

## 4. API 合约最小集

- `POST /rooms`：创建房间 → `{code}`
- `POST /rooms/{code}/join`：加入房间
- `POST /games/{game_id}/start`：房主开始游戏 → 状态 `ROLE_VIEWING`
- `POST /games/{game_id}/proposal`：队长提交队伍
- `POST /games/{game_id}/vote`：全员投票
- `POST /games/{game_id}/mission`：队员执行（成功/失败）
- `POST /games/{game_id}/assassinate`：刺杀阶段操作

备注：以上为最小必需接口，返回统一使用 `{ok:boolean, data?, error?}`。

## 5. 用户认证API

### 用户注册

| 名称   | 请求方法 | 路径                   | 说明    |
| ---- | ---- | -------------------- | ----- |
| 用户注册 | POST | `/api/auth/register` | 新用户注册 |

请求体（Content-Type: application/json）：

```json
{
  "username": "merlin",
  "email": "merlin@avalon.com",
  "password": "sword123"
}
```

请求参数：

| 参数名      | 参数类型   | 是否必需 | 描述         |
| -------- | ------ | ---- | ---------- |
| username | string | 是    | 用户名，3-20字符 |
| email    | string | 是    | 邮箱地址       |
| password | string | 是    | 密码，6-20字符  |

响应体（201 Created）：

```json
{
  "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "username": "merlin",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### 用户登录

| 名称   | 请求方法 | 路径                | 说明   |
| ---- | ---- | ----------------- | ---- |
| 用户登录 | POST | `/api/auth/login` | 用户登录 |

请求体（Content-Type: application/json）：

```json
{
  "username": "merlin",
  "password": "sword123"
}
```

请求参数：

| 参数名      | 参数类型   | 是否必需 | 描述     |
| -------- | ------ | ---- | ------ |
| username | string | 是    | 用户名或邮箱 |
| password | string | 是    | 密码     |

响应体（200 OK）：

```json
{
  "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "username": "merlin",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

## 6. 房间管理API

### 创建房间

| 名称   | 请求方法 | 路径           | 说明     |
| ---- | ---- | ------------ | ------ |
| 创建房间 | POST | `/api/rooms` | 房主创建房间 |

请求体（Content-Type: application/json）：

```json
{
  "maxPlayers": 7,
  "roleConfig": {
    "merlin": true,
    "percival": true,
    "morgana": true,
    "assassin": true
  }
}
```

请求参数：

| 参数名        | 参数类型    | 是否必需 | 描述         |
| ---------- | ------- | ---- | ---------- |
| maxPlayers | integer | 是    | 最大玩家数，5-10 |
| roleConfig | object  | 否    | 角色配置对象     |

响应体（201 Created）：

```json
{
  "roomId": "c5d6e7f8-1234-5678-9abc-def123456789",
  "roomCode": "A3B9C1",
  "hostId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "maxPlayers": 7,
  "roleConfig": { "merlin": true, "percival": true, "morgana": true, "assassin": true },
  "createdAt": "2025-11-17T12:34:56Z"
}
```

### 加入房间

| 名称       | 请求方法 | 路径                               | 说明               |
| -------- | ---- | -------------------------------- | ---------------- |
| 加入房间     | POST | `/api/rooms/{roomId}/join`        | 通过房间ID加入房间       |
| 加入房间     | POST | `/api/rooms/join`                 | 通过房间代码加入房间      |
| 加入房间     | POST | `/api/room-players`               | RESTful风格创建房间玩家关系 |

请求体（Content-Type: application/json）：

```json
{
  "roomCode": "A3B9C1"
}
```

请求参数：

| 参数名      | 参数类型   | 是否必需 | 描述     |
| -------- | ------ | ---- | ------ |
| roomCode | string | 是    | 房间代码   |

响应体（200 OK）：

```json
{
  "roomId": "c5d6e7f8-1234-5678-9abc-def123456789",
  "playerId": "f9e8d7c6-5432-1098-7654-321098765432",
  "seatIdx": 3
}
```

### 获取房间信息

| 名称   | 请求方法 | 路径                               | 说明               |
| ---- | ---- | -------------------------------- | ---------------- |
| 房间信息 | GET  | `/api/rooms?roomCode={roomCode}` | 根据房间代码获取房间详情   |
| 房间信息 | GET  | `/api/rooms/{roomId}`            | 根据房间ID获取房间详情    |

请求参数：`roomCode`（查询参数，房间代码）

响应体（200 OK）：

```json
{
  "roomId": "c5d6e7f8-1234-5678-9abc-def123456789",
  "roomCode": "A3B9C1",
  "hostId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "hostName": "merlin",
  "maxPlayers": 7,
  "players": [
    { "playerId": "xxx", "nickname": "merlin", "seatIdx": 0, "isReady": true },
    { "playerId": "yyy", "nickname": "morgana", "seatIdx": 1, "isReady": false }
  ],
  "status": "WAITING",
  "createdAt": "2025-11-17T12:34:56Z"
}
```

### 获取房间玩家列表

| 名称       | 请求方法 | 路径                                          | 说明                 |
| -------- | ---- | ------------------------------------------- | ------------------ |
| 房间玩家列表 | GET  | `/api/rooms/players?roomCode={roomCode}`    | 根据房间代码获取房间玩家列表   |
| 房间玩家列表 | GET  | `/api/rooms/{roomId}/players`               | 根据房间ID获取房间玩家列表    |

请求参数：`roomCode`（查询参数，房间代码）

响应体（200 OK）：

```json
{
  "roomCode": "A3B9C1",
  "players": [
    { 
      "playerId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "nickname": "merlin",
      "role": "MERLIN",
      "alignment": "GOOD",
      "isHost": true,
      "seatNumber": 1,
      "isActive": true
    },
    { 
      "playerId": "f9e8d7c6-5432-1098-7654-321098765432",
      "nickname": "morgana",
      "role": "MORGANA",
      "alignment": "EVIL",
      "isHost": false,
      "seatNumber": 2,
      "isActive": true
    }
  ]
}
```

### 离开房间

| 名称     | 请求方法  | 路径                                 | 说明               |
| ------ | ----- | ---------------------------------- | ---------------- |
| 离开房间   | DELETE | `/api/rooms/{roomId}`              | 通过房间ID离开房间      |
| 离开房间   | DELETE | `/api/rooms/leave?roomCode={roomCode}` | 通过房间代码离开房间     |
| 离开房间   | DELETE | `/api/room-players/{roomPlayerId}` | RESTful风格删除房间玩家关系 |

请求参数：
- `roomCode`（查询参数，房间代码）
- `roomPlayerId`（路径参数，房间玩家ID）

响应体（200 OK）：

```json
{
  "message": "离开房间成功"
}
```

## 7. 游戏资源API

| 名称       | 请求方法 | 路径                                                   | 说明           |
| -------- | ---- | ---------------------------------------------------- | ------------ |
| 房主开始游戏   | POST | `/api/games/{gameId}/start`                          | 房主开始一局游戏     |
| 队长提议出征成员 | POST | `/api/games/{gameId}/quests/{questNumber}/proposals` | 当前队长提交本轮出征名单 |
| 玩家投票     | POST | `/api/games/{gameId}/proposals/{proposalId}/votes`   | 全体玩家对提案投票    |
| 查看投票情况   | GET  | `/api/games/{gameId}/proposals/{proposalId}/votes`   | 查询当前提案投票汇总   |
| 出征者提交任务  | POST | `/api/games/{gameId}/quests/{questNumber}/execution` | 出征成员匿名提交任务结果 |
| 查看任务结果   | GET  | `/api/games/{gameId}/quests/{questNumber}/result`    | 获取本轮任务最终成败   |
| 刺客查看可选目标 | GET  | `/api/games/{gameId}/assassination/targets`          | 刺客查看可刺杀玩家列表  |
| 刺客执行刺杀   | POST | `/api/games/{gameId}/assassination`                  | 刺客选择目标完成刺杀   |
| 查看角色信息   | GET  | `/api/games/{gameId}/role-info`                      | 玩家查看自己的角色信息  |

请求/响应概览：

| 名称       | 最小角色 | 请求体                                    | 响应体                      |
| -------- | ---- | -------------------------------------- | ------------------------ |
| 房主开始游戏   | 房主   | `{}`                                   | `GameStartedDTO`         |
| 队长提议出征成员 | 当前队长 | `ProposedTeamDTO {members: UUID[]}`    | `TeamProposedDTO`        |
| 玩家投票     | 全体玩家 | `VoteDTO {approve: boolean}`           | `VoteReceivedDTO`        |
| 查看投票情况   | 全体玩家 | —                                      | `VoteSummaryDTO`         |
| 出征者提交任务  | 出征成员 | `QuestExecutionDTO {success: boolean}` | `QuestExecutedDTO`       |
| 查看任务结果   | 全体玩家 | —                                      | `QuestResultDTO`         |
| 刺客查看可选目标 | 刺客   | —                                      | `List<PlayerDTO>`        |
| 刺客执行刺杀   | 刺客   | `AssassinationDTO {targetId: UUID}`    | `AssassinationResultDTO` |
| 查看角色信息   | 全体玩家 | —                                      | `RoleInfoResponse`       |

权限校验规则（Spring Security 伪代码）：

```java
@PreAuthorize("isHost(#gameId)")          // 仅房主
@PreAuthorize("isCurrentLeader(#gameId)") // 仅当前队长
@PreAuthorize("isQuestMember(#gameId)")   // 仅本轮出征者
@PreAuthorize("hasRole(#gameId, 'ASSASSIN')") // 仅刺客
@PreAuthorize("isPlayer(#gameId)")      // 任意局内玩家
```

## 8. RESTful设计原则说明

本系统API设计遵循RESTful架构原则，特别体现在房间玩家关系的管理上：

### 资源定义

1. **Room（房间）**：代表游戏房间本身，是持久化的资源
2. **RoomPlayer（房间玩家关系）**：代表玩家与房间之间的关系，是一个独立的资源

### 状态转移

加入房间和离开房间操作被设计为RoomPlayer资源的状态转移：

- **加入房间**：创建或激活RoomPlayer资源
  - `POST /api/room-players` 创建新的房间玩家关系或重新激活已存在的关系
  
- **离开房间**：更新RoomPlayer资源状态
  - `DELETE /api/room-players/{roomPlayerId}` 将玩家标记为非活跃状态

这种设计更符合RESTful的资源状态转移理念，将房间和玩家关系作为独立的资源进行管理，使API语义更加清晰。