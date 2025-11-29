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