package cn.xiaolin.avalon.service;

import cn.xiaolin.avalon.dto.GameStatisticsResponse;
import cn.xiaolin.avalon.entity.*;
import cn.xiaolin.avalon.enums.VoteType;
import cn.xiaolin.avalon.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameStatisticsService {
    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final QuestRepository questRepository;
    private final VoteRepository voteRepository;
    private final QuestResultRepository questResultRepository;

    public GameStatisticsResponse getGameStatistics(UUID gameId) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));

        List<GamePlayer> players = gamePlayerRepository.findByGame(game);
        List<Quest> quests = questRepository.findByGameOrderByRoundNumber(game);
        List<Vote> votes = voteRepository.findByGame(game);
        List<QuestResult> questResults = questResultRepository.findByGame(game);

        GameStatisticsResponse response = new GameStatisticsResponse();
        response.setGameId(game.getId());
        response.setWinner(game.getWinner());
        response.setWinType(determineWinType(game, quests));
        response.setTotalRounds(quests.size());
        response.setSuccessfulQuests((int) quests.stream().filter(q -> q.getStatus().equals("completed")).count());
        response.setFailedQuests((int) quests.stream().filter(q -> q.getStatus().equals("failed")).count());
        response.setStartedAt(game.getStartedAt());
        response.setEndedAt(game.getEndedAt());
        
        if (game.getStartedAt() != null && game.getEndedAt() != null) {
            response.setDurationMinutes(Duration.between(game.getStartedAt(), game.getEndedAt()).toMinutes());
        }

        // 玩家统计
        Map<UUID, List<Vote>> playerVotes = votes.stream()
            .collect(Collectors.groupingBy(v -> v.getPlayer().getId()));
        Map<UUID, List<QuestResult>> playerQuestResults = questResults.stream()
            .collect(Collectors.groupingBy(r -> r.getPlayer().getId()));

        List<GameStatisticsResponse.PlayerStatistics> playerStats = players.stream()
            .map(player -> {
                GameStatisticsResponse.PlayerStatistics stats = new GameStatisticsResponse.PlayerStatistics();
                stats.setPlayerId(player.getId());
                stats.setUsername(player.getUser().getUsername());
                stats.setRole(player.getRole());
                stats.setAlignment(player.getAlignment());
                stats.setIsHost(player.getIsHost());

                // 投票统计
                List<Vote> playerVoteList = playerVotes.getOrDefault(player.getUser().getId(), List.of());
                stats.setVoteCount(playerVoteList.size());
                stats.setApproveVotes((int) playerVoteList.stream()
                    .filter(v -> v.getVoteType().equals(VoteType.APPROVE.getValue())).count());
                stats.setRejectVotes((int) playerVoteList.stream()
                    .filter(v -> v.getVoteType().equals(VoteType.REJECT.getValue())).count());

                // 任务统计
                List<QuestResult> playerQuestList = playerQuestResults.getOrDefault(player.getUser().getId(), List.of());
                stats.setQuestParticipations(playerQuestList.size());
                stats.setQuestSuccesses((int) playerQuestList.stream().filter(QuestResult::getSuccess).count());
                stats.setQuestFailures((int) playerQuestList.stream().filter(r -> !r.getSuccess()).count());

                // 刺杀相关
                stats.setWasAssassinated(player.getRole().equals("merlin") && 
                    game.getGameConfig() != null && game.getGameConfig().contains("assassination"));
                stats.setWasAssassin(player.getRole().equals("assassin"));

                // 胜率计算
                boolean playerWon = player.getAlignment().equals(game.getWinner());
                stats.setWinRate(playerWon ? 100.0 : 0.0);

                return stats;
            })
            .collect(Collectors.toList());

        response.setPlayerStatistics(playerStats);

        // 任务统计
        List<GameStatisticsResponse.QuestStatistics> questStats = quests.stream()
            .map(quest -> {
                GameStatisticsResponse.QuestStatistics stats = new GameStatisticsResponse.QuestStatistics();
                stats.setQuestId(quest.getId());
                stats.setRoundNumber(quest.getRoundNumber());
                stats.setStatus(quest.getStatus());
                stats.setRequiredPlayers(quest.getRequiredPlayers());
                stats.setRequiredFails(quest.getRequiredFails());

                List<Vote> questVotes = votes.stream()
                    .filter(v -> v.getQuest().getId().equals(quest.getId()))
                    .collect(Collectors.toList());
                stats.setApproveVotes((int) questVotes.stream()
                    .filter(v -> v.getVoteType().equals(VoteType.APPROVE.getValue())).count());
                stats.setRejectVotes((int) questVotes.stream()
                    .filter(v -> v.getVoteType().equals(VoteType.REJECT.getValue())).count());

                if (quest.getLeader() != null) {
                    stats.setLeaderName(quest.getLeader().getUsername());
                }

                // 简化的队伍成员和结果
                stats.setTeamMembers(List.of()); // 需要根据实际数据填充
                stats.setQuestResults(List.of()); // 需要根据实际数据填充

                // 任务结果
                if (quest.getStatus().equals("completed")) {
                    stats.setResult(true);
                } else if (quest.getStatus().equals("failed")) {
                    stats.setResult(false);
                }

                return stats;
            })
            .collect(Collectors.toList());

        response.setQuestStatistics(questStats);

        return response;
    }

    private String determineWinType(Game game, List<Quest> quests) {
        if (game.getWinner() == null) {
            return "unknown";
        }

        if (game.getWinner().equals("good")) {
            // 检查是否是任务胜利
            long successfulQuests = quests.stream().filter(q -> q.getStatus().equals("completed")).count();
            if (successfulQuests >= 3) {
                return "quest_victory";
            }
        } else if (game.getWinner().equals("evil")) {
            // 检查是否是任务失败胜利
            long failedQuests = quests.stream().filter(q -> q.getStatus().equals("failed")).count();
            if (failedQuests >= 3) {
                return "quest_failure";
            }
            
            // 检查是否是刺杀胜利
            if (game.getGameConfig() != null && game.getGameConfig().contains("assassination")) {
                return "assassination_victory";
            }
        }

        return "unknown";
    }
}