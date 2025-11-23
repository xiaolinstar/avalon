package cn.xiaolin.avalon.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerInfoResponse {
    private UUID playerId;
    private String username;
    private String role;
    private String alignment;
    private Boolean isHost;
    private Integer seatNumber;
    private Boolean isActive;
}