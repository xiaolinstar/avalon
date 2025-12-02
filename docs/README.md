# Avalon 项目文档

## 文档结构

- [requirements/](requirements/) - 产品需求文档
  - [avalon_product_requirements.md](requirements/avalon_product_requirements.md) - 产品需求规格说明书

- [design/](design/) - 技术设计文档
  - [design_notes.md](design/design_notes.md) - 设计笔记（已归档）
  - [architecture/](design/architecture/) - 系统架构设计
    - [system_architecture.md](design/architecture/system_architecture.md) - 系统架构设计
    - [data_model_design.md](design/architecture/data_model_design.md) - 数据模型设计
  - [core/](design/core/) - 核心功能设计
    - [gameplay_design.md](design/core/gameplay_design.md) - 游戏核心机制设计
    - [role_system_design.md](design/core/role_system_design.md) - 角色系统设计
    - [quest_system_design.md](design/core/quest_system_design.md) - 任务系统设计
    - [core_classes_design.md](design/core/core_classes_design.md) - 核心类设计
  - [modeling/](design/modeling/) - 面向对象建模
    - [oo_modeling_design.md](design/modeling/oo_modeling_design.md) - 面向对象建模设计
  - [room/](design/room/) - 房间管理设计
    - [room_management_design.md](design/room/room_management_design.md) - 房间管理系统设计

- [api/](api/) - API文档
  - [api_design.md](api/api_design.md) - API设计文档

- [development/](development/) - 开发指南和流程

## 文档更新约定

1. 文档与代码同分支提交，保持同步
2. 仅维护必要文档，避免文档膨胀
3. PRD定义"做什么"，设计文档记录"怎么做"
4. 版本以日期标注在文档顶部

## 文档维护说明

- 需求文档由产品负责人维护
- 设计文档由架构师和核心开发维护
- API文档由后端开发维护
- 开发指南由技术负责人维护