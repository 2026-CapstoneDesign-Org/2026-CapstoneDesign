package com.example.Capstone.common.scheduler;

import com.example.Capstone.service.ReliabilityScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReliabilityScoreScheduler {

    private final ReliabilityScoreService scoreService;

    @Scheduled(cron = "0 0 0 * * *")
    public void applyInactivePenalty() {
        log.info("비활동 패널티 배치 시작");
        scoreService.applyInactivePenalty();
    }

    @Scheduled(cron = "0 0 0 1 * *")
    public void refreshHonorTitles() {
        log.info("명예 칭호 갱신 배치 시작");
        scoreService.refreshHonorTitles();
    }
}