package com.example.Capstone.service;

import com.example.Capstone.common.enums.ScoreEvent;
import com.example.Capstone.domain.ReliabilityScore;
import com.example.Capstone.domain.User;
import com.example.Capstone.dto.response.ReliabilityScoreResponse;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.ReliabilityScoreRepository;
import com.example.Capstone.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReliabilityScoreService {

    private static final double BASE_SCORE   = 20.0;
    private static final double MAX_SCORE    = 100.0;
    private static final double DECAY_FACTOR = 0.05;

    private final ReliabilityScoreRepository scoreRepository;
    private final UserRepository userRepository;

    // 점수 증가 (희소성 보정 적용)
    @Transactional
    public void increase(Long userId, ScoreEvent event) {
        ReliabilityScore rs = getOrCreate(userId);

        double rarityCoeff = 1.0 - (rs.getScore() - BASE_SCORE) / (MAX_SCORE - BASE_SCORE);
        rarityCoeff = Math.max(0.05, rarityCoeff);

        double delta = event.getBasePoint() * rarityCoeff;
        double newActivityIndex = rs.getActivityIndex() + delta;
        double newScore = MAX_SCORE - (MAX_SCORE - BASE_SCORE)
                * Math.exp(-DECAY_FACTOR * newActivityIndex);

        rs.addActivityIndex(delta);
        rs.updateScore(newScore);

        log.info("점수 증가 - userId: {}, event: {}, delta: {}, newScore: {}",
                userId, event.getDescription(), delta, rs.getScore());
    }

    // 점수 감소 (비대칭 - 희소성 보정 없음)
    @Transactional
    public void decrease(Long userId, ScoreEvent event) {
        ReliabilityScore rs = getOrCreate(userId);
        rs.updateScore(rs.getScore() + event.getBasePoint());

        log.info("점수 감소 - userId: {}, event: {}, newScore: {}",
                userId, event.getDescription(), rs.getScore());
    }

    // 점수 조회
    public ReliabilityScoreResponse getScore(Long userId) {
        return ReliabilityScoreResponse.from(getOrCreate(userId));
    }

    // 비활동 패널티 (배치)
    @Transactional
    public void applyInactivePenalty() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        scoreRepository.findAll().stream()
                .filter(rs -> rs.getLastActivityAt().isBefore(threshold))
                .forEach(rs -> {
                    rs.updateScore(rs.getScore() + ScoreEvent.INACTIVE_PENALTY.getBasePoint());
                    log.info("비활동 패널티 적용 - userId: {}", rs.getUser().getId());
                });
    }

    // 명예 칭호 갱신 (배치)
    @Transactional
    public void refreshHonorTitles() {
        List<ReliabilityScore> ranked = scoreRepository.findAllByOrderByScoreDesc();
        long total = ranked.size();
        if (total == 0) return;

        String period = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM"));

        long top01Count = Math.max(1, (long)(total * 0.001));
        long top1Count  = Math.max(1, (long)(total * 0.01));
        long top5Count  = Math.max(1, (long)(total * 0.05));
        long top10Count = Math.max(1, (long)(total * 0.10));

        for (int i = 0; i < ranked.size(); i++) {
            ReliabilityScore rs = ranked.get(i);
            if      (i < top01Count) rs.updateHonorTitle("🏆 이번 달 Top 0.1%", period);
            else if (i < top1Count)  rs.updateHonorTitle("👑 이번 달 Top 1%",   period);
            else if (i < top5Count)  rs.updateHonorTitle("🌟 이번 달 Top 5%",   period);
            else if (i < top10Count) rs.updateHonorTitle("⭐ 이번 달 Top 10%",  period);
            else                     rs.clearHonorTitle();
        }

        log.info("명예 칭호 갱신 완료 - 총 {}명, 기간: {}", total, period);
    }

    private ReliabilityScore getOrCreate(Long userId) {
        return scoreRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findByIdAndIsDeletedFalse(userId)
                            .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다."));
                    return scoreRepository.save(ReliabilityScore.builder()
                            .user(user)
                            .build());
                });
    }
}