package cn.xiaolin.avalon.repository;

import cn.xiaolin.avalon.entity.Vote;
import cn.xiaolin.avalon.entity.Game;
import cn.xiaolin.avalon.entity.Quest;
import cn.xiaolin.avalon.entity.Proposal;
import cn.xiaolin.avalon.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VoteRepository extends JpaRepository<Vote, UUID> {
    // 保留原来的方法以确保向后兼容性
    List<Vote> findByQuest(Quest quest);
    boolean existsByQuestAndPlayer(Quest quest, User player);
    
    // 新增的方法
    List<Vote> findByProposal(Proposal proposal);
    boolean existsByProposalAndPlayer(Proposal proposal, User player);
    
    @Query("SELECT v FROM Vote v WHERE v.quest.game = :game")
    List<Vote> findByGame(@Param("game") Game game);
    
    @Query("SELECT v FROM Vote v JOIN FETCH v.quest WHERE v.quest IN :quests")
    List<Vote> findByQuestsWithQuest(@Param("quests") List<cn.xiaolin.avalon.entity.Quest> quests);
    
    @Query("SELECT v FROM Vote v JOIN FETCH v.proposal WHERE v.proposal IN :proposals")
    List<Vote> findByProposalsWithProposal(@Param("proposals") List<cn.xiaolin.avalon.entity.Proposal> proposals);
}