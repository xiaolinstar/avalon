package cn.xiaolin.avalon.service;

import cn.xiaolin.avalon.dto.ExecuteQuestRequest;
import cn.xiaolin.avalon.entity.*;
import cn.xiaolin.avalon.enums.QuestStatus;
import cn.xiaolin.avalon.enums.VoteType;
import cn.xiaolin.avalon.repository.*;
import cn.xiaolin.avalon.websocket.GameMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class QuestService {
    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final QuestRepository questRepository;
    private final VoteRepository voteRepository;
    private final QuestResultRepository questResultRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository; // 添加RoomRepository
    private final SimpMessagingTemplate messagingTemplate;
    
    @PersistenceContext
    private EntityManager entityManager;

    // 任务配置：轮次 -> (需要玩家数, 需要失败数)
    private static final Map<Integer, List<int[]>> QUEST_CONFIGS = Map.of(
        5, List.of(new int[]{2, 1}, new int[]{3, 1}, new int[]{2, 1}, new int[]{3, 1}, new int[]{3, 1}),
        6, List.of(new int[]{2, 1}, new int[]{3, 1}, new int[]{4, 1}, new int[]{3, 1}, new int[]{4, 1}),
        7, List.of(new int[]{2, 1}, new int[]{3, 1}, new int[]{3, 1}, new int[]{4, 2}, new int[]{4, 1}),
        8, List.of(new int[]{3, 1}, new int[]{4, 1}, new int[]{4, 1}, new int[]{5, 2}, new int[]{5, 1}),
        9, List.of(new int[]{3, 1}, new int[]{4, 1}, new int[]{4, 1}, new int[]{5, 2}, new int[]{5, 1}),
        10, List.of(new int[]{3, 1}, new int[]{4, 1}, new int[]{4, 1}, new int[]{5, 2}, new int[]{5, 1})
    );

    /**
     * 统一的任务启动方法
     * @param gameId 游戏ID
     * @param isFirstQuest 是否为第一个任务
     */
    @Transactional
    public void startQuest(UUID gameId, boolean isFirstQuest) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));

        // 对于第一个任务，需要特殊处理
        if (isFirstQuest) {
            // 确保游戏处于正确的状态（ROLE_VIEWING表示所有玩家已加入并准备开始游戏）
            if (!Objects.equals(game.getStatus(), "role_viewing")) {
                throw new RuntimeException("游戏状态不正确，无法开始第一个任务");
            }
            
            // 获取玩家数量
            List<GamePlayer> gamePlayers = gamePlayerRepository.findByGame(game);
            int playerCount = gamePlayers.size();
            
            // 为游戏创建所有任务
            createQuests(game, playerCount);
            
            // 更新游戏状态为PLAYING，表示游戏正式开始
            game.setStatus("playing");
            gameRepository.save(game);
            
            // 获取第一个任务
            Quest firstQuest = questRepository.findByGameOrderByRoundNumber(game).get(0);
            if (firstQuest == null) {
                throw new RuntimeException("没有找到第一个任务");
            }
            
            // 确保第一个任务的队长已设置
            if (Objects.isNull(firstQuest.getLeader()) && !gamePlayers.isEmpty()) {
                firstQuest.setLeader(gamePlayers.get(0).getUser());
                questRepository.save(firstQuest);
            }
            
            // 发送WebSocket消息通知所有玩家第一个任务已开始
            GameMessage message = new GameMessage();
            message.setType("FIRST_QUEST_STARTED");
            message.setGameId(gameId);
            message.setContent("第一个任务已开始");
            message.setTimestamp(System.currentTimeMillis());
            
            messagingTemplate.convertAndSend("/topic/game/" + gameId, message);
        } else {
            // 后续任务的处理逻辑
            startNextRound(game);
        }
    }

    /**
     * 为游戏创建所有任务
     * @param game 游戏对象
     * @param playerCount 玩家数量
     */
    private void createQuests(Game game, int playerCount) {
        List<int[]> questConfig = QUEST_CONFIGS.get(playerCount);
        
        // 获取游戏中的所有玩家，用于设置任务的队长
        List<GamePlayer> gamePlayers = gamePlayerRepository.findByGame(game);
        
        for (int i = 0; i < questConfig.size(); i++) {
            Quest quest = new Quest();
            quest.setGame(game);
            quest.setRoundNumber(i + 1);
            quest.setRequiredPlayers(questConfig.get(i)[0]);
            quest.setRequiredFails(questConfig.get(i)[1]);
            quest.setStatus(QuestStatus.PROPOSING.getValue());
            
            // 为每个任务设置队长（按座位号顺序循环选择）
            if (!gamePlayers.isEmpty()) {
                int leaderIndex = i % gamePlayers.size();
                quest.setLeader(gamePlayers.get(leaderIndex).getUser());
            }
            
            questRepository.save(quest);
        }
    }

    public List<Quest> getGameQuests(UUID gameId) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));
        
        return questRepository.findByGameOrderByRoundNumber(game);
    }

    @Transactional
    public void executeQuest(UUID gameId, UUID playerId, ExecuteQuestRequest request) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));
        
        GamePlayer player = gamePlayerRepository.findByGameAndUser(game, userRepository.findById(playerId).orElseThrow())
            .orElseThrow(() -> new RuntimeException("玩家不在游戏中"));
        
        Quest currentQuest = getCurrentQuest(game);
        if (currentQuest == null) {
            throw new RuntimeException("没有当前任务");
        }
        
        // 验证任务状态是否为执行阶段
        if (!Objects.equals(currentQuest.getStatus(), QuestStatus.EXECUTING.getValue())) {
            throw new RuntimeException("当前阶段不是任务执行");
        }
        
        // 创建任务执行结果
        QuestResult result = new QuestResult();
        result.setQuest(currentQuest);
        result.setPlayer(player.getUser());
        result.setSuccess(request.getSuccess());
        result.setExecutedAt(LocalDateTime.now());
        
        questResultRepository.save(result);
        
        // 检查是否所有队员都执行了任务
        List<GamePlayer> questMembers = getQuestMembers(currentQuest); // 这里需要实现获取队伍成员的逻辑
        List<QuestResult> results = questResultRepository.findByQuest(currentQuest);
        
        if (results.size() == questMembers.size()) {
            // 所有队员都执行了任务，计算结果
            boolean questSuccess = calculateQuestSuccess(currentQuest, results);
            
            // 进入下一轮或结束游戏
            if (questSuccess) {
                // 先设置当前任务为完成状态
                currentQuest.setStatus(QuestStatus.COMPLETED.getValue());
                questRepository.save(currentQuest);
                
                // 重新加载游戏对象以确保数据是最新的
                game = gameRepository.findById(game.getId()).orElseThrow(() -> new RuntimeException("游戏不存在"));
                
                // 重新加载当前任务以确保数据是最新的
                currentQuest = questRepository.findById(currentQuest.getId()).orElseThrow(() -> new RuntimeException("任务不存在"));
                
                // 检查是否已经有3个任务成功完成（包括当前这个）
                // 注意：我们刚刚设置了当前任务为COMPLETED状态，所以这里应该能正确计数
                List<Quest> allQuests = questRepository.findByGameOrderByRoundNumber(game);
                long completedQuests = allQuests.stream()
                    .filter(q -> Objects.equals(q.getStatus(), QuestStatus.COMPLETED.getValue()))
                    .count();
                
                // 检查是否已经完成了3个任务
                if (completedQuests >= 3) {
                    // 正义阵营胜利
                    endGame(game, "good", "quest_victory");
                } else {
                    // 进入下一轮
                    startNextRound(game);
                }

            } else {
                // 先设置当前任务为失败状态
                currentQuest.setStatus(QuestStatus.FAILED.getValue());
                questRepository.save(currentQuest);
                
                // 重新加载游戏对象以确保数据是最新的
                game = gameRepository.findById(game.getId()).orElseThrow(() -> new RuntimeException("游戏不存在"));
                
                // 重新加载当前任务以确保数据是最新的
                currentQuest = questRepository.findById(currentQuest.getId()).orElseThrow(() -> new RuntimeException("任务不存在"));
                
                // 检查是否已经有3个任务失败（包括当前这个）
                List<Quest> allQuests = questRepository.findByGameOrderByRoundNumber(game);
                long failedQuests = allQuests.stream()
                    .filter(q -> Objects.equals(q.getStatus(), QuestStatus.FAILED.getValue()))
                    .count();
                
                // 检查是否已经失败了3个任务
                if (failedQuests >= 3) {
                    // 邪恶阵营胜利
                    endGame(game, "evil", "quest_failure");
                } else {
                    // 进入下一轮
                    startNextRound(game);
                }

            }
        }
    }

    /**
     * 获取当前进行中的任务
     * @param game 游戏对象
     * @return 当前任务，如果没有找到则返回null
     */
    private Quest getCurrentQuest(Game game) {
        return questRepository.findByGameOrderByRoundNumber(game).stream()
            .filter(q -> !Objects.equals(q.getStatus(), QuestStatus.COMPLETED.getValue()) && 
                       !Objects.equals(q.getStatus(), QuestStatus.FAILED.getValue()))
            .findFirst()
            .orElse(null);
    }

    private List<GamePlayer> getQuestMembers(Quest quest) {
        // 获取当前任务提议的队伍成员
        return quest.getProposedMembers().stream()
            .map(user -> gamePlayerRepository.findByGameAndUser(quest.getGame(), user)
                .orElseThrow(() -> new RuntimeException("玩家不在游戏中")))
            .collect(Collectors.toList());
    }

    private boolean calculateQuestSuccess(Quest quest, List<QuestResult> results) {
        long failCount = results.stream()
            .filter(r -> !r.getSuccess())
            .count();
        
        return failCount < quest.getRequiredFails();
    }

    private void startNextRound(Game game) {
        // 增加游戏轮次
        game.setCurrentRound(game.getCurrentRound() + 1);
        gameRepository.save(game);
        
        // 获取对应轮次的任务（所有任务在游戏开始时已预先创建）
        List<Quest> quests = questRepository.findByGameOrderByRoundNumber(game);
        
        // 添加调试信息
        System.out.println("游戏当前轮次: " + game.getCurrentRound());
        System.out.println("找到的任务数量: " + quests.size());
        for (Quest q : quests) {
            System.out.println("任务轮次: " + q.getRoundNumber());
        }
        
        Quest nextQuest = quests.stream()
            .filter(q -> Objects.equals(q.getRoundNumber(), game.getCurrentRound()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("没有找到第" + game.getCurrentRound() + "轮任务"));
        
        // 更新任务状态为队伍组建阶段
        nextQuest.setStatus(QuestStatus.PROPOSING.getValue());
        
        // 设置新的队长（按座位号顺序循环选择）
        List<GamePlayer> players = gamePlayerRepository.findByGame(game);
        int currentLeaderIndex = (game.getCurrentRound() - 1) % players.size();
        GamePlayer newLeader = players.get(currentLeaderIndex);
        nextQuest.setLeader(newLeader.getUser());
        
        questRepository.save(nextQuest);
        
        // 发送WebSocket消息通知所有玩家下一轮已开始
        GameMessage message = new GameMessage();
        message.setType("NEXT_ROUND_STARTED");
        message.setGameId(game.getId());
        message.setContent("第" + game.getCurrentRound() + "轮任务已开始");
        message.setTimestamp(System.currentTimeMillis());
        
        messagingTemplate.convertAndSend("/topic/game/" + game.getId(), message);
    }

    private void endGame(Game game, String winner, String winType) {
        game.setStatus("ended");
        game.setWinner(winner);
        game.setEndedAt(LocalDateTime.now());
        gameRepository.save(game);
        
        // 同时更新房间状态为ended
        Room room = game.getRoom();
        if (room != null) {
            room.setStatus("ended");
            roomRepository.save(room);
        }
        
        // 发送WebSocket消息通知所有玩家游戏已结束
        GameMessage message = new GameMessage();
        message.setType("QUEST_COMPLETED");
        message.setGameId(game.getId());
        message.setContent("游戏结束，" + ("good".equals(winner) ? "正义" : "邪恶") + "阵营获胜");
        message.setTimestamp(System.currentTimeMillis());
        
        messagingTemplate.convertAndSend("/topic/game/" + game.getId(), message);
    }
}