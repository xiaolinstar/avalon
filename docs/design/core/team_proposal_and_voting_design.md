# 队伍提议和投票功能设计

## 1. 问题陈述

在阿瓦隆游戏中，队长需要选择任务成员（队伍提议），然后所有玩家对提议的队伍进行投票。目前系统缺少这部分核心功能的实现。

## 2. 功能需求

### 2.1 队伍提议功能
- 队长可以为当前任务提议一个队伍
- 队伍成员数量必须符合当前任务要求
- 只有当前队长可以提议队伍
- 提议后进入投票阶段

### 2.2 投票功能
- 所有玩家可以对提议的队伍进行投票（赞成/反对）
- 投票结果需要统计
- 投票通过后进入任务执行阶段
- 投票未通过则更换队长，重新提议

## 3. 设计目标

1. **符合游戏规则**：严格按照阿瓦隆游戏规则实现队伍提议和投票机制
2. **权限控制**：确保只有正确的玩家可以在正确的时机执行操作
3. **状态管理**：正确管理游戏和任务的状态转换
4. **实时通知**：通过WebSocket及时通知所有玩家状态变化

## 4. 技术实现方案

### 4.1 数据模型设计

#### 新增Proposal实体
```java
@Entity
@Table(name = "proposals")
public class Proposal {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quest_id", nullable = false)
    private Quest quest;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_id", nullable = false)
    private User leader;
    
    @ManyToMany
    @JoinTable(
        name = "proposal_members",
        joinColumns = @JoinColumn(name = "proposal_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> proposedMembers;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

#### 修改Quest实体
```java
public class Quest {
    // 现有字段...
    
