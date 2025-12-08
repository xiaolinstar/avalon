package cn.xiaolin.avalon.service;

import cn.xiaolin.avalon.dto.ProposeTeamRequest;
import cn.xiaolin.avalon.entity.*;
import cn.xiaolin.avalon.enums.QuestStatus;
import cn.xiaolin.avalon.repository.*;
import cn.xiaolin.avalon.websocket.GameMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProposalService {
    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final QuestRepository questRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    
    @PersistenceContext
    private EntityManager entityManager;

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
}