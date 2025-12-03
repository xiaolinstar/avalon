## Avalon 项目接口测试用例

### **1. 注册功能测试 (Auth Module)**

#### **REG-TC-001: 正常注册**
- **测试目的**: 验证用户可以使用有效的、唯一的凭据成功注册。
- **前置条件**: 数据库中不存在用户 `testuser1` 或邮箱 `test1@example.com`。
- **请求方法/URL**: `POST /api/auth/register`
- **请求参数**:
  ```json
  {
    "username": "testuser1",
    "email": "test1@example.com",
    "password": "password123"
  }
  ```
- **预期响应**:
  - `Status Code: 200 OK`
  - `Body`:
    ```json
    {
      "success": true,
      "message": "用户注册成功",
      "data": {
        "userId": "...",
        "username": "testuser1",
        "token": "..."
      }
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `true`。
  2. 响应体中 `data.username` 与请求一致。
  3. 响应体中 `data.token` 不为空。
  4. **数据库验证**: `users` 表中存在一条记录，其 `username` 为 `testuser1`，`email` 为 `test1@example.com`，且密码字段已被加密。
- **后置清理**: 从数据库中删除用户 `testuser1`。

#### **REG-TC-002: 重复用户名注册**
- **测试目的**: 验证系统不允许使用已存在的用户名进行注册。
- **前置条件**: 数据库中已存在用户 `testuser1`。
- **请求方法/URL**: `POST /api/auth/register`
- **请求参数**:
  ```json
  {
    "username": "testuser1",
    "email": "new_email@example.com",
    "password": "password123"
  }
  ```
- **预期响应**:
  - `Status Code: 400 Bad Request`
  - `Body`:
    ```json
    {
      "success": false,
      "message": "用户名已存在",
      "data": null
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `false`。
  2. 响应体中 `message` 包含用户名已存在的提示。
  3. **数据库验证**: `users` 表中没有新增记录。
- **后置清理**: 无。

#### **REG-TC-003: 无效邮箱格式注册**
- **测试目的**: 验证后端对邮箱格式的校验是否生效。
- **前置条件**: 无。
- **请求方法/URL**: `POST /api/auth/register`
- **请求参数**:
  ```json
  {
    "username": "testuser2",
    "email": "invalid-email",
    "password": "password123"
  }
  ```
- **预期响应**:
  - `Status Code: 400 Bad Request`
  - `Body`:
    ```json
    {
      "success": false,
      "message": "邮箱格式无效", // 或类似的校验错误信息
      "data": null
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `false`。
  2. 响应体中 `message` 包含邮箱格式错误的提示。
- **后置清理**: 无。

---

### **2. 登录功能测试 (Auth Module)**

#### **LOG-TC-001: 正常登录**
- **测试目的**: 验证已注册用户可以使用正确的凭据成功登录并获取 Token。
- **前置条件**: 数据库中存在用户 `testuser1`，密码为 `password123`。
- **请求方法/URL**: `POST /api/auth/login`
- **请求参数**:
  ```json
  {
    "username": "testuser1",
    "password": "password123"
  }
  ```
- **预期响应**:
  - `Status Code: 200 OK`
  - `Body`:
    ```json
    {
      "success": true,
      "message": "登录成功",
      "data": {
        "userId": "...",
        "username": "testuser1",
        "token": "..." // JWT Token
      }
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `true`。
  2. 响应体中 `data.token` 是一个有效的、非空的字符串。
- **后置清理**: 无。

#### **LOG-TC-002: 错误密码登录**
- **测试目的**: 验证使用错误密码登录时系统会拒绝访问。
- **前置条件**: 数据库中存在用户 `testuser1`。
- **请求方法/URL**: `POST /api/auth/login`
- **请求参数**:
  ```json
  {
    "username": "testuser1",
    "password": "wrongpassword"
  }
  ```
- **预期响应**:
  - `Status Code: 400 Bad Request` 或 `401 Unauthorized`
  - `Body`:
    ```json
    {
      "success": false,
      "message": "用户名或密码错误",
      "data": null
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `false`。
  2. 响应体中 `message` 提示凭据错误。
- **后置清理**: 无。

---

### **3. 房间管理测试 (Room Module)**

#### **ROOM-CREATE-TC-001: 认证用户创建房间**
- **测试目的**: 验证已登录用户可以成功创建一个新房间。
- **前置条件**: 用户 `testuser1` 已登录，获得有效 Token。
- **请求方法/URL**: `POST /api/rooms`
- **请求头**: `Authorization: Bearer <valid_token>`
- **请求参数**:
  ```json
  {
    "maxPlayers": 8
  }
  ```
- **预期响应**:
  - `Status Code: 200 OK`
  - `Body`:
    ```json
    {
      "success": true,
      "message": "房间创建成功",
      "data": {
        "roomId": "...",
        "roomCode": "...", // 6位大写字母+数字
        "maxPlayers": 8,
        "status": "waiting",
        "creatorName": "testuser1",
        "currentPlayers": 1
      }
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `true`。
  2. `data.roomCode` 格式正确。
  3. `data.creatorName` 为当前用户名。
  4. `data.currentPlayers` 为 1。
  5. **数据库验证**: `rooms` 表和 `room_players` 表中已创建相应记录。
  6. **WebSocket 验证**: `/topic/room/{roomId}` 主题上不应广播任何事件消息（创建房间时不发送WebSocket消息）。
- **后置清理**: 删除创建的房间及关联的玩家记录。

#### **ROOM-CREATE-TC-002: 无效令牌创建房间**
- **测试目的**: 验证使用无效令牌时无法创建房间。
- **前置条件**: 无。
- **请求方法/URL**: `POST /api/rooms`
- **请求头**: `Authorization: Bearer invalid_token`
- **请求参数**:
  ```json
  {
    "maxPlayers": 8
  }
  ```
- **预期响应**:
  - `Status Code: 400 Bad Request`
  - `Body`:
    ```json
    {
      "success": false,
      "message": "...", // 错误信息
      "data": null
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `false`。
- **后置清理**: 无。

#### **ROOM-CREATE-TC-003: 无效玩家数创建房间**
- **测试目的**: 验证使用无效玩家数时无法创建房间。
- **前置条件**: 用户已登录，获得有效 Token。
- **请求方法/URL**: `POST /api/rooms`
- **请求头**: `Authorization: Bearer <valid_token>`
- **请求参数**:
  ```json
  {
    "maxPlayers": 3 // 或 15，超出有效范围
  }
  ```
- **预期响应**:
  - `Status Code: 400 Bad Request`
  - `Body`:
    ```json
    {
      "success": false,
      "message": "...", // 错误信息
      "data": null
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `false`。
- **后置清理**: 无。

#### **ROOM-JOIN-TC-001: 正常加入房间**
- **测试目的**: 验证用户可以成功加入一个未满员的、存在的房间。
- **前置条件**:
  1. 用户 `host` 已创建房间，`roomCode` 为 `TEST01`，`maxPlayers` 为 5。
  2. 用户 `player2` 已登录，获得有效 Token。
  3. 房间 `TEST01` 当前人数小于 5。
- **请求方法/URL**: `POST /api/rooms/TEST01/join`
- **请求头**: `Authorization: Bearer <player2_token>`
- **请求参数**: (无)
- **预期响应**:
  - `Status Code: 200 OK`
  - `Body`:
    ```json
    {
      "success": true,
      "message": "加入房间成功",
      "data": {
        "roomId": "...",
        "roomCode": "TEST01",
        // ... 其他房间信息
        "currentPlayers": 2 // 或更高
      }
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `true`。
  2. **数据库验证**: `room_players` 表中为房间 `TEST01` 增加了一条 `player2` 的记录。
  3. **WebSocket 验证**: `/topic/room/{roomId}` 主题上应广播一条 `PLAYER_JOINED` 事件消息。
- **后置清理**: 从房间中移除 `player2`。

#### **ROOM-JOIN-TC-002: 加入已满员的房间**
- **测试目的**: 验证当房间人数已达上限时，系统会拒绝新的加入请求。
- **前置条件**:
  1. 房间 `TEST02` 已存在，`maxPlayers` 为 5，当前人数也为 5。
  2. 用户 `player6` 已登录，获得有效 Token。
- **请求方法/URL**: `POST /api/rooms/TEST02/join`
- **请求头**: `Authorization: Bearer <player6_token>`
- **请求参数**: (无)
- **预期响应**:
  - `Status Code: 400 Bad Request`
  - `Body`:
    ```json
    {
      "success": false,
      "message": "房间已满",
      "data": null
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `false`。
  2. 响应体中 `message` 提示房间已满。
  3. **WebSocket 验证**: `/topic/room/{roomId}` 主题上应广播一条 `JOIN_REJECTED` 事件消息。
- **后置清理**: 无。

#### **ROOM-JOIN-TC-003: 加入不存在的房间**
- **测试目的**: 验证当用户尝试加入不存在的房间时，系统会返回错误。
- **前置条件**: 用户 `testuser1` 已登录，获得有效 Token。
- **请求方法/URL**: `POST /api/rooms/NONEXIST/join`
- **请求头**: `Authorization: Bearer <valid_token>`
- **请求参数**: (无)
- **预期响应**:
  - `Status Code: 400 Bad Request`
  - `Body`:
    ```json
    {
      "success": false,
      "message": "房间不存在",
      "data": null
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `false`。
  2. 响应体中 `message` 提示房间不存在。
  3. **WebSocket 验证**: `/topic/room/{roomId}` 主题上应广播一条 `JOIN_REJECTED` 事件消息。
- **后置清理**: 无。

#### **ROOM-LEAVE-TC-001: 房主离开房间**
- **测试目的**: 验证房主可以成功离开自己创建的房间。
- **前置条件**:
  1. 用户 `host` 已创建房间 `TEST03`。
  2. 用户 `player2` 已加入房间。
  3. 用户 `host` 已登录，获得有效 Token。
- **请求方法/URL**: `DELETE /api/rooms/TEST03/leave`
- **请求头**: `Authorization: Bearer <host_token>`
- **请求参数**: (无)
- **预期响应**:
  - `Status Code: 200 OK`
  - `Body`:
    ```json
    {
      "success": true,
      "message": "离开房间成功",
      "data": null
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `true`。
  2. **数据库验证**: `room_players` 表中房主记录的 `isActive` 字段变为 `false`。
  3. **WebSocket 验证**: `/topic/room/{roomId}` 主题上应广播一条 `PLAYER_LEFT` 事件消息。
- **后置清理**: 无。

#### **ROOM-LEAVE-TC-002: 普通玩家离开房间**
- **测试目的**: 验证普通玩家可以成功离开已加入的房间。
- **前置条件**:
  1. 用户 `host` 已创建房间 `TEST04`。
  2. 用户 `player2` 已加入房间。
  3. 用户 `player2` 已登录，获得有效 Token。
- **请求方法/URL**: `DELETE /api/rooms/TEST04/leave`
- **请求头**: `Authorization: Bearer <player2_token>`
- **请求参数**: (无)
- **预期响应**:
  - `Status Code: 200 OK`
  - `Body`:
    ```json
    {
      "success": true,
      "message": "离开房间成功",
      "data": null
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `true`。
  2. **数据库验证**: `room_players` 表中玩家记录的 `isActive` 字段变为 `false`。
  3. **WebSocket 验证**: `/topic/room/{roomId}` 主题上应广播一条 `PLAYER_LEFT` 事件消息。
- **后置清理**: 无。

#### **ROOM-GET-TC-001: 通过房间代码获取房间信息**
- **测试目的**: 验证可以通过房间代码正确获取房间信息。
- **前置条件**: 房间 `TEST05` 已存在。
- **请求方法/URL**: `GET /api/rooms/TEST05`
- **请求参数**: (无)
- **预期响应**:
  - `Status Code: 200 OK`
  - `Body`:
    ```json
    {
      "success": true,
      "message": "获取房间信息成功",
      "data": {
        "roomId": "...",
        "roomCode": "TEST05",
        "maxPlayers": 5,
        "status": "waiting",
        "creatorName": "host",
        "currentPlayers": 1
      }
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `true`。
  2. 返回的房间信息与数据库中存储的信息一致。
- **后置清理**: 无。

#### **ROOM-GET-TC-002: 通过房间ID获取房间信息**
- **测试目的**: 验证可以通过房间ID正确获取房间信息。
- **前置条件**: 房间 `TEST06` 已存在。
- **请求方法/URL**: `GET /api/rooms/id/{roomId}`
- **请求参数**: (无)
- **预期响应**:
  - `Status Code: 200 OK`
  - `Body`:
    ```json
    {
      "success": true,
      "message": "获取房间信息成功",
      "data": {
        "roomId": "{roomId}",
        "roomCode": "TEST06",
        "maxPlayers": 5,
        "status": "waiting",
        "creatorName": "host",
        "currentPlayers": 1
      }
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `true`。
  2. 返回的房间信息与数据库中存储的信息一致。
- **后置清理**: 无。

#### **ROOM-GET-TC-003: 获取房间玩家列表**
- **测试目的**: 验证可以正确获取房间内的玩家列表。
- **前置条件**: 
  1. 房间 `TEST07` 已存在。
  2. 用户 `host` 和 `player2` 已加入房间。
- **请求方法/URL**: `GET /api/rooms/TEST07/players`
- **请求参数**: (无)
- **预期响应**:
  - `Status Code: 200 OK`
  - `Body`:
    ```json
    {
      "success": true,
      "message": "获取房间玩家列表成功",
      "data": {
        "roomCode": "TEST07",
        "players": [
          {
            "userId": "...",
            "username": "host",
            "role": "unknown",
            "alignment": "unknown",
            "isHost": true,
            "seatNumber": 1,
            "isActive": true
          },
          {
            "userId": "...",
            "username": "player2",
            "role": "unknown",
            "alignment": "unknown",
            "isHost": false,
            "seatNumber": 2,
            "isActive": true
          }
        ]
      }
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `true`。
  2. 返回的玩家列表与房间内的实际玩家一致。
- **后置清理**: 无。

#### **ROOM-GET-TC-004: 通过无效房间代码获取房间信息**
- **测试目的**: 验证通过无效房间代码无法获取房间信息。
- **前置条件**: 无。
- **请求方法/URL**: `GET /api/rooms/INVALID`
- **请求参数**: (无)
- **预期响应**:
  - `Status Code: 400 Bad Request`
  - `Body`:
    ```json
    {
      "success": false,
      "message": "...", // 错误信息
      "data": null
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `false`。
- **后置清理**: 无。

#### **ROOM-GET-TC-005: 通过无效房间ID获取房间信息**
- **测试目的**: 验证通过无效房间ID无法获取房间信息。
- **前置条件**: 无。
- **请求方法/URL**: `GET /api/rooms/id/invalid-uuid`
- **请求参数**: (无)
- **预期响应**:
  - `Status Code: 400 Bad Request`
  - `Body`:
    ```json
    {
      "success": false,
      "message": "房间ID格式错误",
      "data": null
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `false`。
  2. 响应体中 `message` 提示房间ID格式错误。
- **后置清理**: 无。

#### **ROOM-SECURITY-TC-001: 未授权访问受保护端点**
- **测试目的**: 验证未授权用户无法访问受保护的端点。
- **前置条件**: 无。
- **请求方法/URL**: `POST /api/rooms`
- **请求参数**:
  ```json
  {
    "maxPlayers": 8
  }
  ```
- **预期响应**:
  - `Status Code: 400 Bad Request`
  - `Body`:
    ```json
    {
      "success": false,
      "message": "...", // 错误信息
      "data": null
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `false`。
- **后置清理**: 无。

---

### **4. 游戏流程测试 (Game Module)**

#### **GAME-START-TC-001: 房主在满足条件时开始游戏**
- **测试目的**: 验证房主可以在房间人数达到最低要求（例如5人）时成功开始游戏。
- **前置条件**:
  1. 用户 `host` 是房间 `TEST03` 的房主，已登录。
  2. 房间 `TEST03` 中已有 5 名玩家。
  3. 游戏尚未开始，房间状态为 `waiting`。
- **请求方法/URL**: `POST /api/games/{roomId}/start`
- **请求头**: `Authorization: Bearer <host_token>`
- **请求参数**: (无)
- **预期响应**:
  - `Status Code: 200 OK`
  - `Body`:
    ```json
    {
      "success": true,
      "message": "游戏开始成功",
      "data": "..." // gameId 或其他成功标识
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `true`。
  2. **数据库验证**:
     - `rooms` 表中对应房间的 `status` 变为 `playing`。
     - `games` 表中创建了一条新游戏记录。
     - `game_players` 表中为所有玩家分配了角色和阵营。
  3. **WebSocket 验证**: `/topic/game/{gameId}` 或相关主题上应广播游戏开始的状态更新消息。
- **后置清理**: 结束或重置游戏状态。

#### **GAME-START-TC-002: 人数不足时开始游戏**
- **测试目的**: 验证当房间人数未达到最低要求时，无法开始游戏。
- **前置条件**:
  1. 用户 `host` 是房间 `TEST04` 的房主。
  2. 房间 `TEST04` 中只有 4 名玩家。
- **请求方法/URL**: `POST /api/games/{roomId}/start`
- **请求头**: `Authorization: Bearer <host_token>`
- **请求参数**: (无)
- **预期响应**:
  - `Status Code: 400 Bad Request`
  - `Body`:
    ```json
    {
      "success": false,
      "message": "玩家人数不足，无法开始游戏",
      "data": null
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `false`。
  2. 响应体中 `message` 提示人数不足。
- **后置清理**: 无。

#### **GAME-ROLE-INFO-TC-001: 成功获取角色信息**
- **测试目的**: 验证已加入游戏的玩家可以成功获取自己的角色信息。
- **前置条件**:
  1. 用户 `host` 是房间 `TEST05` 的房主，已登录。
  2. 房间 `TEST05` 中已有 5 名玩家。
  3. 游戏已开始，房间状态为 `playing`。
  4. 玩家已被分配角色。
- **请求方法/URL**: `GET /api/games/{gameId}/role-info`
- **请求头**: `Authorization: Bearer <host_token>`
- **请求参数**: (无)
- **预期响应**:
  - `Status Code: 200 OK`
  - `Body`:
    ```json
    {
      "success": true,
      "message": "操作成功",
      "data": {
        "gameId": "...",
        "role": "merlin",
        "roleName": "梅林",
        "alignment": "正义",
        "description": "你知道邪恶阵营的所有成员，除了莫德雷德",
        "visibilityInfo": {
          "evil": ["player1", "player2"]
        }
      }
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `true`。
  2. 返回的数据包含玩家的角色代码、名称、阵营和描述。
  3. 返回的数据包含根据角色规则计算的可见性信息。
- **后置清理**: 无。

#### **GAME-ROLE-INFO-TC-002: 非游戏玩家尝试获取角色信息**
- **测试目的**: 验证未加入游戏的用户无法获取角色信息。
- **前置条件**:
  1. 用户 `outsider` 已登录，获得有效 Token。
  2. 游戏 `GAME01` 已存在。
- **请求方法/URL**: `GET /api/games/{gameId}/role-info`
- **请求头**: `Authorization: Bearer <outsider_token>`
- **请求参数**: (无)
- **预期响应**:
  - `Status Code: 400 Bad Request`
  - `Body`:
    ```json
    {
      "success": false,
      "message": "玩家不在游戏中",
      "data": null
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `false`。
  2. 响应体中 `message` 提示玩家不在游戏中。
- **后置清理**: 无。

#### **GAME-START-FIRST-QUEST-TC-001: 成功开始第一个任务**
- **测试目的**: 验证房主可以在游戏处于ROLE_VIEWING状态时成功开始第一个任务。
- **前置条件**:
  1. 用户 `host` 是房间 `TEST08` 的房主，已登录。
  2. 房间 `TEST08` 中已有 5 名玩家。
  3. 游戏已开始，房间状态为 `playing`，游戏状态为 `ROLE_VIEWING`。
- **请求方法/URL**: `POST /api/games/{gameId}/start-first-quest`
- **请求头**: `Authorization: Bearer <host_token>`
- **请求参数**: (无)
- **预期响应**:
  - `Status Code: 200 OK`
  - `Body`:
    ```json
    {
      "success": true,
      "message": "第一个任务开始成功",
      "data": ""
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `true`。
  2. **数据库验证**:
     - `games` 表中对应游戏的状态变为 `PLAYING`。
     - `quests` 表中为当前游戏创建了5条任务记录。
     - 第一个任务的队长为座位号为1的玩家。
  3. **WebSocket 验证**: `/topic/game/{gameId}` 主题上应广播一条 `FIRST_QUEST_STARTED` 事件消息。
- **后置清理**: 结束或重置游戏状态。

#### **GAME-START-FIRST-QUEST-TC-002: 游戏状态不正确时开始第一个任务**
- **测试目的**: 验证当游戏状态不正确时，无法开始第一个任务。
- **前置条件**:
  1. 用户 `host` 是房间 `TEST09` 的房主，已登录。
  2. 房间 `TEST09` 中已有 5 名玩家。
  3. 游戏已开始，房间状态为 `playing`，游戏状态为 `PLAYING`（不是ROLE_VIEWING）。
- **请求方法/URL**: `POST /api/games/{gameId}/start-first-quest`
- **请求头**: `Authorization: Bearer <host_token>`
- **请求参数**: (无)
- **预期响应**:
  - `Status Code: 400 Bad Request`
  - `Body`:
    ```json
    {
      "success": false,
      "message": "游戏状态不正确，无法开始第一个任务",
      "data": null
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `false`。
  2. 响应体中 `message` 提示游戏状态不正确。
- **后置清理**: 无。

#### **GAME-START-FIRST-QUEST-TC-003: 非房主尝试开始第一个任务**
- **测试目的**: 验证非房主玩家无法开始第一个任务。
- **前置条件**:
  1. 用户 `player2` 是房间 `TEST10` 的普通玩家，已登录。
  2. 房间 `TEST10` 中已有 5 名玩家。
  3. 游戏已开始，房间状态为 `playing`，游戏状态为 `ROLE_VIEWING`。
- **请求方法/URL**: `POST /api/games/{gameId}/start-first-quest`
- **请求头**: `Authorization: Bearer <player2_token>`
- **请求参数**: (无)
- **预期响应**:
  - `Status Code: 400 Bad Request`
  - `Body`:
    ```json
    {
      "success": false,
      "message": "只有房主可以开始第一个任务",
      "data": null
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `false`。
  2. 响应体中 `message` 提示只有房主可以开始第一个任务。
- **后置清理**: 无。

#### **GAME-START-FIRST-QUEST-TC-004: 使用无效游戏ID开始第一个任务**
- **测试目的**: 验证使用无效游戏ID无法开始第一个任务。
- **前置条件**: 用户 `testuser` 已登录，获得有效 Token。
- **请求方法/URL**: `POST /api/games/invalid-game-id/start-first-quest`
- **请求头**: `Authorization: Bearer <valid_token>`
- **请求参数**: (无)
- **预期响应**:
  - `Status Code: 400 Bad Request`
  - `Body`:
    ```json
    {
      "success": false,
      "message": "游戏不存在",
      "data": null
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `false`。
  2. 响应体中 `message` 提示游戏不存在。
- **后置清理**: 无。

#### **GAME-START-QUEST-TC-001: 使用统一接口成功开始第一个任务**
- **测试目的**: 验证可以通过统一接口成功开始第一个任务。
- **前置条件**:
  1. 用户 `host` 是房间 `TEST11` 的房主，已登录。
  2. 房间 `TEST11` 中已有 5 名玩家。
  3. 游戏已开始，房间状态为 `playing`，游戏状态为 `ROLE_VIEWING`。
- **请求方法/URL**: `POST /api/games/{gameId}/start-quest?isFirstQuest=true`
- **请求头**: `Authorization: Bearer <host_token>`
- **请求参数**: `isFirstQuest=true`
- **预期响应**:
  - `Status Code: 200 OK`
  - `Body`:
    ```json
    {
      "success": true,
      "message": "第一个任务开始成功",
      "data": ""
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `true`。
  2. **数据库验证**:
     - `games` 表中对应游戏的状态变为 `PLAYING`。
     - `quests` 表中为当前游戏创建了5条任务记录。
     - 第一个任务的队长为座位号为1的玩家。
  3. **WebSocket 验证**: `/topic/game/{gameId}` 主题上应广播一条 `FIRST_QUEST_STARTED` 事件消息。
- **后置清理**: 结束或重置游戏状态。

#### **GAME-START-QUEST-TC-002: 使用统一接口成功开始后续任务**
- **测试目的**: 验证可以通过统一接口成功开始后续任务。
- **前置条件**:
  1. 用户 `host` 是房间 `TEST12` 的房主，已登录。
  2. 房间 `TEST12` 中已有 5 名玩家。
  3. 游戏已开始，房间状态为 `playing`，游戏状态为 `PLAYING`。
  4. 当前处于第一轮任务结束后，准备开始第二轮任务。
- **请求方法/URL**: `POST /api/games/{gameId}/start-quest?isFirstQuest=false`
- **请求头**: `Authorization: Bearer <host_token>`
- **请求参数**: `isFirstQuest=false`
- **预期响应**:
  - `Status Code: 200 OK`
  - `Body`:
    ```json
    {
      "success": true,
      "message": "任务开始成功",
      "data": ""
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `true`。
  2. **数据库验证**:
     - `games` 表中游戏轮次增加1。
     - `quests` 表中为当前游戏创建了新的任务记录。
     - 新任务的队长根据轮询规则正确设置。
- **后置清理**: 结束或重置游戏状态。

---
  
### **5. 队伍提议和投票测试 (Team Proposal and Voting Module)**

#### **TEAM-PROPOSAL-TC-001: 队长成功提议队伍**
- **测试目的**: 验证队长可以成功为当前任务提议一个符合要求的队伍。
- **前置条件**:
  1. 用户 `leader` 是房间 `TEST13` 的房主，已登录。
  2. 房间 `TEST13` 中已有 5 名玩家。
  3. 游戏已开始，房间状态为 `playing`，游戏状态为 `PLAYING`。
  4. 当前处于第一轮任务的队伍组建阶段。
  5. 第一轮任务需要2名玩家参与。
- **请求方法/URL**: `POST /api/games/{gameId}/propose-team`
- **请求头**: `Authorization: Bearer <leader_token>`
- **请求参数**:
  ```json
  {
    "playerIds": ["uuid1", "uuid2"]
  }
  ```
- **预期响应**:
  - `Status Code: 200 OK`
  - `Body`:
    ```json
    {
      "success": true,
      "message": "队伍提议成功",
      "data": {
        // Quest对象信息
      }
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `true`。
  2. **数据库验证**:
     - `quests` 表中当前任务的 `status` 变为 `VOTING`。
     - `quests` 表中当前任务的 `proposedMembers` 包含指定的玩家。
  3. **WebSocket 验证**: `/topic/game/{gameId}` 主题上应广播一条 `TEAM_PROPOSED` 事件消息。
- **后置清理**: 结束或重置游戏状态。

#### **TEAM-PROPOSAL-TC-002: 非队长尝试提议队伍**
- **测试目的**: 验证非队长玩家无法提议队伍。
- **前置条件**:
  1. 用户 `player2` 是房间 `TEST14` 的普通玩家，已登录。
  2. 房间 `TEST14` 中已有 5 名玩家。
  3. 游戏已开始，房间状态为 `playing`，游戏状态为 `PLAYING`。
  4. 当前处于第一轮任务的队伍组建阶段。
  5. 用户 `player3` 是当前队长。
- **请求方法/URL**: `POST /api/games/{gameId}/propose-team`
- **请求头**: `Authorization: Bearer <player2_token>`
- **请求参数**:
  ```json
  {
    "playerIds": ["uuid1", "uuid2"]
  }
  ```
- **预期响应**:
  - `Status Code: 400 Bad Request`
  - `Body`:
    ```json
    {
      "success": false,
      "message": "不是当前队长",
      "data": null
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `false`。
  2. 响应体中 `message` 提示不是当前队长。
- **后置清理**: 无。

#### **TEAM-PROPOSAL-TC-003: 队伍成员数量不符合要求**
- **测试目的**: 验证当提议的队伍成员数量不符合当前任务要求时，系统会拒绝提议。
- **前置条件**:
  1. 用户 `leader` 是房间 `TEST15` 的房主，已登录。
  2. 房间 `TEST15` 中已有 5 名玩家。
  3. 游戏已开始，房间状态为 `playing`，游戏状态为 `PLAYING`。
  4. 当前处于第一轮任务的队伍组建阶段。
  5. 第一轮任务需要2名玩家参与。
- **请求方法/URL**: `POST /api/games/{gameId}/propose-team`
- **请求头**: `Authorization: Bearer <leader_token>`
- **请求参数**:
  ```json
  {
    "playerIds": ["uuid1", "uuid2", "uuid3"] // 3人队伍，但任务只需要2人
  }
  ```
- **预期响应**:
  - `Status Code: 400 Bad Request`
  - `Body`:
    ```json
    {
      "success": false,
      "message": "队伍人数不符合要求",
      "data": null
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `false`。
  2. 响应体中 `message` 提示队伍人数不符合要求。
- **后置清理**: 无。

#### **TEAM-VOTE-TC-001: 玩家成功投票**
- **测试目的**: 验证玩家可以成功对提议的队伍进行投票。
- **前置条件**:
  1. 用户 `player1` 是房间 `TEST16` 的玩家，已登录。
  2. 房间 `TEST16` 中已有 5 名玩家。
  3. 游戏已开始，房间状态为 `playing`，游戏状态为 `PLAYING`。
  4. 当前处于第一轮任务的投票阶段。
- **请求方法/URL**: `POST /api/games/{gameId}/vote`
- **请求头**: `Authorization: Bearer <player1_token>`
- **请求参数**:
  ```json
  {
    "voteType": "APPROVE" // 或 "REJECT"
  }
  ```
- **预期响应**:
  - `Status Code: 200 OK`
  - `Body`:
    ```json
    {
      "success": true,
      "message": "投票成功",
      "data": {
        // Vote对象信息
      }
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `true`。
  2. **数据库验证**:
     - `votes` 表中增加一条投票记录。
- **后置清理**: 无。

#### **TEAM-VOTE-TC-002: 玩家重复投票**
- **测试目的**: 验证玩家无法对同一任务重复投票。
- **前置条件**:
  1. 用户 `player1` 是房间 `TEST17` 的玩家，已登录。
  2. 房间 `TEST17` 中已有 5 名玩家。
  3. 游戏已开始，房间状态为 `playing`，游戏状态为 `PLAYING`。
  4. 当前处于第一轮任务的投票阶段。
  5. 用户 `player1` 已经投过票。
- **请求方法/URL**: `POST /api/games/{gameId}/vote`
- **请求头**: `Authorization: Bearer <player1_token>`
- **请求参数**:
  ```json
  {
    "voteType": "APPROVE"
  }
  ```
- **预期响应**:
  - `Status Code: 400 Bad Request`
  - `Body`:
    ```json
    {
      "success": false,
      "message": "已经投过票了",
      "data": null
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `false`。
  2. 响应体中 `message` 提示已经投过票了。
- **后置清理**: 无。

#### **TEAM-VOTE-TC-003: 投票通过**
- **测试目的**: 验证当投票通过时，系统正确更新任务状态。
- **前置条件**:
  1. 房间 `TEST18` 中已有 5 名玩家。
  2. 游戏已开始，房间状态为 `playing`，游戏状态为 `PLAYING`。
  3. 当前处于第一轮任务的投票阶段。
  4. 3名玩家投赞成票，2名玩家投反对票。
- **请求方法/URL**: `POST /api/games/{gameId}/process-votes`
- **请求参数**: 无
- **预期响应**:
  - `Status Code: 200 OK`
  - `Body`:
    ```json
    {
      "success": true,
      "message": "投票结果处理成功",
      "data": null
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `true`。
  2. **数据库验证**:
     - `quests` 表中当前任务的 `status` 变为 `EXECUTING`。
  3. **WebSocket 验证**: `/topic/game/{gameId}` 主题上应广播一条 `TEAM_APPROVED` 事件消息。
- **后置清理**: 结束或重置游戏状态。

#### **TEAM-VOTE-TC-004: 投票未通过**
- **测试目的**: 验证当投票未通过时，系统正确更新任务状态并更换队长。
- **前置条件**:
  1. 房间 `TEST19` 中已有 5 名玩家。
  2. 游戏已开始，房间状态为 `playing`，游戏状态为 `PLAYING`。
  3. 当前处于第一轮任务的投票阶段。
  4. 2名玩家投赞成票，3名玩家投反对票。
- **请求方法/URL**: `POST /api/games/{gameId}/process-votes`
- **请求参数**: 无
- **预期响应**:
  - `Status Code: 200 OK`
  - `Body`:
    ```json
    {
      "success": true,
      "message": "投票结果处理成功",
      "data": null
    }
    ```
- **实际响应验证点**:
  1. 响应体中 `success` 为 `true`。
  2. **数据库验证**:
     - `quests` 表中当前任务的 `status` 变为 `PROPOSING`。
     - `quests` 表中当前任务的 `leader` 更换为下一位玩家。
  3. **WebSocket 验证**: `/topic/game/{gameId}` 主题上应广播一条 `TEAM_REJECTED` 事件消息。
- **后置清理**: 结束或重置游戏状态。
