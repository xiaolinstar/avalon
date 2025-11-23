package cn.xiaolin.avalon.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomPlayersResponse {
    private String roomCode;
    private List<PlayerInfoResponse> players;
}