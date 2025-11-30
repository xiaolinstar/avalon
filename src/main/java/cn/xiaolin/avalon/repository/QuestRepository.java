package cn.xiaolin.avalon.repository;

import cn.xiaolin.avalon.entity.Quest;
import cn.xiaolin.avalon.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuestRepository extends JpaRepository<Quest, UUID> {
    List<Quest> findByGame(Game game);
    List<Quest> findByGameOrderByRoundNumber(Game game);
}