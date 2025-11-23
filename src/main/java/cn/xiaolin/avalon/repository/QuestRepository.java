package cn.xiaolin.avalon.repository;

import cn.xiaolin.avalon.entity.Quest;
import cn.xiaolin.avalon.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestRepository extends JpaRepository<Quest, java.util.UUID> {
    List<Quest> findByGame(Game game);
    List<Quest> findByGameOrderByRoundNumber(Game game);
}