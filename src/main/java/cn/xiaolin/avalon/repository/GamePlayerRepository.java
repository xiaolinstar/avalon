package cn.xiaolin.avalon.repository;

import cn.xiaolin.avalon.entity.GamePlayer;
import cn.xiaolin.avalon.entity.Game;
import cn.xiaolin.avalon.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GamePlayerRepository extends JpaRepository<GamePlayer, java.util.UUID> {
    List<GamePlayer> findByGame(Game game);
    
    @Query("SELECT gp FROM GamePlayer gp JOIN FETCH gp.user WHERE gp.game = :game")
    List<GamePlayer> findByGameWithUser(@Param("game") Game game);
    
    @Query("SELECT gp FROM GamePlayer gp JOIN FETCH gp.user JOIN FETCH gp.game WHERE gp.game.id = :gameId")
    List<GamePlayer> findByGameIdWithUserAndGame(@Param("gameId") UUID gameId);
    
    Optional<GamePlayer> findByGameAndUser(Game game, User user);
}