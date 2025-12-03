package cn.xiaolin.avalon.enums;

import lombok.Getter;

import java.util.Objects;

@Getter
public enum Role {
    // 正义阵营
    MERLIN("merlin", "梅林", "正义", "你知道邪恶阵营的所有成员，除了莫德雷德"),
    PERCIVAL("percival", "派西维尔", "正义", "你知道梅林和莫甘娜，但不知道谁是谁"),
    LOYAL_SERVANT("loyal_servant", "亚瑟的忠臣", "正义", "你是忠诚的骑士，目标是完成神圣任务"),
    
    // 邪恶阵营
    MORGANA("morgana", "莫甘娜", "邪恶", "你出现在派西维尔的视野中，看起来像梅林"),
    ASSASSIN("assassin", "刺客", "邪恶", "游戏结束时，你可以尝试刺杀梅林"),
    MORDRED("mordred", "莫德雷德", "邪恶", "梅林不知道你的身份"),
    MINION("minion", "间谍", "邪恶", "你是邪恶阵营的普通成员"),
    OBERON("oberon", "奥伯伦", "邪恶", "其他邪恶成员不知道你的身份，你也不知道他们");

    private final String code;
    private final String name;
    private final String alignment;
    private final String description;

    Role(String code, String name, String alignment, String description) {
        this.code = code;
        this.name = name;
        this.alignment = alignment;
        this.description = description;
    }

    public boolean isGood() {
        return Objects.equals("正义", alignment);
    }

    public boolean isEvil() {
        return Objects.equals("邪恶", alignment);
    }
}