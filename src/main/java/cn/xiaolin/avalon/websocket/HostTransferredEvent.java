package cn.xiaolin.avalon.websocket;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class HostTransferredEvent {
    private String type = "HOST_TRANSFERRED";
    private String roomId;
    private String newHost;
    private long timestamp;
}