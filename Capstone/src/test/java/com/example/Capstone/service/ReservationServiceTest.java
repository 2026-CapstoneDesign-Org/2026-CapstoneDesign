package com.example.Capstone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.Capstone.client.reservation.ReservationCallProvider;
import com.example.Capstone.client.reservation.ReservationCallStartCommand;
import com.example.Capstone.client.reservation.ReservationCallStartResult;
import com.example.Capstone.domain.ReservationStatus;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.RestaurantReservation;
import com.example.Capstone.domain.User;
import com.example.Capstone.dto.request.ApplyReservationMockResultRequest;
import com.example.Capstone.dto.request.CreateAiCallReservationRequest;
import com.example.Capstone.dto.response.ReservationResponse;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.repository.RestaurantReservationRepository;
import com.example.Capstone.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private RestaurantReservationRepository reservationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private ReservationCallProvider reservationCallProvider;

    @Test
    @DisplayName("AI 전화 예약 요청을 저장하고 Mock provider 시작 정보를 반영한다")
    void createAiCallReservationStoresReservation() {
        ReservationService reservationService = reservationService();
        User user = user(1L);
        Restaurant restaurant = restaurant(10L, "02-1234-5678");
        CreateAiCallReservationRequest request = futureRequest();

        given(userRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(user));
        given(restaurantRepository.findByIdAndIsDeletedFalseAndIsHiddenFalse(10L)).willReturn(Optional.of(restaurant));
        given(reservationRepository.existsByUserIdAndRestaurantIdAndReservationDateTimeAndStatusIn(
                eq(1L),
                eq(10L),
                any(LocalDateTime.class),
                eq(ReservationStatus.duplicateBlockingStatuses())
        )).willReturn(false);
        given(reservationRepository.save(any(RestaurantReservation.class))).willAnswer(invocation -> {
            RestaurantReservation reservation = invocation.getArgument(0);
            ReflectionTestUtils.setField(reservation, "id", 100L);
            return reservation;
        });
        given(reservationCallProvider.startCall(any(ReservationCallStartCommand.class)))
                .willReturn(new ReservationCallStartResult(
                        ReservationStatus.REQUESTED,
                        "MOCK",
                        "mock-100",
                        "MOCK_WAITING_RESULT"
                ));

        ReservationResponse response = reservationService.createAiCallReservation(1L, 10L, request);

        assertThat(response.reservationId()).isEqualTo(100L);
        assertThat(response.restaurantId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(ReservationStatus.REQUESTED);
        assertThat(response.provider()).isEqualTo("MOCK");
        assertThat(response.providerCallId()).isEqualTo("mock-100");
        assertThat(response.restaurantPhoneNumberMasked()).isEqualTo("02-1234-****");

        ArgumentCaptor<ReservationCallStartCommand> commandCaptor =
                ArgumentCaptor.forClass(ReservationCallStartCommand.class);
        then(reservationCallProvider).should().startCall(commandCaptor.capture());
        assertThat(commandCaptor.getValue().restaurantPhoneNumber()).isEqualTo("02-1234-5678");
    }

    @Test
    @DisplayName("전화번호가 없는 식당은 예약할 수 없다")
    void createAiCallReservationRejectsRestaurantWithoutPhoneNumber() {
        ReservationService reservationService = reservationService();
        given(userRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(user(1L)));
        given(restaurantRepository.findByIdAndIsDeletedFalseAndIsHiddenFalse(10L))
                .willReturn(Optional.of(restaurant(10L, null)));

        assertThatThrownBy(() -> reservationService.createAiCallReservation(1L, 10L, futureRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("전화번호가 없는 식당");

        then(reservationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("과거 시점으로 예약할 수 없다")
    void createAiCallReservationRejectsPastDateTime() {
        ReservationService reservationService = reservationService();
        given(userRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(user(1L)));
        given(restaurantRepository.findByIdAndIsDeletedFalseAndIsHiddenFalse(10L))
                .willReturn(Optional.of(restaurant(10L, "02-1234-5678")));

        CreateAiCallReservationRequest request = new CreateAiCallReservationRequest(
                LocalDate.now().minusDays(1),
                LocalTime.of(12, 0),
                2,
                null
        );

        assertThatThrownBy(() -> reservationService.createAiCallReservation(1L, 10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("과거 시점");
    }

    @Test
    @DisplayName("비정상적인 인원 수는 거부한다")
    void createAiCallReservationRejectsInvalidPartySize() {
        ReservationService reservationService = reservationService();
        given(userRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(user(1L)));
        given(restaurantRepository.findByIdAndIsDeletedFalseAndIsHiddenFalse(10L))
                .willReturn(Optional.of(restaurant(10L, "02-1234-5678")));

        CreateAiCallReservationRequest request = new CreateAiCallReservationRequest(
                LocalDate.now().plusDays(1),
                LocalTime.of(12, 0),
                0,
                null
        );

        assertThatThrownBy(() -> reservationService.createAiCallReservation(1L, 10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("예약 인원 수");
    }

    @Test
    @DisplayName("동일 사용자/식당/시간의 충돌 가능 예약은 중복 생성하지 않는다")
    void createAiCallReservationRejectsDuplicateActiveReservation() {
        ReservationService reservationService = reservationService();
        given(userRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(user(1L)));
        given(restaurantRepository.findByIdAndIsDeletedFalseAndIsHiddenFalse(10L))
                .willReturn(Optional.of(restaurant(10L, "02-1234-5678")));
        given(reservationRepository.existsByUserIdAndRestaurantIdAndReservationDateTimeAndStatusIn(
                eq(1L),
                eq(10L),
                any(LocalDateTime.class),
                eq(ReservationStatus.duplicateBlockingStatuses())
        )).willReturn(true);

        assertThatThrownBy(() -> reservationService.createAiCallReservation(1L, 10L, futureRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("내 예약 목록을 조회한다")
    void getMyReservationsReturnsOnlyCurrentUserReservations() {
        ReservationService reservationService = reservationService();
        User user = user(1L);
        RestaurantReservation reservation = reservation(100L, user, restaurant(10L, "02-1234-5678"));

        given(userRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(user));
        given(reservationRepository.findAllByUserIdOrderByReservationDateTimeDescIdDesc(1L))
                .willReturn(List.of(reservation));

        assertThat(reservationService.getMyReservations(1L).items())
                .extracting(ReservationResponse::reservationId)
                .containsExactly(100L);
    }

    @Test
    @DisplayName("예약 소유자는 REQUESTED 상태 예약을 취소할 수 있다")
    void cancelReservationCancelsRequestedReservation() {
        ReservationService reservationService = reservationService();
        User user = user(1L);
        RestaurantReservation reservation = reservation(100L, user, restaurant(10L, "02-1234-5678"));

        given(userRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(user));
        given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation));

        reservationService.cancelReservation(1L, 100L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
    }

    @Test
    @DisplayName("확정된 예약은 앱에서 바로 취소할 수 없다")
    void cancelReservationRejectsConfirmedReservation() {
        ReservationService reservationService = reservationService();
        User user = user(1L);
        RestaurantReservation reservation = reservation(100L, user, restaurant(10L, "02-1234-5678"));
        reservation.applyMockResult(ReservationStatus.CONFIRMED, "ok", "confirmed", null, "mock-100");

        given(userRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(user));
        given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancelReservation(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("취소할 수 없습니다");
    }

    @Test
    @DisplayName("관리자 Mock 결과로 예약을 확정할 수 있다")
    void applyMockResultByAdminConfirmsReservation() {
        ReservationService reservationService = reservationService();
        User user = user(1L);
        RestaurantReservation reservation = reservation(100L, user, restaurant(10L, "02-1234-5678"));

        given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation));

        ReservationResponse response = reservationService.applyMockResultByAdmin(
                100L,
                new ApplyReservationMockResultRequest(
                        ReservationStatus.CONFIRMED,
                        "예약 가능 확인",
                        "예약이 확정되었습니다.",
                        null,
                        "mock-100"
                )
        );

        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(response.aiSummary()).isEqualTo("예약 가능 확인");
        assertThat(response.resultMessage()).isEqualTo("예약이 확정되었습니다.");
    }

    @Test
    @DisplayName("terminal 상태 예약에는 Mock 결과를 다시 반영할 수 없다")
    void applyMockResultByAdminRejectsTerminalTransition() {
        ReservationService reservationService = reservationService();
        User user = user(1L);
        RestaurantReservation reservation = reservation(100L, user, restaurant(10L, "02-1234-5678"));
        reservation.applyMockResult(ReservationStatus.UNAVAILABLE, "불가", "예약 불가", null, "mock-100");

        given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.applyMockResultByAdmin(
                100L,
                new ApplyReservationMockResultRequest(
                        ReservationStatus.CONFIRMED,
                        "가능",
                        "예약 가능",
                        null,
                        "mock-100"
                )
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("허용되지 않는 예약 상태 전이");
    }

    @Test
    @DisplayName("관리자 Mock 결과 반영은 예약 소유자가 아니어도 대상 예약에 반영할 수 있다")
    void applyMockResultByAdminDoesNotRequireReservationOwner() {
        ReservationService reservationService = reservationService();
        User owner = user(1L);
        RestaurantReservation reservation = reservation(100L, owner, restaurant(10L, "02-1234-5678"));

        given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation));

        ReservationResponse response = reservationService.applyMockResultByAdmin(
                100L,
                new ApplyReservationMockResultRequest(
                        ReservationStatus.CONFIRMED,
                        "가능",
                        "예약 가능",
                        null,
                        "mock-100"
                )
        );

        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    private ReservationService reservationService() {
        return new ReservationService(
                reservationRepository,
                userRepository,
                restaurantRepository,
                reservationCallProvider
        );
    }

    private CreateAiCallReservationRequest futureRequest() {
        return new CreateAiCallReservationRequest(
                LocalDate.now().plusDays(1),
                LocalTime.of(19, 0),
                4,
                "창가 자리 요청"
        );
    }

    private User user(Long id) {
        User user = User.builder()
                .provider("KAKAO")
                .providerUserId("provider-" + id)
                .nickname("user-" + id)
                .profileImageUrl("profile")
                .role(User.Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Restaurant restaurant(Long id, String phoneNumber) {
        Restaurant restaurant = Restaurant.builder()
                .name("Restaurant " + id)
                .address("Address " + id)
                .roadAddress("Road " + id)
                .categoryName("Korean")
                .regionName("Seoul")
                .lat(new BigDecimal("37.0"))
                .lng(new BigDecimal("127.0"))
                .phoneNumber(phoneNumber)
                .build();
        ReflectionTestUtils.setField(restaurant, "id", id);
        return restaurant;
    }

    private RestaurantReservation reservation(Long id, User user, Restaurant restaurant) {
        RestaurantReservation reservation = RestaurantReservation.builder()
                .user(user)
                .restaurant(restaurant)
                .reservationDateTime(LocalDateTime.now().plusDays(1))
                .partySize(4)
                .requestNote("note")
                .restaurantPhoneNumberSnapshot(restaurant.getPhoneNumber())
                .build();
        ReflectionTestUtils.setField(reservation, "id", id);
        return reservation;
    }
}
