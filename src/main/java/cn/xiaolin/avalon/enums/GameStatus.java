package cn.xiaolin.avalon.enums;

import lombok.Getter;

@Getter
public enum GameStatus {
    PREPARING("preparing"),
    PLAYING("playing"),
    ENDED("ended");

    private final String value;

    GameStatus(String value) {
        this.value = value;
    }

}