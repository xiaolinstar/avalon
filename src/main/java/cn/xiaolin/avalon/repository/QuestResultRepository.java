package cn.xiaolin.avalon.repository;

import cn.xiaolin.avalon.entity.QuestResult;
import cn.xiaolin.avalon.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuestResultRepository extends JpaRepository<QuestResult, java.util.UUID> {
    List<QuestResult> findByQuest(cn.xiaolin.avalon.entity.Quest quest);
    
    @Query("SELECT qr FROM QuestResult qr WHERE qr.quest.game = :game")
    List<QuestResult> findByGame(@Param("game") Game game);
    
    @Query("SELECT qr FROM QuestResult qr JOIN FETCH qr.quest WHERE qr.quest IN :quests")
    List<QuestResult> findByQuestsWithQuest(@Param("quests") List<cn.xiaolin.avalon.entity.Quest> quests);
}