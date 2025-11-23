package cn.xiaolin.avalon.service;

import cn.xiaolin.avalon.dto.GameStateResponse;
import cn.xiaolin.avalon.entity.*;
import cn.xiaolin.avalon.enums.*;
import cn.xiaolin.avalon.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class GameStateService {
    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final QuestRepository questRepository;
    private final VoteRepository voteRepository;
    private final QuestResultRepository questResultRepository;
    private final RoleVisibilityService roleVisibilityService;

    @Cacheable(value = "gameState", key = "#gameId + '_' + #userId")
    public GameStateResponse getGameState(UUID gameId, UUID userId) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));
        
        // 使用JOIN FETCH预加载用户关联，避免N+1查询
        List<GamePlayer> players = gamePlayerRepository.findByGameWithUser(game);
        List<Quest> quests = questRepository.findByGameOrderByRoundNumber(game);
        
        // 批量加载投票和任务结果，避免每个任务单独查询
        List<Vote> allVotes = voteRepository.findByQuestsWithQuest(quests);
        List<QuestResult> allResults = questResultRepository.findByQuestsWithQuest(quests);
        
        // 将投票和结果按任务ID分组，提高查询效率
        Map<UUID, List<Vote>> votesByQuest = allVotes.stream()
            .collect(Collectors.groupingBy(vote -> vote.getQuest().getId()));
        Map<UUID, List<QuestResult>> resultsByQuest = allResults.stream()
            .collect(Collectors.groupingBy(result -> result.getQuest().getId()));
        
        // 获取当前玩家 - 现在用户已预加载，不会触发额外查询
        GamePlayer currentPlayer = players.stream()
            .filter(p -> p.getUser().getId().equals(userId))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("玩家不在游戏中"));
        
        // 获取当前任务
        Quest currentQuest = quests.stream()
            .filter(q -> !q.getStatus().equals(QuestStatus.COMPLETED.getValue()) && 
                       !q.getStatus().equals(QuestStatus.FAILED.getValue()))
            .findFirst()
            .orElse(null);
        
        return buildGameStateResponse(game, players, quests, currentPlayer, currentQuest, 
                                    votesByQuest, resultsByQuest);
    }
    
    @Transactional(readOnly = true)
    public Map<UUID, GameStateResponse> getGameStatesForAllPlayers(UUID gameId) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));
        
        // 使用JOIN FETCH预加载用户关联
        List<GamePlayer> players = gamePlayerRepository.findByGameWithUser(game);
        List<Quest> quests = questRepository.findByGameOrderByRoundNumber(game);
        
        // 批量加载投票和任务结果
        List<Vote> allVotes = voteRepository.findByQuestsWithQuest(quests);
        List<QuestResult> allResults = questResultRepository.findByQuestsWithQuest(quests);
        
        // 将投票和结果按任务ID分组
        Map<UUID, List<Vote>> votesByQuest = allVotes.stream()
            .collect(Collectors.groupingBy(vote -> vote.getQuest().getId()));
        Map<UUID, List<QuestResult>> resultsByQuest = allResults.stream()
            .collect(Collectors.groupingBy(result -> result.getQuest().getId()));
        
        // 获取当前任务
        Quest currentQuest = quests.stream()
            .filter(q -> !q.getStatus().equals(QuestStatus.COMPLETED.getValue()) && 
                       !q.getStatus().equals(QuestStatus.FAILED.getValue()))
            .findFirst()
            .orElse(null);
        
        // 为每个玩家构建游戏状态
        Map<UUID, GameStateResponse> gameStates = new HashMap<>();
        for (GamePlayer player : players) {
            GameStateResponse state = buildGameStateResponse(game, players, quests, player, currentQuest, 
                                                          votesByQuest, resultsByQuest);
            gameStates.put(player.getUser().getId(), state);
        }
        
        return gameStates;
    }

    private GameStateResponse buildGameStateResponse(Game game, List<GamePlayer> players, 
                                                   List<Quest> quests, GamePlayer currentPlayer, 
                                                   Quest currentQuest, Map<UUID, List<Vote>> votesByQuest,
                                                   Map<UUID, List<QuestResult>> resultsByQuest) {
        GameStateResponse response = new GameStateResponse();
        response.setGameId(game.getId());
        response.setStatus(game.getStatus());
        response.setCurrentRound(game.getCurrentRound());
        response.setCurrentPhase(determineCurrentPhase(game, currentQuest));
        response.setCurrentLeaderId(currentQuest != null ? currentQuest.getLeader().getId() : null);
        
        // 构建玩家信息
        List<GameStateResponse.PlayerInfo> playerInfos = players.stream()
            .map(player -> {
                GameStateResponse.PlayerInfo info = new GameStateResponse.PlayerInfo();
                info.setPlayerId(player.getId());
                info.setUsername(player.getUser().getUsername());
                info.setRole(player.getRole());
                info.setAlignment(player.getAlignment());
                info.setIsHost(player.getIsHost());
                info.setSeatNumber(player.getSeatNumber());
                info.setIsActive(player.getIsActive());
                return info;
            })
            .collect(Collectors.toList());
        response.setPlayers(playerInfos);
        
        // 构建任务信息 - 使用预加载的数据，避免N+1查询
        List<GameStateResponse.QuestInfo> questInfos = quests.stream()
            .map(quest -> {
                GameStateResponse.QuestInfo info = new GameStateResponse.QuestInfo();
                info.setQuestId(quest.getId());
                info.setRoundNumber(quest.getRoundNumber());
                info.setRequiredPlayers(quest.getRequiredPlayers());
                info.setRequiredFails(quest.getRequiredFails());
                info.setStatus(quest.getStatus());
                info.setLeaderId(quest.getLeader() != null ? quest.getLeader().getId() : null);
                
                // 使用预加载的投票数据，避免数据库查询
                List<Vote> votes = votesByQuest.getOrDefault(quest.getId(), Collections.emptyList());
                long approveCount = votes.stream()
                    .filter(v -> v.getVoteType().equals(VoteType.APPROVE.getValue()))
                    .count();
                long rejectCount = votes.stream()
                    .filter(v -> v.getVoteType().equals(VoteType.REJECT.getValue()))
                    .count();
                
                info.setApproveCount((int) approveCount);
                info.setRejectCount((int) rejectCount);
                
                // 使用预加载的任务结果数据，避免数据库查询
                if (quest.getStatus().equals(QuestStatus.COMPLETED.getValue()) || 
                    quest.getStatus().equals(QuestStatus.FAILED.getValue())) {
                    List<QuestResult> results = resultsByQuest.getOrDefault(quest.getId(), Collections.emptyList());
                    boolean questSuccess = results.stream().allMatch(QuestResult::getSuccess);
                    info.setQuestResult(questSuccess);
                }
                
                return info;
            })
            .collect(Collectors.toList());
        response.setQuests(questInfos);
        
        // 添加角色可见性信息
        Map<String, List<String>> visibility = roleVisibilityService.getVisiblePlayers(currentPlayer, players);
        
        return response;
    }

    private String determineCurrentPhase(Game game, Quest currentQuest) {
        if (game.getStatus().equals(GameStatus.PREPARING.getValue())) {
            return "preparing";
        } else if (game.getStatus().equals(GameStatus.ENDED.getValue())) {
            return "ended";
        } else if (currentQuest == null) {
            return "completed";
        }
        
        switch (currentQuest.getStatus()) {
            case "proposing":
                return "team_building";
            case "voting":
                return "team_voting";
            case "executing":
                return "quest_execution";
            default:
                return "unknown";
        }
    }

    public boolean isPlayerTurn(UUID gameId, UUID playerId) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));
        
        Quest currentQuest = questRepository.findByGameOrderByRoundNumber(game).stream()
            .filter(q -> !q.getStatus().equals(QuestStatus.COMPLETED.getValue()) && 
                       !q.getStatus().equals(QuestStatus.FAILED.getValue()))
            .findFirst()
            .orElse(null);
        
        if (currentQuest == null) {
            return false;
        }
        
        return currentQuest.getLeader().getId().equals(playerId);
    }
}