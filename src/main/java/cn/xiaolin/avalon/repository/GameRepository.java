package cn.xiaolin.avalon.repository;

import cn.xiaolin.avalon.entity.Game;
import cn.xiaolin.avalon.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GameRepository extends JpaRepository<Game, UUID> {
    Optional<Game> findByRoomId(UUID roomId);
}