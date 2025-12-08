package cn.xiaolin.avalon.service;

import cn.xiaolin.avalon.dto.VoteRequest;
import cn.xiaolin.avalon.entity.*;
import cn.xiaolin.avalon.enums.QuestStatus;
import cn.xiaolin.avalon.enums.VoteType;
import cn.xiaolin.avalon.repository.*;
import cn.xiaolin.avalon.websocket.GameMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class VoteService {
    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final QuestRepository questRepository;
    private final VoteRepository voteRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

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
}