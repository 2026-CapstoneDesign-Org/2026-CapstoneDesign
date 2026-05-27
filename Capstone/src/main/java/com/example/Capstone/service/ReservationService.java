package com.example.Capstone.service;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.client.reservation.ReservationCallProvider;
import com.example.Capstone.client.reservation.ReservationCallStartCommand;
import com.example.Capstone.domain.ReservationStatus;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.RestaurantReservation;
import com.example.Capstone.domain.User;
import com.example.Capstone.dto.request.ApplyReservationMockResultRequest;
import com.example.Capstone.dto.request.CreateAiCallReservationRequest;
import com.example.Capstone.dto.response.ReservationListResponse;
import com.example.Capstone.dto.response.ReservationResponse;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.repository.RestaurantReservationRepository;
import com.example.Capstone.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private static final int MAX_PARTY_SIZE = 20;

    private final RestaurantReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final ReservationCallProvider reservationCallProvider;

    @Transactional
    public ReservationResponse createAiCallReservation(
            Long userId,
            Long restaurantId,
            CreateAiCallReservationRequest request
    ) {
        User user = getActiveUser(userId);
        Restaurant restaurant = getVisibleRestaurant(restaurantId);
        validateRestaurantPhoneNumber(restaurant);
        validateReservationDateTimeFields(request);
        validatePartySize(request.partySize());

        LocalDateTime reservationDateTime = LocalDateTime.of(
                request.reservationDate(),
                request.reservationTime()
        );
        validateFutureReservationDateTime(reservationDateTime);
        validateNoDuplicate(user.getId(), restaurant.getId(), reservationDateTime);

        RestaurantReservation reservation = RestaurantReservation.builder()
                .user(user)
                .restaurant(restaurant)
                .reservationDateTime(reservationDateTime)
                .partySize(request.partySize())
                .requestNote(request.requestNote())
                .restaurantPhoneNumberSnapshot(restaurant.getPhoneNumber())
                .build();

        RestaurantReservation savedReservation = reservationRepository.save(reservation);
        var startResult = reservationCallProvider.startCall(toStartCommand(savedReservation));
        savedReservation.applyProviderStart(
                startResult.status(),
                startResult.provider(),
                startResult.providerCallId(),
                startResult.providerStatus()
        );

        return ReservationResponse.from(savedReservation);
    }

    public ReservationListResponse getMyReservations(Long userId) {
        User user = getActiveUser(userId);
        return new ReservationListResponse(
                reservationRepository.findAllByUserIdOrderByReservationDateTimeDescIdDesc(user.getId())
                        .stream()
                        .map(ReservationResponse::from)
                        .toList()
        );
    }

    public ReservationResponse getReservation(Long userId, Long reservationId) {
        return ReservationResponse.from(getOwnedReservation(userId, reservationId));
    }

    @Transactional
    public void cancelReservation(Long userId, Long reservationId) {
        RestaurantReservation reservation = getOwnedReservation(userId, reservationId);
        try {
            reservation.cancel();
        } catch (IllegalStateException exception) {
            throw new BusinessException(exception.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public ReservationResponse applyMockResultByAdmin(
            Long reservationId,
            ApplyReservationMockResultRequest request
    ) {
        RestaurantReservation reservation = getReservationById(reservationId);
        validateMockResultStatus(request.status());
        try {
            reservation.applyMockResult(
                    request.status(),
                    request.aiSummary(),
                    request.resultMessage(),
                    request.failureReason(),
                    request.providerCallId()
            );
        } catch (IllegalStateException exception) {
            throw new BusinessException(exception.getMessage(), HttpStatus.BAD_REQUEST);
        }
        return ReservationResponse.from(reservation);
    }

    private User getActiveUser(Long userId) {
        if (userId == null) {
            throw new BusinessException("인증이 필요합니다.", HttpStatus.UNAUTHORIZED);
        }
        return userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다."));
    }

    private Restaurant getVisibleRestaurant(Long restaurantId) {
        return restaurantRepository.findByIdAndIsDeletedFalseAndIsHiddenFalse(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("식당을 찾을 수 없습니다."));
    }

    private RestaurantReservation getOwnedReservation(Long userId, Long reservationId) {
        getActiveUser(userId);
        RestaurantReservation reservation = getReservationById(reservationId);
        if (!reservation.getUser().getId().equals(userId)) {
            throw new SecurityException("예약을 조회하거나 변경할 권한이 없습니다.");
        }
        return reservation;
    }

    private RestaurantReservation getReservationById(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다."));
    }

    private void validateRestaurantPhoneNumber(Restaurant restaurant) {
        if (restaurant.getPhoneNumber() == null || restaurant.getPhoneNumber().isBlank()) {
            throw new BusinessException("전화번호가 없는 식당은 AI 전화 예약을 요청할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private void validatePartySize(Integer partySize) {
        if (partySize == null || partySize < 1 || partySize > MAX_PARTY_SIZE) {
            throw new BusinessException("예약 인원 수는 1명 이상 20명 이하이어야 합니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateReservationDateTimeFields(CreateAiCallReservationRequest request) {
        if (request.reservationDate() == null || request.reservationTime() == null) {
            throw new BusinessException("예약 날짜와 시간은 필수입니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateFutureReservationDateTime(LocalDateTime reservationDateTime) {
        if (reservationDateTime == null || !reservationDateTime.isAfter(LocalDateTime.now())) {
            throw new BusinessException("과거 시점으로는 예약할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateNoDuplicate(Long userId, Long restaurantId, LocalDateTime reservationDateTime) {
        if (reservationRepository.existsByUserIdAndRestaurantIdAndReservationDateTimeAndStatusIn(
                userId,
                restaurantId,
                reservationDateTime,
                ReservationStatus.duplicateBlockingStatuses()
        )) {
            throw new BusinessException("같은 식당과 시간에 이미 진행 중인 예약이 있습니다.", HttpStatus.CONFLICT);
        }
    }

    private void validateMockResultStatus(ReservationStatus status) {
        if (status == null || status == ReservationStatus.REQUESTED || status == ReservationStatus.CANCELED) {
            throw new BusinessException("Mock 결과로 반영할 수 없는 예약 상태입니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private ReservationCallStartCommand toStartCommand(RestaurantReservation reservation) {
        return new ReservationCallStartCommand(
                reservation.getId(),
                reservation.getRestaurant().getId(),
                reservation.getRestaurant().getName(),
                reservation.getRestaurantPhoneNumberSnapshot(),
                reservation.getReservationDateTime(),
                reservation.getPartySize(),
                reservation.getRequestNote()
        );
    }
}
