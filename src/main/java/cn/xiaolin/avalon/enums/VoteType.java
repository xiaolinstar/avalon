package cn.xiaolin.avalon.enums;

import lombok.Getter;

@Getter
public enum VoteType {
    APPROVE("approve"),
    REJECT("reject");

    private final String value;

    VoteType(String value) {
        this.value = value;
    }

}