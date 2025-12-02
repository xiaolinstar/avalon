# 房间管理系统设计

## 1. 系统功能需求分析

### 1.1 核心功能需求

#### 玩家准备状态管理
- **个人准备状态切换**：玩家可以切换自己的准备状态（准备/取消准备）
- **状态实时同步**：所有玩家都能看到其他玩家的准备状态变化
- **状态持久化**：准备状态在页面刷新后保持

#### 房主权限控制
- **开始游戏权限**：只有房主可以开始游戏
- **踢出玩家**：房主可以将未准备的玩家踢出房间
- **房间设置**：房主可以修改房间设置（最大人数、游戏模式等）

#### 开始游戏条件判断
- **人数检查**：当前玩家数 >= 5人且 <= 10人
- **准备状态检查**：所有玩家都已准备
- **自动开始**：满足条件时自动开始游戏（可选功能）

### 1.2 用户体验需求
- **视觉反馈**：清晰的准备状态指示（颜色、图标、文字）
- **实时更新**：状态变化立即反映到所有玩家界面
- **操作确认**：重要操作需要确认（如踢出玩家）
- **错误提示**：友好的错误信息和操作引导

## 2. 数据模型设计

### 2.1 玩家状态模型
```typescript
interface PlayerReadyState {
  playerId: string;
  username: string;
  isReady: boolean;
  isHost: boolean;
  joinedAt: string;
  readyAt?: string;
  avatar?: string;
}

interface RoomReadinessState {
  roomId: string;
  roomCode: string;
  players: PlayerReadyState[];
  totalPlayers: number;
  readyPlayers: number;
  minPlayers: number; // 5人
  maxPlayers: number; // 10人
  canStartGame: boolean;
  gameStarted: boolean;
}
```

### 2.2 状态变更记录
```typescript
interface ReadinessChangeLog {
  id: string;
  roomId: string;
  playerId: string;
  action: 'ready' | 'unready' | 'join' | 'leave' | 'kick';
  timestamp: string;
  oldState?: boolean;
  newState?: boolean;
}
```

## 3. 状态管理设计

### 3.1 Zustand Store 结构
```typescript
interface RoomReadinessStore {
  // 状态数据
  readinessState: RoomReadinessState | null;
  isLoading: boolean;
  error: string | null;
  
  // 派生状态
  currentPlayer: PlayerReadyState | null;
  isCurrentPlayerHost: boolean;
  isCurrentPlayerReady: boolean;
  missingPlayers: number; // 还需要多少玩家才能开始
  
  // 操作方法
  loadReadinessState: (roomId: string) => Promise<void>;
  toggleReady: () => Promise<void>;
  startGame: () => Promise<void>;
  kickPlayer: (playerId: string) => Promise<void>;
  updatePlayerState: (playerId: string, updates: Partial<PlayerReadyState>) => void;
  
  // 工具方法
  canStartGame: () => boolean;
  getReadyPlayers: () => PlayerReadyState[];
  getUnreadyPlayers: () => PlayerReadyState[];
}
```

### 3.2 状态同步策略
- **乐观更新**：本地立即更新，失败时回滚
- **冲突处理**：后到达的更新覆盖先到达的
- **重试机制**：网络失败时自动重试3次
- **离线处理**：本地状态缓存，恢复连接后同步

## 4. API接口设计

### 4.1 RESTful API
```typescript
// 获取房间准备状态
GET /api/rooms/{roomCode}/readiness
Response: {
  success: boolean;
  data: RoomReadinessState;
}

// 切换准备状态
POST /api/rooms/{roomCode}/ready
Request: { isReady: boolean }
Response: {
  success: boolean;
  data: PlayerReadyState;
}

// 开始游戏（房主）
POST /api/rooms/{roomCode}/start
Response: {
  success: boolean;
  data: { gameId: string; status: string };
}

// 踢出玩家（房主）
DELETE /api/rooms/{roomCode}/players/{playerId}
Response: {
  success: boolean;
  message: string;
}
```

### 4.2 WebSocket 事件
```typescript
// 玩家状态变更
interface PlayerReadyUpdateEvent {
  type: 'player_ready_update';
  roomId: string;
  playerId: string;
  isReady: boolean;
  readyAt?: string;
}

// 玩家加入/离开
interface PlayerJoinLeaveEvent {
  type: 'player_join' | 'player_leave';
  roomId: string;
  player: PlayerReadyState;
  currentCount: number;
}

// 游戏开始
interface GameStartEvent {
  type: 'game_started';
  roomId: string;
  gameId: string;
  startedBy: string;
}
```

## 5. UI界面设计

