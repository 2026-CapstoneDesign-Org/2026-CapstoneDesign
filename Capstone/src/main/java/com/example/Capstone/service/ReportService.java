package com.example.Capstone.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.domain.Report;
import com.example.Capstone.domain.User;
import com.example.Capstone.dto.request.CreateReportRequest;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.ReportRepository;
import com.example.Capstone.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    // 수동 신고
    @Transactional
    public void report(Long reporterId, CreateReportRequest request) {

        // 중복 신고 방지
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                reporterId, request.targetType(), request.targetId())) {
            throw new BusinessException("이미 신고한 대상입니다.", HttpStatus.BAD_REQUEST);
        }

        User reporter = userRepository.findByIdAndIsDeletedFalse(reporterId)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다."));

        reportRepository.save(Report.manualReport()
                .reporter(reporter)
                .targetType(request.targetType())
                .targetId(request.targetId())
                .reason(request.reason())
                .build());
    }
}
