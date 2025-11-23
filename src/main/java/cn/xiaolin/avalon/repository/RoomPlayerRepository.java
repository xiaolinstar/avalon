package cn.xiaolin.avalon.repository;

import cn.xiaolin.avalon.entity.RoomPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomPlayerRepository extends JpaRepository<RoomPlayer, UUID> {
    
    List<RoomPlayer> findByRoomIdAndIsActiveTrue(UUID roomId);
    
    Optional<RoomPlayer> findByRoomIdAndUserId(UUID roomId, UUID userId);
    
    boolean existsByRoomIdAndUserId(UUID roomId, UUID userId);
    
    boolean existsByRoomIdAndUserIdAndIsActiveTrue(UUID roomId, UUID userId);
    
    @Query("SELECT rp FROM RoomPlayer rp JOIN FETCH rp.user WHERE rp.room.roomCode = :roomCode AND rp.isActive = true")
    List<RoomPlayer> findActivePlayersByRoomCode(@Param("roomCode") String roomCode);
    
    @Query("SELECT rp FROM RoomPlayer rp JOIN FETCH rp.user JOIN FETCH rp.room WHERE rp.room.roomCode = :roomCode AND rp.isActive = true")
    List<RoomPlayer> findActivePlayersWithRoomByRoomCode(@Param("roomCode") String roomCode);
    
    @Query("SELECT COUNT(rp) FROM RoomPlayer rp WHERE rp.room.id = :roomId AND rp.isActive = true")
    long countActivePlayersByRoomId(@Param("roomId") UUID roomId);
}