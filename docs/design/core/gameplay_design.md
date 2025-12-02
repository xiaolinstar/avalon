# 游戏核心机制设计

## 1. 核心类设计

> 有关核心类的详细设计，请参阅 [Core Classes Design](core_classes_design.md)

> 有关任务系统设计的详细信息，请参阅 [Quest System Design](quest_system_design.md)

### Game类（游戏核心）

职能：一局游戏的"状态机 + 规则引擎"。

* 保存整局元数据（id、房间、当前轮次、状态机阶段）
* 负责阶段推进、角色分配、胜负判定、并发写保护（事务）

```java
public class Game {
    private UUID id;               // 局唯一标识
    private Room room;             // 所属房间
    private GameStatus status;     // 当前阶段（PREPARING / PROPOSING / VOTING ...）
    private int currentQuest;      // 第几轮任务（1-5）
    private List<GamePlayer> players; // 座位列表（含角色）
    private List<Quest> quests;    // 每轮任务快照，用于复盘
    private GameConfig config;   // 角色配置、最大人数等

    /**
     * 房主调用：把房间状态改为 PREPARING，分配角色，推送 WebSocket 事件
     */
    public void startGame();

    /**
     * 根据 config 把角色随机发到玩家，同时写入可见性缓存（Role#getVisiblePlayers）
     */
    public void assignRoles();

    /**
     * 收集完所有玩家投票后触发：
     * - 计算通过/否决
     * - 若通过→EXECUTING，给出征者推送执行页
     * - 若否决→PROPOSING，轮次+1，换队长
     * - 连续否决5次直接判邪恶胜利
     */
    public void processProposalVote(Proposal proposal);

    /**
     * 出征者全部提交任务卡后调用：
     * - 按规则计算成败（需至少1张失败即失败，第4轮需2张失败才失败）
     * - 更新好人/坏人任务胜利计数
     * - 进入 QUEST_RESULT 并广播结果
     */
    public void calculateQuestResult(Quest quest);

    /**
     * 好人3任务胜→进入 ASSASSINATION；
     * 坏人3任务胜→直接 FINISHED 邪恶胜利；
     * 刺杀完成→根据是否命中 Merlin 决定最终阵营胜利
     */
    public GameResult calculateFinalResult(Assassination assassination);
}
```

### RoomPlayer类（房间玩家）

职能：表示用户在房间中的状态和会话信息。

* 负责跟踪用户何时加入房间、座位号、主机状态等

```java
public class RoomPlayer {
    private UUID id;               // 房间玩家唯一标识
    private Room room;             // 关联房间
    private User user;             // 关联用户
    private boolean isHost;        // 是否为房主
    private boolean isActive;      // 是否活跃（未离开房间）
    private int seatNumber;        // 座位序号
    private LocalDateTime joinedAt; // 加入时间
    private LocalDateTime updatedAt; // 更新时间
}
```

### GamePlayer类（游戏内玩家）

职能：表示用户在游戏中的状态和游戏相关信息。

* 负责跟踪用户的游戏中角色、阵营、游戏内状态等信息

```java
public class GamePlayer {
    private UUID id;               // 游戏玩家唯一标识
    private Game game;             // 关联游戏
    private User user;             // 关联用户
    private String role;           // 分配到的角色（Merlin/Assassin...）
    private String alignment;      // 阵营（GOOD/EVIL）
    private boolean isHost;        // 是否为房主
    private int seatNumber;        // 座位序号
    private boolean isActive;      // 是否仍在局内

    /**
     * 对当前提案投票；写入 Vote 记录并触发 Game.processProposalVote
     */
    public Vote voteProposal(Proposal proposal, boolean approve);

    /**
     * 出征者匿名提交任务卡；仅当本玩家本轮在出征列表才可调用
     * @return true=任务成功，false=任务失败（由角色决定能否投失败）
     */
    public boolean submitQuestCard(boolean success);

    /**
     * 返回"我"能看到哪些其他玩家（基于角色枚举的可见性规则）
     */
    public List<GamePlayer> getVisiblePlayers();

    /**
     * 刺客阶段选择刺杀目标；写入 Assassination 记录
     */
    public Assassination chooseAssassinationTarget(GamePlayer target);

    /**
     * 查看角色信息
     */
    public RoleInfo viewRoleInfo();
}
```