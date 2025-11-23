package cn.xiaolin.avalon.websocket;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameMessage {
    private String type;
    private UUID gameId;
    private UUID roomId;
    private UUID userId;
    private String content;
    private String sender;
    private Long timestamp;
}