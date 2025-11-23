package cn.xiaolin.avalon.repository;

import cn.xiaolin.avalon.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {
    Optional<Room> findByRoomCode(String roomCode);
    
    @Query("SELECT r FROM Room r JOIN FETCH r.creator WHERE r.roomCode = :roomCode")
    Optional<Room> findByRoomCodeWithCreator(@Param("roomCode") String roomCode);
}