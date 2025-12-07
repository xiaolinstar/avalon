package cn.xiaolin.avalon.service;

import cn.xiaolin.avalon.dto.*;
import cn.xiaolin.avalon.entity.*;
import cn.xiaolin.avalon.enums.*;
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
public class GameService {
    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final RoomRepository roomRepository;
    private final RoomPlayerRepository roomPlayerRepository;
    private final QuestRepository questRepository;
    private final VoteRepository voteRepository;
    private final QuestResultRepository questResultRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    
    @PersistenceContext
    private EntityManager entityManager;

    // 角色配置
    private static final Map<Integer, List<String>> ROLE_CONFIGS = Map.of(
        5, List.of("merlin", "percival", "loyal_servant", "morgana", "assassin"),
        6, List.of("merlin", "percival", "loyal_servant", "loyal_servant", "morgana", "assassin"),
        7, List.of("merlin", "percival", "loyal_servant", "loyal_servant", "morgana", "assassin", "minion"),
        8, List.of("merlin", "percival", "loyal_servant", "loyal_servant", "loyal_servant", "morgana", "assassin", "minion"),
        9, List.of("merlin", "percival", "loyal_servant", "loyal_servant", "loyal_servant", "loyal_servant", "morgana", "assassin", "minion"),
        10, List.of("merlin", "percival", "loyal_servant", "loyal_servant", "loyal_servant", "loyal_servant", "morgana", "assassin", "minion", "oberon")
    );

    // 队伍组建投票失败次数限制
    private static final int MAX_TEAM_PROPOSAL_FAILURES = 5;

    // 任务配置：轮次 -> (需要玩家数, 需要失败数)
    private static final Map<Integer, List<int[]>> QUEST_CONFIGS = Map.of(
        5, List.of(new int[]{2, 1}, new int[]{3, 1}, new int[]{2, 1}, new int[]{3, 1}, new int[]{3, 1}),
        6, List.of(new int[]{2, 1}, new int[]{3, 1}, new int[]{4, 1}, new int[]{3, 1}, new int[]{4, 1}),
        7, List.of(new int[]{2, 1}, new int[]{3, 1}, new int[]{3, 1}, new int[]{4, 2}, new int[]{4, 1}),
        8, List.of(new int[]{3, 1}, new int[]{4, 1}, new int[]{4, 1}, new int[]{5, 2}, new int[]{5, 1}),
        9, List.of(new int[]{3, 1}, new int[]{4, 1}, new int[]{4, 1}, new int[]{5, 2}, new int[]{5, 1}),
        10, List.of(new int[]{3, 1}, new int[]{4, 1}, new int[]{4, 1}, new int[]{5, 2}, new int[]{5, 1})
    );

