package cn.xiaolin.avalon.enums;

import lombok.Getter;

@Getter
public enum QuestStatus {
    PROPOSING("proposing"),
    VOTING("voting"),
    EXECUTING("executing"),
    COMPLETED("completed"),
    FAILED("failed");

    private final String value;

    QuestStatus(String value) {
        this.value = value;
    }

}