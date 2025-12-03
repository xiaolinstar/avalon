package cn.xiaolin.avalon.service;

import cn.xiaolin.avalon.dto.AssassinationRequest;
import cn.xiaolin.avalon.entity.*;
import cn.xiaolin.avalon.enums.*;
import cn.xiaolin.avalon.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssassinationService {
    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final UserRepository userRepository;

    @Transactional
    public boolean processAssassination(UUID gameId, UUID assassinId, AssassinationRequest request) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));
        
        // 验证游戏状态
        if (!Objects.equals(game.getStatus(), GameStatus.ENDED.getValue())) {
            throw new RuntimeException("游戏还未结束");
        }
        
        // 验证获胜阵营
        if (!Objects.equals("good", game.getWinner())) {
            throw new RuntimeException("正义阵营未获胜，无法进行刺杀");
        }
        
        // 验证刺客身份
        GamePlayer assassin = gamePlayerRepository.findByGameAndUser(game, userRepository.findById(assassinId).orElseThrow())
            .orElseThrow(() -> new RuntimeException("刺客不在游戏中"));
        
        if (!Objects.equals(assassin.getRole(), "assassin")) {
            throw new RuntimeException("只有刺客可以进行刺杀");
        }
        
        // 获取目标玩家
        GamePlayer target = gamePlayerRepository.findById(request.getTargetPlayerId())
            .orElseThrow(() -> new RuntimeException("目标玩家不存在"));
        
        // 验证目标是否是梅林
        boolean isTargetMerlin = Objects.equals(target.getRole(), "merlin");
        
        // 更新游戏结果
        if (isTargetMerlin) {
            // 刺杀成功，邪恶阵营获胜
            game.setWinner("evil");
            game.setGameConfig(updateGameConfig(game.getGameConfig(), true, target.getId()));
        } else {
            // 刺杀失败，正义阵营仍然获胜
            game.setGameConfig(updateGameConfig(game.getGameConfig(), false, target.getId()));
        }
        
        gameRepository.save(game);
        
        return isTargetMerlin;
    }

    private String updateGameConfig(String gameConfig, boolean assassinationSuccess, UUID targetId) {
        // 简化处理，实际应该解析和更新JSON配置
        return String.format("{\"assassination\":{\"success\":%b,\"target\":\"%s\"}}", 
                           assassinationSuccess, targetId);
    }

    public boolean isAssassinationPhase(UUID gameId) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));
        
        return Objects.equals(game.getStatus(), GameStatus.ENDED.getValue()) && 
               Objects.equals("good", game.getWinner());
    }

    public List<GamePlayer> getAssassinationCandidates(UUID gameId) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new RuntimeException("游戏不存在"));
        
        // 返回所有正义阵营的玩家作为刺杀候选
        return gamePlayerRepository.findByGame(game).stream()
            .filter(p -> Objects.equals(p.getAlignment(), "good"))
            .collect(java.util.stream.Collectors.toList());
    }
}