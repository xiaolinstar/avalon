package cn.xiaolin.avalon.websocket;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class RoomClosedEvent {
    private String type = "ROOM_CLOSED";
    private String roomId;
    private long timestamp;
}