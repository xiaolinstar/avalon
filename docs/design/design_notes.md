# Avalon 设计笔记（MVP）

> ⚠️ 此文档已归档，详细设计信息请参考以下文档：
> - [系统架构设计](../design/architecture/system_architecture.md)
> - [API设计](../api/api_design.md)
> - [房间管理设计](../design/room/room_management_design.md)
> - [游戏核心机制设计](../design/core/gameplay_design.md)
> - [角色系统设计](../design/core/role_system_design.md)

## 核心设计原则

1. **前后端分离**：前端使用React + TypeScript，后端使用Spring Boot + Java
2. **实时通信**：使用WebSocket进行游戏状态实时同步
3. **状态管理**：游戏状态由后端统一管理，前端通过订阅获取状态更新
4. **安全性**：JWT认证，权限控制，防止越权访问

## MVP功能范围

- 用户注册/登录
- 房间创建/加入
- 游戏角色分配
- 任务投票机制
- 任务执行机制
- 刺杀机制
- 游戏结果展示