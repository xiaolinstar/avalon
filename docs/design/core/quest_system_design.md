# 任务系统设计

## 1. 问题陈述

目前，阿瓦隆游戏实 hiện有一个单独的API端点用于启动第一个任务（`POST /api/games/{gameId}/start-first-quest`），这违反了对所有任务操作采用统一方法的原则。这在API设计中造成了不一致性，并使代码库更难维护。

## 2. 当前实现问题

1. **单独的API端点**：第一个任务通过专用端点启动，而不是使用通用的任务启动机制。
2. **不一致的逻辑**：启动第一个任务与后续任务使用不同的代码路径。
3. **维护开销**：拥有单独的逻辑增加了维护和测试任务系统的复杂性。

## 3. 设计目标

1. **统一任务启动**：实现一个单一、一致的方法来启动任何任务，无论是第一个还是任何后续任务。
2. **一致的API**：移除用于启动第一个任务的单独端点，使用统一的方法。
3. **可维护的代码**：减少代码重复，简化任务管理逻辑。

## 4. 提议的解决方案

### 4.1 统一任务启动方法

我们应该实现一个统一的 `startQuest` 方法，而不是使用单独的 `startFirstQuest` 方法，该方法可以根据当前游戏状态处理启动任何任务。

```java
@Transactional
public void startQuest(UUID gameId) {
    Game game = gameRepository.findById(gameId)
        .orElseThrow(() -> new RuntimeException("游戏不存在"));

    // 验证游戏状态 - 当处于ROLE_VIEWING或任务之间时可以启动任务
    if (!game.getStatus().equals(GameStatus.ROLE_VIEWING.getValue()) && 
        !game.getStatus().equals(GameStatus.PLAYING.getValue())) {
        throw new RuntimeException("游戏状态不正确，无法开始任务");
    }

    // 对于第一个任务，我们需要根据玩家数量创建所有任务
    if (game.getStatus().equals(GameStatus.ROLE_VIEWING.getValue())) {
        // 获取玩家数量
        List<GamePlayer> gamePlayers = gamePlayerRepository.findByGame(game);
        int playerCount = gamePlayers.size();

        // 为游戏创建所有任务
        createQuests(game, playerCount);

        // 设置第一个任务的队长
        Quest firstQuest = questRepository.findByGameOrderByRoundNumber(game).get(0);
        firstQuest.setLeader(gamePlayers.get(0).getUser());
        questRepository.save(firstQuest);

        // 更新游戏状态为PLAYING
        game.setStatus(GameStatus.PLAYING.getValue());
        gameRepository.save(game);

        // 发送WebSocket消息
        sendQuestStartedMessage(gameId, firstQuest);
    } else {
        // 对于后续任务，我们只需要启动下一个任务
        startNextRound(game);
    }
}
```

### 4.2 API变更

移除用于启动第一个任务的单独端点，使用统一端点：

```
POST /api/games/{gameId}/start-quest
```

此端点将根据当前游戏状态处理启动任何任务。

### 4.3 控制器变更

更新GameController以使用统一方法：

```java
@PostMapping("/{gameId}/start-quest")
public ResponseEntity<ApiResponse<String>> startQuest(@PathVariable UUID gameId) {
    try {
        gameService.startQuest(gameId);
        return ResponseEntity.ok(ApiResponse.success("任务开始成功", ""));
    } catch (Exception e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
    }
}
```

## 5. 实施计划

### 5.1 阶段1：后端实现

1. **在GameService中创建统一的startQuest方法**
2. **更新现有的startFirstQuest方法**以委托给统一方法（为了向后兼容）
3. **在GameController中添加新的start-quest端点**
4. **更新startNextRound方法**以确保它正确启动下一个任务

### 5.2 阶段2：测试

1. **统一startQuest方法的单元测试**
2. **新API端点的集成测试**
3. **回归测试**以确保现有功能仍然有效

### 5.3 阶段3：迁移

