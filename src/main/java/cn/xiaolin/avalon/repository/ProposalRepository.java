package cn.xiaolin.avalon.repository;

import cn.xiaolin.avalon.entity.Proposal;
import cn.xiaolin.avalon.entity.Quest;
import cn.xiaolin.avalon.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, UUID> {
    List<Proposal> findByQuest(Quest quest);
    List<Proposal> findByQuestAndLeader(Quest quest, User leader);
    
    @Query("SELECT p FROM Proposal p JOIN FETCH p.proposedMembers WHERE p.quest = :quest")
    List<Proposal> findByQuestWithMembers(@Param("quest") Quest quest);
}