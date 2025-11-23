package cn.xiaolin.avalon.enums;

import lombok.Getter;

@Getter
public enum RoomStatus {
    WAITING("waiting"),
    PLAYING("playing"),
    ENDED("ended");

    private final String value;

    RoomStatus(String value) {
        this.value = value;
    }

}