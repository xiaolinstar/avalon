package cn.xiaolin.avalon.repository;

import cn.xiaolin.avalon.entity.Vote;
import cn.xiaolin.avalon.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VoteRepository extends JpaRepository<Vote, java.util.UUID> {
    List<Vote> findByQuest(cn.xiaolin.avalon.entity.Quest quest);
    boolean existsByQuestAndPlayer(cn.xiaolin.avalon.entity.Quest quest, cn.xiaolin.avalon.entity.User player);
    
    @Query("SELECT v FROM Vote v WHERE v.quest.game = :game")
    List<Vote> findByGame(@Param("game") Game game);
    
    @Query("SELECT v FROM Vote v JOIN FETCH v.quest WHERE v.quest IN :quests")
    List<Vote> findByQuestsWithQuest(@Param("quests") List<cn.xiaolin.avalon.entity.Quest> quests);
}