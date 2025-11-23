package cn.xiaolin.avalon.enums;

import lombok.Getter;

@Getter
public enum Alignment {
    GOOD("good"),
    EVIL("evil");

    private final String value;

    Alignment(String value) {
        this.value = value;
    }

}