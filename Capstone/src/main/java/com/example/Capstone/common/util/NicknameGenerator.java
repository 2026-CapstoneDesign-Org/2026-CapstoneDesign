package com.example.Capstone.common.util;

import java.util.Random;

import org.springframework.stereotype.Component;

@Component
public class NicknameGenerator {
    private static final String[] ADJECTIVES = {
        "행복한", "즐거운", "신나는", "귀여운", "멋진",
        "용감한", "친절한", "씩씩한", "유쾌한", "따뜻한"
    };

    private static final String[] NOUNS = {
        "고양이", "강아지", "토끼", "판다", "여우",
        "햄스터", "다람쥐", "펭귄", "북극곰", "코알라"
    };

    private final Random random = new Random();

    public String generate() {
        String adjective = ADJECTIVES[random.nextInt(ADJECTIVES.length)];
        String noun      = NOUNS[random.nextInt(NOUNS.length)];
        int number       = random.nextInt(9000) + 1000;
        return adjective + noun + number;
    }
}
