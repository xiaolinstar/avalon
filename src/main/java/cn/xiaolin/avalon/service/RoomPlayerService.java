package cn.xiaolin.avalon.service;

import cn.xiaolin.avalon.dto.JoinRoomRequest;
import cn.xiaolin.avalon.dto.RoomPlayersResponse;
import cn.xiaolin.avalon.dto.PlayerInfoResponse;
import cn.xiaolin.avalon.entity.Room;
import cn.xiaolin.avalon.entity.RoomPlayer;
import cn.xiaolin.avalon.entity.User;
import cn.xiaolin.avalon.repository.RoomPlayerRepository;
import cn.xiaolin.avalon.repository.RoomRepository;
import cn.xiaolin.avalon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomPlayerService {
    private final RoomPlayerRepository roomPlayerRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    @Transactional
    public RoomPlayer joinRoom(UUID userId, JoinRoomRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        Room room = roomRepository.findByRoomCode(request.getRoomCode())
                .orElseThrow(() -> new RuntimeException("房间不存在"));

        // Check if room is closed
        if ("closed".equals(room.getStatus())) {
            throw new RuntimeException("房间已关闭");
        }

        // Check if user is already in the room and active
        if (roomPlayerRepository.existsByRoomIdAndUserIdAndIsActiveTrue(room.getId(), userId)) {
            throw new RuntimeException("您已经在该房间中");
        }

        // Check if room is full
        long currentPlayerCount = roomPlayerRepository.countActivePlayersByRoomId(room.getId());
        if (currentPlayerCount >= room.getMaxPlayers()) {
            throw new RuntimeException("房间已满");
        }

        // Check if user has previously joined and left the room
        Optional<RoomPlayer> existingPlayerOpt = roomPlayerRepository.findByRoomIdAndUserId(room.getId(), userId);
        RoomPlayer roomPlayer;

        if (existingPlayerOpt.isPresent()) {
            // Player has previously joined and left, reactivate them
            roomPlayer = existingPlayerOpt.get();
            roomPlayer.setIsActive(true);
            // Keep existing seat number
        } else {
            // New player, create new RoomPlayer entry
            roomPlayer = new RoomPlayer();
            roomPlayer.setRoom(room);
            roomPlayer.setUser(user);
            roomPlayer.setIsHost(false); // Joining players are not hosts
            roomPlayer.setIsActive(true);
            roomPlayer.setSeatNumber((int) (currentPlayerCount + 1));
        }

        return roomPlayerRepository.save(roomPlayer);
    }

    @Transactional
    public RoomPlayersResponse leaveRoom(UUID userId, String roomCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("房间不存在"));

        // Check if user is in the room
        RoomPlayer leavingPlayer = roomPlayerRepository.findByRoomIdAndUserId(room.getId(), userId)
                .orElseThrow(() -> new RuntimeException("您不在该房间中"));

        // Check if the leaving player is the host
        boolean wasHost = leavingPlayer.getIsHost();
        String newHostUsername = null;

        // Mark player as inactive instead of deleting
        leavingPlayer.setIsActive(false);
        roomPlayerRepository.save(leavingPlayer);

        // Get remaining active players
        List<RoomPlayer> remainingPlayers = roomPlayerRepository.findByRoomIdAndIsActiveTrue(room.getId());

        // If no players left, update room status
        boolean roomClosed = false;
        if (remainingPlayers.isEmpty()) {
            room.setStatus("closed");
            roomRepository.save(room);
            roomClosed = true;
        }

        // If the leaving player was the host, transfer host to the player with the smallest seat number
        if (wasHost && !remainingPlayers.isEmpty()) {
            // Find the player with the smallest seat number
            Optional<RoomPlayer> newHost = remainingPlayers.stream()
                    .min(Comparator.comparing(RoomPlayer::getSeatNumber));

            if (newHost.isPresent()) {
                RoomPlayer newHostPlayer = newHost.get();
                newHostPlayer.setIsHost(true);
                roomPlayerRepository.save(newHostPlayer);
                newHostUsername = newHostPlayer.getUser().getUsername();
            }
        }

        // Convert remaining players to response format
        List<PlayerInfoResponse> players = remainingPlayers.stream()
                .map(rp -> new PlayerInfoResponse(
                        rp.getUser().getId(),
                        rp.getUser().getUsername(),
                        "unknown",
                        "unknown",
                        rp.getIsHost(),
                        rp.getSeatNumber(),
                        rp.getIsActive()
                ))
                .collect(Collectors.toList());

        // Store room closed and new host info in the response for the controller to use
        RoomPlayersResponse response = new RoomPlayersResponse(roomCode, players);
        // We'll use the room code to indicate special conditions:
        // - "CLOSED" prefix means room was closed
        // - "HOST:" prefix followed by username means host was transferred
        if (roomClosed) {
            response.setRoomCode("CLOSED:" + roomCode);
        } else if (newHostUsername != null) {
            response.setRoomCode("HOST:" + newHostUsername + ":" + roomCode);
        }

        return response;
    }

    @Transactional
    public RoomPlayersResponse leaveRoomByRoomPlayerId(UUID userId, UUID roomPlayerId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 通过roomPlayerId查找RoomPlayer
        RoomPlayer leavingPlayer = roomPlayerRepository.findById(roomPlayerId)
                .orElseThrow(() -> new RuntimeException("房间玩家关系不存在"));

        // 验证用户是否有权限操作此RoomPlayer
        if (!leavingPlayer.getUser().getId().equals(userId)) {
            throw new RuntimeException("您无权操作此房间玩家关系");
        }

        // 获取房间信息
        Room room = leavingPlayer.getRoom();

        // 检查玩家是否已经在房间中（活跃状态）
        if (!leavingPlayer.getIsActive()) {
            throw new RuntimeException("您已不在该房间中");
        }

        // Check if the leaving player is the host
        boolean wasHost = leavingPlayer.getIsHost();
        String newHostUsername = null;

        // Mark player as inactive instead of deleting
        leavingPlayer.setIsActive(false);
        roomPlayerRepository.save(leavingPlayer);

        // Get remaining active players
        List<RoomPlayer> remainingPlayers = roomPlayerRepository.findByRoomIdAndIsActiveTrue(room.getId());

        // If no players left, update room status
        boolean roomClosed = false;
        if (remainingPlayers.isEmpty()) {
            room.setStatus("closed");
            roomRepository.save(room);
            roomClosed = true;
        }

        // If the leaving player was the host, transfer host to the player with the smallest seat number
        if (wasHost && !remainingPlayers.isEmpty()) {
            // Find the player with the smallest seat number
            Optional<RoomPlayer> newHost = remainingPlayers.stream()
                    .min(Comparator.comparing(RoomPlayer::getSeatNumber));

            if (newHost.isPresent()) {
                RoomPlayer newHostPlayer = newHost.get();
                newHostPlayer.setIsHost(true);
                roomPlayerRepository.save(newHostPlayer);
                newHostUsername = newHostPlayer.getUser().getUsername();
            }
        }

        // Convert remaining players to response format
        List<PlayerInfoResponse> players = remainingPlayers.stream()
                .map(rp -> new PlayerInfoResponse(
                        rp.getUser().getId(),
                        rp.getUser().getUsername(),
                        "unknown",
                        "unknown",
                        rp.getIsHost(),
                        rp.getSeatNumber(),
                        rp.getIsActive()
                ))
                .collect(Collectors.toList());

        // Store room closed and new host info in the response for the controller to use
        RoomPlayersResponse response = new RoomPlayersResponse(room.getRoomCode(), players);
        // We'll use the room code to indicate special conditions:
        // - "CLOSED" prefix means room was closed
        // - "HOST:" prefix followed by username means host was transferred
        if (roomClosed) {
            response.setRoomCode("CLOSED:" + room.getRoomCode());
        } else if (newHostUsername != null) {
            response.setRoomCode("HOST:" + newHostUsername + ":" + room.getRoomCode());
        }

        return response;
    }
}