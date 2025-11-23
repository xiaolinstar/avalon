package cn.xiaolin.avalon.service;

import cn.xiaolin.avalon.entity.*;
import cn.xiaolin.avalon.enums.*;
import cn.xiaolin.avalon.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import cn.xiaolin.avalon.dto.ProposeTeamRequest;
import cn.xiaolin.avalon.dto.VoteRequest;
import cn.xiaolin.avalon.dto.ExecuteQuestRequest;

@Service
@RequiredArgsConstructor
public class GameService {
    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final QuestRepository questRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final VoteRepository voteRepository;
    private final QuestResultRepository questResultRepository;

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
    public void startGame(UUID gameId) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));

        // 获取房间中的所有玩家
        Room room = game.getRoom();
        List<User> players = List.of(room.getCreator()); // 这里需要扩展为获取房间中的所有玩家
        
        if (players.size() < 5 || players.size() > 10) {
            throw new RuntimeException("游戏人数必须在5-10人之间");
        }

        // 分配角色
        assignRoles(game, players);

        // 创建任务
        createQuests(game, players.size());

        game.setStatus(GameStatus.PLAYING.getValue());
        game.setStartedAt(LocalDateTime.now());
        gameRepository.save(game);
    }

    private void assignRoles(Game game, List<User> players) {
        List<String> roles = new ArrayList<>(ROLE_CONFIGS.get(players.size()));
        Collections.shuffle(roles);

        for (int i = 0; i < players.size(); i++) {
            GamePlayer gamePlayer = new GamePlayer();
            gamePlayer.setGame(game);
            gamePlayer.setUser(players.get(i));
            gamePlayer.setRole(roles.get(i));
            gamePlayer.setAlignment(getAlignment(roles.get(i)));
            gamePlayer.setSeatNumber(i + 1);
            gamePlayer.setIsHost(i == 0); // 第一个玩家是房主
            
            gamePlayerRepository.save(gamePlayer);
        }
    }

    private void createQuests(Game game, int playerCount) {
        List<int[]> questConfig = QUEST_CONFIGS.get(playerCount);
        
        for (int i = 0; i < questConfig.size(); i++) {
            Quest quest = new Quest();
            quest.setGame(game);
            quest.setRoundNumber(i + 1);
            quest.setRequiredPlayers(questConfig.get(i)[0]);
            quest.setRequiredFails(questConfig.get(i)[1]);
            quest.setStatus(QuestStatus.PROPOSING.getValue());
            
            questRepository.save(quest);
        }
    }

    private String getAlignment(String role) {
        return switch (role) {
            case "merlin", "percival", "loyal_servant" -> Alignment.GOOD.getValue();
            case "morgana", "assassin", "minion", "oberon" -> Alignment.EVIL.getValue();
            default -> Alignment.GOOD.getValue();
        };
    }

    public Game getGameByRoomId(UUID roomId) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RuntimeException("房间不存在"));
        
        return gameRepository.findByRoom(room)
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

    // 队伍组建相关方法
    @Transactional
    public Quest proposeTeam(UUID gameId, UUID leaderId, ProposeTeamRequest request) {
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
        
        if (!currentQuest.getStatus().equals(QuestStatus.PROPOSING.getValue())) {
            throw new RuntimeException("当前阶段不是队伍组建");
        }
        
        // 验证队伍成员
        if (request.getPlayerIds().size() != currentQuest.getRequiredPlayers()) {
            throw new RuntimeException("队伍人数不符合要求");
        }
        
        // 更新任务状态为投票阶段
        currentQuest.setStatus(QuestStatus.VOTING.getValue());
        questRepository.save(currentQuest);
        
        return currentQuest;
    }

    @Transactional
    public Vote submitVote(UUID gameId, UUID playerId, VoteRequest request) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));
        
        GamePlayer player = gamePlayerRepository.findByGameAndUser(game, userRepository.findById(playerId).orElseThrow())
            .orElseThrow(() -> new RuntimeException("玩家不在游戏中"));
        
        Quest currentQuest = getCurrentQuest(game);
        if (currentQuest == null) {
            throw new RuntimeException("没有当前任务");
        }
        
        if (!currentQuest.getStatus().equals(QuestStatus.VOTING.getValue())) {
            throw new RuntimeException("当前阶段不是投票");
        }
        
        // 检查是否已经投票
        if (voteRepository.existsByQuestAndPlayer(currentQuest, player.getUser())) {
            throw new RuntimeException("已经投过票了");
        }
        
        // 创建投票记录
        Vote vote = new Vote();
        vote.setQuest(currentQuest);
        vote.setPlayer(player.getUser());
        vote.setVoteType(request.getVoteType());
        
        return voteRepository.save(vote);
    }

    @Transactional
    public void processVoteResults(UUID gameId) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));
        
        Quest currentQuest = getCurrentQuest(game);
        if (currentQuest == null) {
            throw new RuntimeException("没有当前任务");
        }
        
        List<Vote> votes = voteRepository.findByQuest(currentQuest);
        long approveCount = votes.stream()
            .filter(v -> v.getVoteType().equals(VoteType.APPROVE.getValue()))
            .count();
        long rejectCount = votes.stream()
            .filter(v -> v.getVoteType().equals(VoteType.REJECT.getValue()))
            .count();
        
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
        
        if (!currentQuest.getStatus().equals(QuestStatus.EXECUTING.getValue())) {
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
            
            if (questSuccess) {
                currentQuest.setStatus(QuestStatus.COMPLETED.getValue());
            } else {
                currentQuest.setStatus(QuestStatus.FAILED.getValue());
            }
            
            questRepository.save(currentQuest);
            
            // 进入下一轮或结束游戏
            if (questSuccess) {
                if (game.getCurrentRound() >= 5) {
                    // 正义阵营胜利
                    endGame(game, "good", "quest_victory");
                } else {
                    // 进入下一轮
                    startNextRound(game);
                }
            } else {
                // 检查是否已经有3个任务失败
                long failedQuests = questRepository.findByGameOrderByRoundNumber(game).stream()
                    .filter(q -> q.getStatus().equals(QuestStatus.FAILED.getValue()))
                    .count();
                
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

    private Quest getCurrentQuest(Game game) {
        return questRepository.findByGameOrderByRoundNumber(game).stream()
            .filter(q -> !q.getStatus().equals(QuestStatus.COMPLETED.getValue()) && 
                       !q.getStatus().equals(QuestStatus.FAILED.getValue()))
            .findFirst()
            .orElse(null);
    }

    private void changeLeader(Game game, Quest quest) {
        List<GamePlayer> players = gamePlayerRepository.findByGame(game);
        int currentLeaderIndex = players.stream()
            .filter(p -> p.getUser().getId().equals(quest.getLeader().getId()))
            .findFirst()
            .map(p -> p.getSeatNumber() - 1)
            .orElse(0);
        
        int nextLeaderIndex = (currentLeaderIndex + 1) % players.size();
        GamePlayer nextLeader = players.stream()
            .filter(p -> p.getSeatNumber() == nextLeaderIndex + 1)
            .findFirst()
            .orElse(players.get(0));
        
        quest.setLeader(nextLeader.getUser());
    }

    private List<GamePlayer> getQuestMembers(Quest quest) {
        // 这里需要实现获取队伍成员的逻辑
        // 暂时返回所有玩家，实际应该根据提议的队伍成员
        return gamePlayerRepository.findByGame(quest.getGame());
    }

    private boolean calculateQuestSuccess(Quest quest, List<QuestResult> results) {
        long failCount = results.stream()
            .filter(r -> !r.getSuccess())
            .count();
        
        return failCount < quest.getRequiredFails();
    }

    private void startNextRound(Game game) {
        game.setCurrentRound(game.getCurrentRound() + 1);
        
        // 创建新的任务
        Quest newQuest = new Quest();
        newQuest.setGame(game);
        newQuest.setRoundNumber(game.getCurrentRound());
        
        // 根据玩家数量和当前轮次设置任务参数
        int playerCount = gamePlayerRepository.findByGame(game).size();
        int[] questConfig = QUEST_CONFIGS.get(playerCount).get(game.getCurrentRound() - 1);
        newQuest.setRequiredPlayers(questConfig[0]);
        newQuest.setRequiredFails(questConfig[1]);
        newQuest.setStatus(QuestStatus.PROPOSING.getValue());
        
        // 设置新的队长
        List<GamePlayer> players = gamePlayerRepository.findByGame(game);
        int currentLeaderIndex = (game.getCurrentRound() - 1) % players.size();
        GamePlayer newLeader = players.get(currentLeaderIndex);
        newQuest.setLeader(newLeader.getUser());
        
        questRepository.save(newQuest);
        gameRepository.save(game);
    }

    private void endGame(Game game, String winner, String winType) {
        game.setStatus(GameStatus.ENDED.getValue());
        game.setWinner(winner);
        game.setEndedAt(LocalDateTime.now());
        gameRepository.save(game);
    }
}