### 5.1 房间页面布局
```
┌─────────────────────────────────────────────────────────────┐
│  [Logo] 阿瓦隆                    [用户名 ▼] [退出] │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│                    房间号: ABC123                           │
│                    玩家人数: 6/8                          │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ 玩家列表                                           │    │
│  ├─────────────────────────────────────────────────────┤    │
│  │ 👑 玩家1        [准备就绪]    ✅ 已准备             │    │
│  │    玩家2        [点击准备]    ⏳ 未准备             │    │
│  │    玩家3        [点击准备]    ⏳ 未准备             │    │
│  │ 👑 玩家4        [准备就绪]    ✅ 已准备             │    │
│  │    玩家5        [点击准备]    ⏳ 未准备             │    │
│  │    玩家6        [准备就绪]    ✅ 已准备             │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                             │
│  状态提示: 还需要2名玩家，所有玩家准备后即可开始游戏      │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ [准备] [取消准备]        [开始游戏] (房主)          │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 组件设计
```typescript
// 玩家卡片组件
interface PlayerCardProps {
  player: PlayerReadyState;
  isCurrentUser: boolean;
  isHost: boolean;
  onReadyToggle: () => void;
  onKickPlayer?: () => void; // 房主踢人
}

// 准备状态指示器
interface ReadinessIndicatorProps {
  readyCount: number;
  totalCount: number;
  canStart: boolean;
}

// 开始游戏按钮
interface StartGameButtonProps {
  canStart: boolean;
  isHost: boolean;
  onStart: () => void;
  reason?: string; // 不能开始的原因
}
```

## 6. 实现步骤规划

### 6.1 第一阶段：基础框架 (1-2天)
1. **扩展数据模型**
   - 在现有Player和Room类型中添加准备状态字段
   - 创建RoomReadinessState类型
   - 更新相关接口定义

2. **创建状态管理**
   - 新建roomReadinessStore.ts
   - 实现基本的状态管理逻辑
   - 添加本地状态同步

3. **API接口准备**
   - 创建readinessAPI.ts服务
   - 定义API调用方法
   - 添加错误处理

### 6.2 第二阶段：UI界面 (1-2天)
1. **玩家列表组件**
   - 创建PlayerCard组件
   - 实现准备状态切换
   - 添加视觉反馈效果

2. **状态指示器**
   - 创建ReadinessIndicator组件
   - 显示准备进度
   - 添加状态提示信息

3. **控制按钮区域**
   - 实现准备/取消准备按钮
   - 创建开始游戏按钮
   - 添加权限控制逻辑

### 6.3 第三阶段：实时同步 (2-3天)
1. **WebSocket集成**
   - 扩展WebSocket连接
   - 实现玩家状态广播
   - 添加事件监听

2. **状态同步逻辑**
   - 实现乐观更新
   - 添加冲突处理
   - 错误恢复机制

3. **权限验证**
   - 房主权限检查
   - 开始游戏条件验证
   - 操作权限控制

### 6.4 第四阶段：优化完善 (1天)
1. **用户体验优化**
   - 添加加载状态
   - 实现错误提示
   - 优化响应速度

2. **测试验证**
   - 单元测试
   - 集成测试
   - 多用户场景测试

## 7. 技术实现细节

### 7.1 状态更新流程
```
用户点击准备按钮
    ↓
本地状态立即更新（乐观更新）
    ↓
发送API请求到后端
    ↓
后端验证并更新数据库
    ↓
WebSocket广播状态变更
    ↓
所有客户端接收更新
    ↓
同步本地状态
```

### 7.2 错误处理策略
- **网络错误**：显示友好提示，提供重试按钮
- **权限错误**：显示权限不足提示
- **状态冲突**：自动同步最新状态
- **服务器错误**：显示通用错误页面

### 7.3 性能优化
- **防抖处理**：快速点击时合并请求
- **缓存策略**：合理的本地缓存
- **按需加载**：组件懒加载
- **状态批量更新**：减少重渲染次数

## 8. 安全考虑

### 8.1 权限控制
- **后端验证**：所有操作都需要后端权限验证
- **防重复提交**：添加操作锁机制
- **数据验证**：输入数据严格验证
- **操作日志**：记录关键操作日志

### 8.2 数据保护
- **敏感信息脱敏**：不暴露用户敏感信息
- **状态一致性**：保证数据一致性
- **并发控制**：处理并发操作
- **回滚机制**：异常情况下的数据回滚

这个设计方案涵盖了房间准备系统的所有核心功能，可以作为开发的详细指导文档。建议按照实现步骤逐步开发，确保每个阶段的功能都能正常工作。