    @Transactional
    public Game createGame(UUID roomId) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RuntimeException("房间不存在"));

        Game game = new Game();
        game.setRoom(room);
        game.setStatus(GameStatus.PREPARING.getValue());
        game.setCurrentRound(1);
        
        return gameRepository.save(game);
    }

    @Transactional
    public void startGame(UUID roomId) {
        // 首先通过roomId获取房间
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RuntimeException("房间不存在"));
        
        String roomCode = room.getRoomCode();
        
        // 检查房间是否已经有游戏存在
        Optional<Game> existingGameOpt = gameRepository.findByRoomRoomCode(roomCode);
        
        // 如果已有游戏且游戏正在进行中，则返回错误
        if (existingGameOpt.isPresent()) {
            Game existingGame = existingGameOpt.get();
            if (!Objects.equals(existingGame.getStatus(), GameStatus.ENDED.getValue())) {
                throw new RuntimeException("游戏已开始");
            }
        }
        
        room.setStatus(RoomStatus.PLAYING.getValue());
        roomRepository.save(room);

        // 获取房间中的所有活跃玩家
        List<RoomPlayer> roomPlayers = roomPlayerRepository.findByRoomIdAndIsActiveTrue(room.getId());
        List<User> players = roomPlayers.stream()
                .map(RoomPlayer::getUser)
                .collect(Collectors.toList());

        if (players.size() < 5 || players.size() > 10) {
            throw new RuntimeException("游戏人数必须在5-10人之间");
        }

        // 创建新游戏并保存
        Game game = new Game();
        game.setRoom(room);
        game.setStatus(GameStatus.ROLE_VIEWING.getValue());
        game.setStartedAt(LocalDateTime.now());
        game = gameRepository.save(game);

        // 分配角色，并将角色信息存储到数据库
        assignRoles(game, players);

        // 发送WebSocket消息通知所有玩家游戏已开始，可以查看角色
        GameMessage message = new GameMessage();
        message.setType("GAME_STARTED");
        message.setGameId(game.getId());
        message.setRoomId(room.getId());
        message.setContent("游戏已开始，请查看您的角色");
        message.setTimestamp(System.currentTimeMillis());
        
        messagingTemplate.convertAndSend("/topic/room/" + room.getId(), message);
    }

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
            if (!Objects.equals(game.getStatus(), GameStatus.ROLE_VIEWING.getValue())) {
                throw new RuntimeException("游戏状态不正确，无法开始第一个任务");
            }
            
            // 获取玩家数量
            List<GamePlayer> gamePlayers = gamePlayerRepository.findByGame(game);
            int playerCount = gamePlayers.size();
            
            // 为游戏创建所有任务
            createQuests(game, playerCount);
            
            // 更新游戏状态为PLAYING，表示游戏正式开始
            game.setStatus(GameStatus.PLAYING.getValue());
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

    private void assignRoles(Game game, List<User> players) {
        List<String> roles = new ArrayList<>(ROLE_CONFIGS.get(players.size()));
        Collections.shuffle(roles);
        List<GamePlayer> gamePlayerList = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            GamePlayer gamePlayer = new GamePlayer();
            gamePlayer.setGame(game);
            gamePlayer.setUser(players.get(i));
            gamePlayer.setRole(roles.get(i));
            gamePlayer.setAlignment(getAlignment(roles.get(i)));
            gamePlayer.setSeatNumber(i + 1);
            gamePlayer.setIsHost(i == 0); // 第一个玩家是房主
            gamePlayerList.add(gamePlayer);
        }
        gamePlayerRepository.saveAll(gamePlayerList);
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

    private String getAlignment(String role) {
        return switch (role) {
            case "morgana", "assassin", "minion", "oberon" -> Alignment.EVIL.getValue();
            default -> Alignment.GOOD.getValue();
        };
    }

    public Game getGameById(UUID gameId) {
        Optional<Game> gameOpt = gameRepository.findById(gameId);
        if (gameOpt.isPresent()) {
            return gameOpt.get();
        } else {
            throw new RuntimeException("游戏不存在");
        }
    }

    public Game getGameByRoomId(UUID roomId) {
        return gameRepository.findByRoomId(roomId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));
    }

    public List<GamePlayer> getGamePlayers(UUID gameId) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));
        
        return gamePlayerRepository.findByGame(game);
    }

    public List<Quest> getGameQuests(UUID gameId) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));
        
        return questRepository.findByGameOrderByRoundNumber(game);
    }

    public String getGameStatus(UUID gameId) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));
        return game.getStatus();
    }

    // 队伍组建相关方法
    @Transactional
    public Quest proposeTeam(UUID gameId, UUID leaderId, ProposeTeamRequest request) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));
    
        Quest currentQuest = getCurrentQuest(game);
        if (currentQuest == null) {
            throw new RuntimeException("没有当前任务");
        }
    
        // 检查队长是否为空
        if (Objects.isNull(currentQuest.getLeader())) {
            // 尝试重新加载任务以获取队长信息
            currentQuest = questRepository.findById(currentQuest.getId())
                    .orElse(currentQuest);
            if (Objects.isNull(currentQuest.getLeader())) {
                throw new RuntimeException("当前任务队长未设置");
            }
        }
    
        // 验证请求者是否为当前任务的队长
        if (!Objects.equals(currentQuest.getLeader().getId(), leaderId)) {
            throw new RuntimeException("不是当前队长");
        }
    
        // 验证任务状态是否为队伍组建阶段
        if (!Objects.equals(currentQuest.getStatus(), QuestStatus.PROPOSING.getValue())) {
            throw new RuntimeException("当前阶段不是队伍组建");
        }
    
        // 验证队伍成员数量是否符合要求
        if (request.getPlayerIds().size() != currentQuest.getRequiredPlayers()) {
            throw new RuntimeException("队伍人数不符合要求");
        }
    
        // 设置提议的队伍成员
        List<User> proposedMembers = userRepository.findAllById(request.getPlayerIds());
        currentQuest.setProposedMembers(proposedMembers);
    
        // 更新任务状态为投票阶段
        currentQuest.setStatus(QuestStatus.VOTING.getValue());
        questRepository.save(currentQuest);
    
        // 刷新实体管理器以确保数据同步
        entityManager.flush();
    
        // 发送WebSocket消息通知所有玩家开始投票
        GameMessage message = new GameMessage();
        message.setType("TEAM_PROPOSED");
        message.setGameId(gameId);
        message.setContent("队伍已提议，请投票");
        message.setTimestamp(System.currentTimeMillis());
    
        messagingTemplate.convertAndSend("/topic/game/" + gameId, message);
    
        return currentQuest;
    }

    @Transactional
    public Vote submitVote(UUID gameId, UUID playerId, VoteRequest request) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));
    
        GamePlayer player = gamePlayerRepository.findByGameAndUser(game, userRepository.findById(playerId).orElseThrow())
            .orElseThrow(() -> new RuntimeException("玩家不在游戏中"));
        
        // 获取当前进行中的任务（排除已完成或失败的任务）
        List<Quest> quests = questRepository.findByGameOrderByRoundNumber(game);
        Quest currentQuest = quests.stream()
            .filter(q -> !Objects.equals(q.getStatus(), QuestStatus.COMPLETED.getValue()) && 
                       !Objects.equals(q.getStatus(), QuestStatus.FAILED.getValue()))
            .findFirst()
            .orElse(null);
        
        if (Objects.isNull(currentQuest)) {
            throw new RuntimeException("没有当前任务");
        }
    
        // 通过ID重新查询任务以确保获取最新状态
        currentQuest = questRepository.findById(currentQuest.getId())
            .orElseThrow(() -> new RuntimeException("任务不存在"));
    
        // 验证任务状态是否为投票阶段
        if (!Objects.equals(currentQuest.getStatus(),QuestStatus.VOTING.getValue())) {
            // 添加更详细的错误信息
            throw new RuntimeException("当前阶段不是投票，当前状态为: " + currentQuest.getStatus());
        }
    
        // 检查玩家是否已经对该任务投过票
        boolean hasVoted = voteRepository.existsByQuestAndPlayer(currentQuest, player.getUser());
        if (hasVoted) {
            throw new RuntimeException("已经投过票了");
        }
    
        // 创建投票记录
        Vote vote = new Vote();
        vote.setQuest(currentQuest);
        vote.setPlayer(player.getUser());
        vote.setVoteType(request.getVoteType());
    
        Vote savedVote = voteRepository.save(vote);
        
        // 检查是否所有玩家都已投票，如果是则处理投票结果
        List<GamePlayer> gamePlayers = gamePlayerRepository.findByGame(game);
        List<Vote> votes = voteRepository.findByQuest(currentQuest);
        
        // 如果所有玩家都已投票，则处理投票结果
        if (votes.size() == gamePlayers.size()) {
            processVoteResults(gameId);
        } else {
            // 发送WebSocket消息通知投票情况
            GameMessage message = new GameMessage();
            message.setType("VOTE_SUBMITTED");
            message.setGameId(gameId);
            message.setContent(player.getUser().getUsername() + "已投票");
            message.setTimestamp(System.currentTimeMillis());
            
            messagingTemplate.convertAndSend("/topic/game/" + gameId, message);
        }
    
        return savedVote;
    }

    @Transactional
    public void processVoteResults(UUID gameId) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));
        
        Quest currentQuest = getCurrentQuest(game);
        if (Objects.isNull(currentQuest)) {
            throw new RuntimeException("没有当前任务");
        }
        
        // 统计投票结果
        List<Vote> votes = voteRepository.findByQuest(currentQuest);
        long approveCount = votes.stream()
            .filter(v -> Objects.equals(v.getVoteType(), VoteType.APPROVE.getValue()))
            .count();
        long rejectCount = votes.stream()
            .filter(v -> Objects.equals(v.getVoteType(), VoteType.REJECT.getValue()))
            .count();
        
        // 判断投票是否通过（赞成票数大于反对票数）
        boolean votePassed = approveCount > rejectCount;
        
        if (votePassed) {
            // 投票通过，进入任务执行阶段
            currentQuest.setStatus(QuestStatus.EXECUTING.getValue());
        } else {
            // 投票失败，重新进入队伍组建阶段
            currentQuest.setStatus(QuestStatus.PROPOSING.getValue());
            // 更换队长
            changeLeader(game, currentQuest);
        }
        
        questRepository.save(currentQuest);
        
        // 发送WebSocket消息通知投票结果
        GameMessage message = new GameMessage();
        if (votePassed) {
            message.setType("TEAM_APPROVED");
            message.setContent("队伍提议已通过，进入任务执行阶段");
        } else {
            message.setType("TEAM_REJECTED");
            message.setContent("队伍提议被否决，需要重新提议队伍");
        }
        message.setGameId(gameId);
        message.setTimestamp(System.currentTimeMillis());
        
        messagingTemplate.convertAndSend("/topic/game/" + gameId, message);
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
                
                // 检查是否已经有5个任务成功完成（包括当前这个）
                // 注意：我们刚刚设置了当前任务为COMPLETED状态，所以这里应该能正确计数
                List<Quest> allQuests = questRepository.findByGameOrderByRoundNumber(game);
                long completedQuests = allQuests.stream()
                    .filter(q -> Objects.equals(q.getStatus(), QuestStatus.COMPLETED.getValue()))
                    .count();
                
                // 添加调试信息
                System.out.println("游戏ID: " + game.getId());
                System.out.println("当前任务轮次: " + currentQuest.getRoundNumber());
                System.out.println("当前任务状态: " + currentQuest.getStatus());
                System.out.println("所有任务数量: " + allQuests.size());
                System.out.println("已完成任务数量: " + completedQuests);
                
                if (completedQuests >= 3) {
                    // 正义阵营胜利
                    System.out.println("正义阵营胜利，结束游戏");
                    endGame(game, "good", "quest_victory");
                } else {
                    // 进入下一轮
                    System.out.println("进入下一轮");
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
                
                // 添加调试信息
                System.out.println("游戏ID: " + game.getId());
                System.out.println("当前任务轮次: " + currentQuest.getRoundNumber());
                System.out.println("当前任务状态: " + currentQuest.getStatus());
                System.out.println("所有任务数量: " + allQuests.size());
                System.out.println("已失败任务数量: " + failedQuests);
                
                if (failedQuests >= 3) {
                    // 邪恶阵营胜利
                    System.out.println("邪恶阵营胜利，结束游戏");
                    endGame(game, "evil", "quest_failure");
                } else {
                    // 进入下一轮
                    System.out.println("进入下一轮");
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

    private void changeLeader(Game game, Quest quest) {
        List<GamePlayer> players = gamePlayerRepository.findByGame(game);
        // 找到当前队长在玩家列表中的索引位置
        int currentLeaderIndex = players.stream()
            .filter(p -> Objects.equals(p.getUser().getId(), quest.getLeader().getId()))
            .findFirst()
            .map(p -> p.getSeatNumber() - 1)
            .orElse(0);
        
        // 计算下一个队长的索引位置（循环选择）
        int nextLeaderIndex = (currentLeaderIndex + 1) % players.size();
        GamePlayer nextLeader = players.stream()
            .filter(p -> p.getSeatNumber() == nextLeaderIndex + 1)
            .findFirst()
            .orElse(players.get(0));
        
        quest.setLeader(nextLeader.getUser());
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
        game.setStatus(GameStatus.ENDED.getValue());
        game.setWinner(winner);
        game.setEndedAt(LocalDateTime.now());
        gameRepository.save(game);
        
        // 更新房间状态为ended
        Room room = game.getRoom();
        if (room != null) {
            room.setStatus(RoomStatus.ENDED.getValue());
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