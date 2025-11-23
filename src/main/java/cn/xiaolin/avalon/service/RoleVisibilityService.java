package cn.xiaolin.avalon.service;

import cn.xiaolin.avalon.entity.GamePlayer;
import cn.xiaolin.avalon.enums.Role;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RoleVisibilityService {

    public Map<String, List<String>> getVisiblePlayers(GamePlayer viewer, List<GamePlayer> allPlayers) {
        Map<String, List<String>> visibility = new HashMap<>();
        
        // 获取观看者的角色
        Role viewerRole = getRoleByCode(viewer.getRole());
        
        switch (viewerRole) {
            case MERLIN:
                // 梅林可以看到所有邪恶阵营，除了莫德雷德
                List<String> evilPlayers = allPlayers.stream()
                    .filter(p -> p.getAlignment().equals("evil") && !p.getRole().equals("mordred"))
                    .map(p -> p.getUser().getUsername())
                    .collect(Collectors.toList());
                visibility.put("evil", evilPlayers);
                break;
                
            case PERCIVAL:
                // 派西维尔可以看到梅林和莫甘娜，但不知道谁是谁
                List<String> merlinAndMorgana = allPlayers.stream()
                    .filter(p -> p.getRole().equals("merlin") || p.getRole().equals("morgana"))
                    .map(p -> p.getUser().getUsername())
                    .collect(Collectors.toList());
                visibility.put("merlin_or_morgana", merlinAndMorgana);
                break;
                
            case MORGANA:
            case ASSASSIN:
            case MINION:
                // 邪恶阵营可以看到其他邪恶成员，除了奥伯伦
                List<String> evilTeam = allPlayers.stream()
                    .filter(p -> p.getAlignment().equals("evil") && !p.getRole().equals("oberon"))
                    .map(p -> p.getUser().getUsername())
                    .collect(Collectors.toList());
                visibility.put("evil_team", evilTeam);
                break;
                
            case MORDRED:
                // 莫德雷德可以看到其他邪恶成员，除了奥伯伦，但其他邪恶成员看不到他
                List<String> mordredTeam = allPlayers.stream()
                    .filter(p -> p.getAlignment().equals("evil") && !p.getRole().equals("oberon") && !p.getRole().equals("mordred"))
                    .map(p -> p.getUser().getUsername())
                    .collect(Collectors.toList());
                visibility.put("evil_team_except_me", mordredTeam);
                break;
                
            case OBERON:
                // 奥伯伦看不到其他邪恶成员，其他邪恶成员也看不到他
                visibility.put("no_evil_info", List.of("你看不到其他邪恶成员的信息"));
                break;
                
            default:
                // 忠臣看不到任何特殊信息
                visibility.put("no_special_info", List.of("你没有特殊视野"));
                break;
        }
        
        return visibility;
    }
    
    private Role getRoleByCode(String roleCode) {
        return Arrays.stream(Role.values())
            .filter(role -> role.getCode().equals(roleCode))
            .findFirst()
            .orElse(Role.LOYAL_SERVANT);
    }
}