    // 与Proposal的关联
    @OneToMany(mappedBy = "quest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Proposal> proposals;
    
    // 提议的队伍成员（仅用于向后兼容）
    @ManyToMany
    @JoinTable(
        name = "quest_proposed_members",
        joinColumns = @JoinColumn(name = "quest_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> proposedMembers;
}
```

#### 修改Vote实体
```java
@Entity
@Table(name = "votes")
public class Vote {
    @Id
    @GeneratedValue
    private UUID id;
    
    // 与Quest关联（向后兼容）
    @ManyToOne
    @JoinColumn(name = "quest_id")
    private Quest quest;
    
    // 与Proposal关联（新设计）
    @ManyToOne
    @JoinColumn(name = "proposal_id")
    private Proposal proposal;
    
    @ManyToOne
    @JoinColumn(name = "player_id")
    private User player;
    
    // 投票类型（赞成/反对）
    @Column(name = "vote_type", nullable = false, length = 10)
    private String voteType;
    
    private LocalDateTime votedAt;
}
```

### 4.2 核心服务方法

#### 队伍提议方法
```java
/**
 * 队长提议任务队伍
 * @param gameId 游戏ID
 * @param leaderId 队长ID
 * @param proposedMemberIds 提议的成员ID列表
 */
@Transactional
public Proposal proposeTeam(UUID gameId, UUID leaderId, List<UUID> proposedMemberIds) {
    Game game = gameRepository.findById(gameId)
        .orElseThrow(() -> new RuntimeException("游戏不存在"));
    
    User leader = userRepository.findById(leaderId)
        .orElseThrow(() -> new RuntimeException("玩家不存在"));
    
    Quest currentQuest = getCurrentQuest(game);
    if (currentQuest == null) {
        throw new RuntimeException("没有当前任务");
    }
    
    if (!currentQuest.getLeader().getId().equals(leaderId)) {
        throw new RuntimeException("不是当前队长");
    }
    
    // 验证队伍成员
    if (proposedMemberIds.size() != currentQuest.getRequiredPlayers()) {
        throw new RuntimeException("队伍人数不符合要求");
    }
    
    // 创建新的Proposal实体
    Proposal proposal = new Proposal();
    proposal.setQuest(currentQuest);
    proposal.setLeader(leader);
    List<User> proposedMembers = userRepository.findAllById(proposedMemberIds);
    proposal.setProposedMembers(proposedMembers);
    proposal.setCreatedAt(LocalDateTime.now());
    
    Proposal savedProposal = proposalRepository.save(proposal);
    
    // 发送WebSocket消息通知所有玩家开始投票
    GameMessage message = new GameMessage();
    message.setType("TEAM_PROPOSED");
    message.setGameId(gameId);
    message.setContent("队伍已提议，请投票");
    message.setTimestamp(System.currentTimeMillis());
    
    messagingTemplate.convertAndSend("/topic/game/" + gameId, message);
    
    return savedProposal;
}
```

#### 投票方法
```java
/**
 * 玩家投票
 * @param gameId 游戏ID
 * @param playerId 玩家ID
 * @param approve 是否赞成
 */
@Transactional
public Vote submitVote(UUID gameId, UUID proposalId, UUID playerId, boolean approve) {
    Game game = gameRepository.findById(gameId)
        .orElseThrow(() -> new RuntimeException("游戏不存在"));
    
    Proposal proposal = proposalRepository.findById(proposalId)
        .orElseThrow(() -> new RuntimeException("提议不存在"));
    
    User player = userRepository.findById(playerId)
        .orElseThrow(() -> new RuntimeException("玩家不存在"));
    
    // 检查是否已经对该提议投票
    if (voteRepository.existsByProposalAndPlayer(proposal, player)) {
        throw new RuntimeException("已经对该提议投过票了");
    }
    
    // 创建投票记录
    Vote vote = new Vote();
    vote.setProposal(proposal);
    vote.setPlayer(player);
    vote.setVoteType(approve ? "APPROVE" : "REJECT");
    vote.setVotedAt(LocalDateTime.now());
    
    Vote savedVote = voteRepository.save(vote);
    
    // 发送WebSocket消息通知投票情况
    GameMessage message = new GameMessage();
    message.setType("VOTE_SUBMITTED");
    message.setGameId(gameId);
    message.setContent(player.getUsername() + "已投票");
    message.setTimestamp(System.currentTimeMillis());
    
    messagingTemplate.convertAndSend("/topic/game/" + gameId, message);
    
    return savedVote;
}
```

#### 处理投票结果方法
```java
/**
 * 处理投票结果
 * @param gameId 游戏ID
 * @param proposalId 提议ID
 */
@Transactional
public void processVoteResults(UUID gameId, UUID proposalId) {
    Game game = gameRepository.findById(gameId)
        .orElseThrow(() -> new RuntimeException("游戏不存在"));
    
    Proposal proposal = proposalRepository.findById(proposalId)
        .orElseThrow(() -> new RuntimeException("提议不存在"));
    
    Quest currentQuest = proposal.getQuest();
    
    List<Vote> votes = voteRepository.findByProposal(proposal);
    long approveCount = votes.stream()
        .filter(vote -> "APPROVE".equals(vote.getVoteType()))
        .count();
    long rejectCount = votes.stream()
        .filter(vote -> "REJECT".equals(vote.getVoteType()))
        .count();
    
    boolean votePassed = approveCount > rejectCount;
    
    if (votePassed) {
        // 投票通过，进入任务执行阶段
        currentQuest.setStatus(QuestStatus.EXECUTING.getValue());
        
        // 将通过的提议成员设置为任务成员（向后兼容）
        currentQuest.setProposedMembers(proposal.getProposedMembers());
        
        questRepository.save(currentQuest);
        
        // 发送WebSocket消息通知任务执行开始
        GameMessage message = new GameMessage();
        message.setType("QUEST_EXECUTING");
        message.setGameId(gameId);
        message.setContent("投票通过，任务执行开始");
        message.setTimestamp(System.currentTimeMillis());
        
        messagingTemplate.convertAndSend("/topic/game/" + gameId, message);
    } else {
        // 投票失败，重新进入队伍组建阶段
        currentQuest.setStatus(QuestStatus.PROPOSING.getValue());
        questRepository.save(currentQuest);
        
        // 更换队长
        changeLeader(game, currentQuest);
        
        // 发送WebSocket消息通知重新提议队伍
        GameMessage message = new GameMessage();
        message.setType("TEAM_VETOED");
        message.setGameId(gameId);
        message.setContent("投票未通过，需要重新提议队伍");
        message.setTimestamp(System.currentTimeMillis());
        
        messagingTemplate.convertAndSend("/topic/game/" + gameId, message);
    }
}
```

### 4.3 API接口设计

#### 队伍提议接口
```
POST /api/games/{gameId}/proposals
```

请求体：
```json
{
  "playerIds": ["uuid1", "uuid2", "uuid3"]
}
```

#### 投票接口
```
POST /api/games/{gameId}/proposals/{proposalId}/votes
```

请求体：
```json
{
  "approve": true
}
```

#### 处理投票结果接口
```
POST /api/games/{gameId}/proposals/{proposalId}/process-votes
```

## 5. 实施计划

### 5.1 第一阶段：数据模型和实体类
1. 创建Proposal实体类
2. 修改Quest实体类，添加与Proposal的关联
3. 修改Vote实体类，支持关联Proposal
4. 创建相应的Repository接口

### 5.2 第二阶段：服务层实现
1. 实现队伍提议方法
2. 实现投票方法
3. 实现处理投票结果方法

### 5.3 第三阶段：控制器层实现
1. 创建队伍提议API端点
2. 创建投票API端点
3. 创建处理投票结果API端点

### 5.4 第四阶段：测试
1. 编写单元测试
2. 编写集成测试
3. 验证WebSocket通知功能

## 6. 测试策略

### 6.1 单元测试
- 测试队伍提议的各种边界条件
- 测试投票功能的正确性
- 测试投票结果处理逻辑

### 6.2 集成测试
- 测试完整的队伍提议到投票流程
- 验证WebSocket消息的正确发送
- 验证数据库状态的正确更新

## 7. 安全考虑

1. **权限验证**：确保只有当前队长可以提议队伍
2. **状态验证**：确保只在正确的游戏阶段执行操作
3. **数据验证**：验证队伍成员数量和身份的有效性
4. **重复投票防护**：防止玩家重复投票

## 8. 性能考虑

1. **数据库查询优化**：合理使用索引和连接查询
2. **事务管理**：确保数据一致性
3. **WebSocket消息**：批量发送消息以减少网络开销