1. **更新前端**以使用新的统一端点
2. **在确认迁移完成后弃用旧端点**
3. **在未来的版本中移除旧的startFirstQuest方法**

## 6. 此方法的优势

1. **一致性**：单一方法处理所有任务启动操作
2. **可维护性**：减少代码重复，简化逻辑
3. **可扩展性**：更容易添加新的任务相关功能
4. **测试**：简化的测试场景，减少代码路径

## 7. 向后兼容性

为确保过渡期间的向后兼容性：

1. 保留现有的 `startFirstQuest` 方法，但让它委托给新的统一方法
2. 保留现有的端点，但标记为已弃用
3. 更新文档以推荐使用新的统一方法

## 8. 代码实现细节

### 8.1 统一的startQuest方法

统一方法将根据当前游戏状态确定要采取的操作：

- 如果游戏处于 `ROLE_VIEWING` 状态，它将创建所有任务并启动第一个任务
- 如果游戏处于 `PLAYING` 状态，它将按顺序启动下一个任务

### 8.2 任务创建逻辑

任务创建逻辑将保持不变，但从统一方法中调用：

```java
private void createQuests(Game game, int playerCount) {
    List<int[]> questConfigs = QUEST_CONFIGS.get(playerCount);
    if (questConfigs == null) {
        throw new RuntimeException("不支持的玩家数量: " + playerCount);
    }

    List<Quest> quests = new ArrayList<>();
    List<GamePlayer> players = gamePlayerRepository.findByGame(game);

    for (int i = 0; i < questConfigs.size(); i++) {
        int[] config = questConfigs.get(i);
        Quest quest = new Quest();
        quest.setGame(game);
        quest.setRoundNumber(i + 1);
        quest.setRequiredPlayers(config[0]);
        quest.setRequiredFails(config[1]);
        quest.setStatus(QuestStatus.PROPOSING.getValue());
        
        // 为每个任务设置队长（任务实际启动时会更新）
        quest.setLeader(players.get(i % players.size()).getUser());
        
        quests.add(quest);
    }

    questRepository.saveAll(quests);
}
```

### 8.3 下一个任务逻辑

下一个任务逻辑也将是统一方法的一部分：

```java
private void startNextRound(Game game) {
    game.setCurrentRound(game.getCurrentRound() + 1);
    
    // 查找要启动的下一个任务
    Quest nextQuest = questRepository.findByGameAndRoundNumber(game, game.getCurrentRound());
    if (nextQuest == null) {
        throw new RuntimeException("找不到第" + game.getCurrentRound() + "轮任务");
    }
    
    // 更新任务状态为proposing
    nextQuest.setStatus(QuestStatus.PROPOSING.getValue());
    
    // 为此任务设置队长
    List<GamePlayer> players = gamePlayerRepository.findByGame(game);
    int leaderIndex = (game.getCurrentRound() - 1) % players.size();
    GamePlayer leader = players.get(leaderIndex);
    nextQuest.setLeader(leader.getUser());
    
    questRepository.save(nextQuest);
    gameRepository.save(game);
    
    // 发送WebSocket消息
    sendQuestStartedMessage(game.getId(), nextQuest);
}
```

## 9. 测试策略

### 9.1 单元测试

1. 使用不同游戏状态测试统一的 `startQuest` 方法
2. 使用不同玩家数量测试任务创建
3. 测试下一个任务启动逻辑
4. 测试错误条件和边缘情况

### 9.2 集成测试

1. 测试新API端点
2. 测试从游戏开始到任务执行的完整流程
3. 测试与旧端点的向后兼容性

### 9.3 回归测试

1. 确保现有功能未被破坏
2. 验证WebSocket消息正确发送
3. 验证数据库状态正确更新

## 10. 文档更新

1. 更新API文档以反映新的统一端点
2. 将旧端点标记为已弃用
3. 更新任何引用旧方法的开发者文档
4. 如有必要，更新技术架构文档