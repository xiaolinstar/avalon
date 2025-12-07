### **7. 多局游戏运行测试 (Multiple Game Runs Test)**

#### MULTI-GAME-RUN-TC-001: 5人游戏完整流程测试

- **测试目的**: 验证5人游戏能够正常进行完整的游戏流程，包括所有任务轮次和胜利条件判定。
- **前置条件**:
  1. 房间中有5名玩家。
  2. 游戏已开始，房间状态为 `playing`。
  3. 所有玩家均已查看角色信息。
- **测试步骤**:
  1. **开始第一个任务**
     - **请求方法/URL**: `POST /api/games/{gameId}/quests?isFirstQuest=true`
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

  2. **每轮任务流程**（总共5轮）
     - **队长提议队伍**:
       - **请求方法/URL**: `POST /api/games/{gameId}/propose-team`
       - **请求头**: `Authorization: Bearer <leader_token>`
       - **请求参数**:
         ```json
         {
           "playerIds": ["uuid1", "uuid2"] // 根据轮次选择正确的玩家数（第1,3轮选2人，第2,4,5轮选3人）
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

     - **所有玩家投票**:
       - **请求方法/URL**: `POST /api/games/{gameId}/vote`
       - **请求头**: `Authorization: Bearer <player_token>`
       - **请求参数**:
         ```json
         {
           "voteType": "approve" // 根据策略决定投票类型
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

     - **队伍成员执行任务**:
       - **请求方法/URL**: `POST /api/games/{gameId}/quests/execute`
       - **请求头**: `Authorization: Bearer <player_token>` (仅参与任务的玩家)
       - **请求参数**:
         ```json
         {
           "success": true // 根据测试目标决定任务成败
         }
         ```
       - **预期响应**:
         - `Status Code: 200 OK`
         - `Body`:
           ```json
           {
             "success": true,
             "message": "任务执行成功",
             "data": {
               // QuestResult对象信息
             }
           }
           ```

  3. **重复任务流程直到游戏结束**

- **预期结果**:
  1. **数据库验证**:
     - `games` 表中游戏的 `status` 变为 `ENDED`。
     - `games` 表中游戏的 `winner` 根据任务执行情况正确设置。
  2. **WebSocket 验证**: `/topic/game/{gameId}` 主题上应广播相应的游戏结束消息。
- **后置清理**: 结束或重置游戏状态。

#### MULTI-GAME-RUN-TC-002: 6人游戏完整流程测试

- **测试目的**: 验证6人游戏能够正常进行完整的游戏流程，包括所有任务轮次和胜利条件判定。
- **前置条件**:
  1. 房间中有6名玩家。
  2. 游戏已开始，房间状态为 `playing`。
  3. 所有玩家均已查看角色信息。
- **测试步骤**:
  1. **开始第一个任务**
     - **请求方法/URL**: `POST /api/games/{gameId}/quests?isFirstQuest=true`
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

  2. **每轮任务流程**（总共5轮）
     - **队长提议队伍**:
       - **请求方法/URL**: `POST /api/games/{gameId}/propose-team`
       - **请求头**: `Authorization: Bearer <leader_token>`
       - **请求参数**:
         ```json
         {
           "playerIds": ["uuid1", "uuid2"] // 根据轮次选择正确的玩家数（第1,3,5轮选2人，第2,4轮选3人和4人）
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

     - **所有玩家投票**:
       - **请求方法/URL**: `POST /api/games/{gameId}/vote`
       - **请求头**: `Authorization: Bearer <player_token>`
       - **请求参数**:
         ```json
         {
           "voteType": "approve" // 根据策略决定投票类型
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

     - **队伍成员执行任务**:
       - **请求方法/URL**: `POST /api/games/{gameId}/quests/execute`
       - **请求头**: `Authorization: Bearer <player_token>` (仅参与任务的玩家)
       - **请求参数**:
         ```json
         {
           "success": true // 根据测试目标决定任务成败
         }
         ```
       - **预期响应**:
         - `Status Code: 200 OK`
         - `Body`:
           ```json
           {
             "success": true,
             "message": "任务执行成功",
             "data": {
               // QuestResult对象信息
             }
           }
           ```

  3. **重复任务流程直到游戏结束**

- **预期结果**:
  1. **数据库验证**:
     - `games` 表中游戏的 `status` 变为 `ENDED`。
     - `games` 表中游戏的 `winner` 根据任务执行情况正确设置。
  2. **WebSocket 验证**: `/topic/game/{gameId}` 主题上应广播相应的游戏结束消息。
- **后置清理**: 结束或重置游戏状态。

#### MULTI-GAME-RUN-TC-003: 7人游戏完整流程测试

- **测试目的**: 验证7人游戏能够正常进行完整的游戏流程，包括所有任务轮次和胜利条件判定。
- **前置条件**:
  1. 房间中有7名玩家。
  2. 游戏已开始，房间状态为 `playing`。
  3. 所有玩家均已查看角色信息。
- **测试步骤**:
  1. **开始第一个任务**
     - **请求方法/URL**: `POST /api/games/{gameId}/quests?isFirstQuest=true`
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

  2. **每轮任务流程**（总共5轮）
     - **队长提议队伍**:
       - **请求方法/URL**: `POST /api/games/{gameId}/propose-team`
       - **请求头**: `Authorization: Bearer <leader_token>`
       - **请求参数**:
         ```json
         {
           "playerIds": ["uuid1", "uuid2"] // 根据轮次选择正确的玩家数（第1轮选2人，第2,3轮选3人，第4,5轮选4人）
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

     - **所有玩家投票**:
       - **请求方法/URL**: `POST /api/games/{gameId}/vote`
       - **请求头**: `Authorization: Bearer <player_token>`
       - **请求参数**:
         ```json
         {
           "voteType": "approve" // 根据策略决定投票类型
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

     - **队伍成员执行任务**:
       - **请求方法/URL**: `POST /api/games/{gameId}/quests/execute`
       - **请求头**: `Authorization: Bearer <player_token>` (仅参与任务的玩家)
       - **请求参数**:
         ```json
         {
           "success": true // 根据测试目标决定任务成败
         }
         ```
       - **预期响应**:
         - `Status Code: 200 OK`
         - `Body`:
           ```json
           {
             "success": true,
             "message": "任务执行成功",
             "data": {
               // QuestResult对象信息
             }
           }
           ```

  3. **重复任务流程直到游戏结束**

- **预期结果**:
  1. **数据库验证**:
     - `games` 表中游戏的 `status` 变为 `ENDED`。
     - `games` 表中游戏的 `winner` 根据任务执行情况正确设置。
  2. **WebSocket 验证**: `/topic/game/{gameId}` 主题上应广播相应的游戏结束消息。
- **后置清理**: 结束或重置游戏状态。

#### MULTI-GAME-RUN-TC-004: 8人游戏完整流程测试

- **测试目的**: 验证8人游戏能够正常进行完整的游戏流程，包括所有任务轮次和胜利条件判定。
- **前置条件**:
  1. 房间中有8名玩家。
  2. 游戏已开始，房间状态为 `playing`。
  3. 所有玩家均已查看角色信息。
- **测试步骤**:
  1. **开始第一个任务**
     - **请求方法/URL**: `POST /api/games/{gameId}/quests?isFirstQuest=true`
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

  2. **每轮任务流程**（总共5轮）
     - **队长提议队伍**:
       - **请求方法/URL**: `POST /api/games/{gameId}/propose-team`
       - **请求头**: `Authorization: Bearer <leader_token>`
       - **请求参数**:
         ```json
         {
           "playerIds": ["uuid1", "uuid2"] // 根据轮次选择正确的玩家数（第1轮选3人，第2,3轮选4人，第4,5轮选5人）
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

     - **所有玩家投票**:
       - **请求方法/URL**: `POST /api/games/{gameId}/vote`
       - **请求头**: `Authorization: Bearer <player_token>`
       - **请求参数**:
         ```json
         {
           "voteType": "approve" // 根据策略决定投票类型
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

     - **队伍成员执行任务**:
       - **请求方法/URL**: `POST /api/games/{gameId}/quests/execute`
       - **请求头**: `Authorization: Bearer <player_token>` (仅参与任务的玩家)
       - **请求参数**:
         ```json
         {
           "success": true // 根据测试目标决定任务成败
         }
         ```
       - **预期响应**:
         - `Status Code: 200 OK`
         - `Body`:
           ```json
           {
             "success": true,
             "message": "任务执行成功",
             "data": {
               // QuestResult对象信息
             }
           }
           ```

  3. **重复任务流程直到游戏结束**

- **预期结果**:
  1. **数据库验证**:
     - `games` 表中游戏的 `status` 变为 `ENDED`。
     - `games` 表中游戏的 `winner` 根据任务执行情况正确设置。
  2. **WebSocket 验证**: `/topic/game/{gameId}` 主题上应广播相应的游戏结束消息。
- **后置清理**: 结束或重置游戏状态。

#### MULTI-GAME-RUN-TC-005: 9人游戏完整流程测试

- **测试目的**: 验证9人游戏能够正常进行完整的游戏流程，包括所有任务轮次和胜利条件判定。
- **前置条件**:
  1. 房间中有9名玩家。
  2. 游戏已开始，房间状态为 `playing`。
  3. 所有玩家均已查看角色信息。
- **测试步骤**:
  1. **开始第一个任务**
     - **请求方法/URL**: `POST /api/games/{gameId}/quests?isFirstQuest=true`
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

  2. **每轮任务流程**（总共5轮）
     - **队长提议队伍**:
       - **请求方法/URL**: `POST /api/games/{gameId}/propose-team`
       - **请求头**: `Authorization: Bearer <leader_token>`
       - **请求参数**:
         ```json
         {
           "playerIds": ["uuid1", "uuid2"] // 根据轮次选择正确的玩家数（第1轮选3人，第2,3轮选4人，第4,5轮选5人）
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

     - **所有玩家投票**:
       - **请求方法/URL**: `POST /api/games/{gameId}/vote`
       - **请求头**: `Authorization: Bearer <player_token>`
       - **请求参数**:
         ```json
         {
           "voteType": "approve" // 根据策略决定投票类型
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

     - **队伍成员执行任务**:
       - **请求方法/URL**: `POST /api/games/{gameId}/quests/execute`
       - **请求头**: `Authorization: Bearer <player_token>` (仅参与任务的玩家)
       - **请求参数**:
         ```json
         {
           "success": true // 根据测试目标决定任务成败
         }
         ```
       - **预期响应**:
         - `Status Code: 200 OK`
         - `Body`:
           ```json
           {
             "success": true,
             "message": "任务执行成功",
             "data": {
               // QuestResult对象信息
             }
           }
           ```

  3. **重复任务流程直到游戏结束**

- **预期结果**:
  1. **数据库验证**:
     - `games` 表中游戏的 `status` 变为 `ENDED`。
     - `games` 表中游戏的 `winner` 根据任务执行情况正确设置。
  2. **WebSocket 验证**: `/topic/game/{gameId}` 主题上应广播相应的游戏结束消息。
- **后置清理**: 结束或重置游戏状态。

#### MULTI-GAME-RUN-TC-006: 10人游戏完整流程测试

- **测试目的**: 验证10人游戏能够正常进行完整的游戏流程，包括所有任务轮次和胜利条件判定。
- **前置条件**:
  1. 房间中有10名玩家。
  2. 游戏已开始，房间状态为 `playing`。
  3. 所有玩家均已查看角色信息。
- **测试步骤**:
  1. **开始第一个任务**
     - **请求方法/URL**: `POST /api/games/{gameId}/quests?isFirstQuest=true`
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

  2. **每轮任务流程**（总共5轮）
     - **队长提议队伍**:
       - **请求方法/URL**: `POST /api/games/{gameId}/propose-team`
       - **请求头**: `Authorization: Bearer <leader_token>`
       - **请求参数**:
         ```json
         {
           "playerIds": ["uuid1", "uuid2"] // 根据轮次选择正确的玩家数（第1,2,3轮选3人，第4,5轮选4人和5人）
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

     - **所有玩家投票**:
       - **请求方法/URL**: `POST /api/games/{gameId}/vote`
       - **请求头**: `Authorization: Bearer <player_token>`
       - **请求参数**:
         ```json
         {
           "voteType": "approve" // 根据策略决定投票类型
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

     - **队伍成员执行任务**:
       - **请求方法/URL**: `POST /api/games/{gameId}/quests/execute`
       - **请求头**: `Authorization: Bearer <player_token>` (仅参与任务的玩家)
       - **请求参数**:
         ```json
         {
           "success": true // 根据测试目标决定任务成败
         }
         ```
       - **预期响应**:
         - `Status Code: 200 OK`
         - `Body`:
           ```json
           {
             "success": true,
             "message": "任务执行成功",
             "data": {
               // QuestResult对象信息
             }
           }
           ```

  3. **重复任务流程直到游戏结束**

- **预期结果**:
  1. **数据库验证**:
     - `games` 表中游戏的 `status` 变为 `ENDED`。
     - `games` 表中游戏的 `winner` 根据任务执行情况正确设置。
  2. **WebSocket 验证**: `/topic/game/{gameId}` 主题上应广播相应的游戏结束消息。
- **后置清理**: 结束或重置游戏状态。

#### MULTI-GAME-RUN-TC-007: 正义阵营获胜场景测试

- **测试目的**: 验证当正义阵营成功完成3个任务时，游戏正确结束并宣布正义阵营获胜。
- **前置条件**:
  1. 房间中有5-10名玩家。
  2. 游戏已开始，房间状态为 `playing`。
  3. 所有玩家均已查看角色信息。
- **测试步骤**:
  1. 进行游戏流程，确保正义阵营成功完成至少3个任务。
  2. 在适当的时候让邪恶阵营破坏任务，但不足以阻止正义阵营获胜。
  3. 对于7人及以上游戏，注意第4轮任务需要2张失败票才能破坏任务。
- **预期结果**:
  1. **数据库验证**:
     - `games` 表中游戏的 `status` 变为 `ENDED`。
     - `games` 表中游戏的 `winner` 为 `good`。
  2. **WebSocket 验证**: `/topic/game/{gameId}` 主题上应广播 `QUEST_COMPLETED` 事件消息，宣布正义阵营获胜。
- **后置清理**: 结束或重置游戏状态。

#### MULTI-GAME-RUN-TC-008: 邪恶阵营获胜场景测试

- **测试目的**: 验证当邪恶阵营成功破坏3个任务时，游戏正确结束并宣布邪恶阵营获胜。
- **前置条件**:
  1. 房间中有5-10名玩家。
  2. 游戏已开始，房间状态为 `playing`。
  3. 所有玩家均已查看角色信息。
- **测试步骤**:
  1. 进行游戏流程，确保邪恶阵营成功破坏至少3个任务。
  2. 在适当的时候让正义阵营成功完成任务，但不足以赢得游戏。
  3. 对于7人及以上游戏，注意第4轮任务需要2张失败票才能破坏任务。
- **预期结果**:
  1. **数据库验证**:
     - `games` 表中游戏的 `status` 变为 `ENDED`。
     - `games` 表中游戏的 `winner` 为 `evil`。
  2. **WebSocket 验证**: `/topic/game/{gameId}` 主题上应广播 `QUEST_COMPLETED` 事件消息，宣布邪恶阵营获胜。
- **后置清理**: 结束或重置游戏状态。

#### MULTI-GAME-RUN-TC-009: 投票失败导致队长轮换测试

- **测试目的**: 验证当队伍提议投票未通过时，队长正确轮换到下一个玩家。
- **前置条件**:
  1. 房间中有5-10名玩家。
  2. 游戏已开始，房间状态为 `playing`。
  3. 所有玩家均已查看角色信息。
- **测试步骤**:
  1. 队长提议队伍。
  2. 大部分玩家投反对票，使投票未通过。
  3. 验证下一个玩家成为新的队长。
- **预期结果**:
  1. **数据库验证**:
     - `quests` 表中当前任务的 `leader` 更新为下一个玩家。
     - `quests` 表中当前任务的 `status` 仍为 `PROPOSING`。
  2. **WebSocket 验证**: `/topic/game/{gameId}` 主题上应广播 `TEAM_REJECTED` 事件消息。
- **后置清理**: 结束或重置游戏状态。

#### MULTI-GAME-RUN-TC-010: 连续失败任务导致邪恶阵营获胜测试

- **测试目的**: 验证当连续失败任务达到限制时，游戏正确结束并宣布邪恶阵营获胜。
- **前置条件**:
  1. 房间中有5-10名玩家。
  2. 游戏已开始，房间状态为 `playing`。
  3. 所有玩家均已查看角色信息。
- **测试步骤**:
  1. 进行游戏流程，确保连续有3个任务失败。
  2. 对于7人及以上游戏，注意第4轮任务需要2张失败票才能破坏任务。
- **预期结果**:
  1. **数据库验证**:
     - `games` 表中游戏的 `status` 变为 `ENDED`。
     - `games` 表中游戏的 `winner` 为 `evil`。
  2. **WebSocket 验证**: `/topic/game/{gameId}` 主题上应广播 `QUEST_COMPLETED` 事件消息，宣布邪恶阵营获胜。
- **后置清理**: 结束或重置游戏状态。