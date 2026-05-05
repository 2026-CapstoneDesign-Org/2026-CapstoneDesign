package com.example.Capstone.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.client.PcmapSearchClient;
import com.example.Capstone.client.PcmapSearchClient.PcmapRestaurantCandidate;
import com.example.Capstone.common.enums.ScoreEvent;
import com.example.Capstone.domain.ListRestaurant;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.User;
import com.example.Capstone.domain.UserList;
import com.example.Capstone.dto.request.AddExternalRestaurantRequest;
import com.example.Capstone.dto.request.AddRestaurantRequest;
import com.example.Capstone.dto.request.CreateListRequest;
import com.example.Capstone.dto.request.UpdateListRequest;
import com.example.Capstone.dto.request.UpdateScoreRequest;
import com.example.Capstone.dto.response.UserListDetailResponse;
import com.example.Capstone.dto.response.UserListResponse;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.ListRestaurantRepository;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.repository.UserListRepository;
import com.example.Capstone.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserListService {

    private static final int MIN_RESTAURANT_COUNT = 5;

    private final UserListRepository userListRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final ListRestaurantRepository listRestaurantRepository;
    private final PcmapSearchClient pcmapSearchClient;
    private final ReliabilityScoreService reliabilityScoreService;

	// 리스트 생성
	@Transactional
    public UserListResponse createList(Long userId, CreateListRequest request) {
        validateMinimumRestaurantCount(request.restaurants());
        validateNoDuplicateRestaurants(request.restaurants());
        List<Restaurant> initialRestaurants = validateAndLoadRestaurants(
                request.regionName(),
                request.restaurants()
        );

        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다."));

        UserList userList = UserList.builder()
                .user(user)
                .title(request.title())
                .description(request.description())
                .regionName(request.regionName())
                .build();

        UserList savedList = userListRepository.save(userList);

        List<ListRestaurant> restaurants = new ArrayList<>();
        for (int i = 0; i < request.restaurants().size(); i++) {
            AddRestaurantRequest restaurantRequest = request.restaurants().get(i);
            Restaurant restaurant = initialRestaurants.get(i);

            restaurants.add(ListRestaurant.builder()
                    .userList(savedList)
                    .restaurant(restaurant)
                    .tasteScore(restaurantRequest.tasteScore())
                    .valueScore(restaurantRequest.valueScore())
                    .moodScore(restaurantRequest.moodScore())
                    .build());
        }

        listRestaurantRepository.saveAll(restaurants);

        return UserListResponse.from(savedList);
    }

	// 내 리스트 목록
	public List<UserListResponse> getMyLists(Long userId) {
        return userListRepository.findAllByUserIdAndIsDeletedFalse(userId)
                .stream()
                .map(UserListResponse::from)
                .toList();
    }

	// 리스트 상세
	public UserListDetailResponse getList(Long listId) {
        UserList userList = userListRepository.findByIdAndIsDeletedFalse(listId)
                .orElseThrow(() -> new EntityNotFoundException("리스트를 찾을 수 없습니다."));
        return UserListDetailResponse.from(userList);
    }

	// 리스트 정보 수정
	@Transactional
    public UserListResponse updateList(Long userId, Long listId, UpdateListRequest request) {
        UserList userList = getOwnedList(userId, listId);
        userList.updateInfo(request.title(), request.description());
        return UserListResponse.from(userList);
    }

	// 공개 여부 변경
	@Transactional
    public void toggleVisibility(Long userId, Long listId) {
        UserList userList = getOwnedList(userId, listId);

		if (userList.getIsRepresentative() && userList.getIsPublic()) {
			throw new BusinessException("대표 리스트는 비공개로 변경할 수 없습니다.", HttpStatus.BAD_REQUEST);
		}
        userList.toggleVisibility();
    }

	// 대표 리스트 지정
	@Transactional
    public void setRepresentative(Long userId, Long listId) {
        // 기존에 대표 리스트가 있었는지 확인
    boolean hadRepresentative = userListRepository
            .existsByUserIdAndIsRepresentativeTrueAndIsDeletedFalse(userId);

        userListRepository.findAllByUserIdAndIsDeletedFalse(userId)
                .forEach(list -> list.setRepresentative(false));
        UserList userList = getOwnedList(userId, listId);
        userList.setRepresentative(true);
        userList.setPublic(true);

        // 최초 1회만 점수 제공
        if (!hadRepresentative) {
            reliabilityScoreService.increase(userId, ScoreEvent.REPRESENTATIVE_SET);
        }
    }

	// 리스트 삭제
	@Transactional
	public void deleteList(Long userId, Long listId) {
        UserList userList = getOwnedList(userId, listId);
		listRestaurantRepository.deleteAllByUserListId(listId);
        userList.delete();
    }
	
	// 리스트에 식당 추가
    @Transactional
    public void addRestaurant(Long userId, Long listId, AddRestaurantRequest request) {
        UserList userList = getOwnedList(userId, listId);
        validateDuplicateRestaurant(userList.getId(), request.restaurantId());
        Restaurant restaurant = getVisibleRestaurant(request.restaurantId());
        validateRegionMatch(userList.getRegionName(), restaurant.getRegionName());

        ListRestaurant listRestaurant = ListRestaurant.builder()
                .userList(userList)
                .restaurant(restaurant)
                .tasteScore(request.tasteScore())
                .valueScore(request.valueScore())
                .moodScore(request.moodScore())
                .build();

        listRestaurantRepository.save(listRestaurant);
    }

    @Transactional
    public void addExternalFallbackRestaurant(Long userId, Long listId, AddExternalRestaurantRequest request) {
        UserList userList = getOwnedList(userId, listId);
        PcmapRestaurantCandidate candidate = resolveExternalCandidate(request);
        validateExternalRegionMatch(userList.getRegionName(), candidate);

        Restaurant restaurant = restaurantRepository.findByPcmapPlaceId(candidate.placeId())
                .map(existing -> {
                    validateVisibleRestaurant(existing);
                    validateRegionMatch(userList.getRegionName(), existing.getRegionName());
                    return existing;
                })
                .orElseGet(() -> restaurantRepository.save(createRestaurantFromExternalCandidate(
                        candidate,
                        userList.getRegionName()
                )));

        validateDuplicateRestaurant(userList.getId(), restaurant.getId());

        listRestaurantRepository.save(ListRestaurant.builder()
                .userList(userList)
                .restaurant(restaurant)
                .tasteScore(request.tasteScore())
                .valueScore(request.valueScore())
                .moodScore(request.moodScore())
                .build());
    }

	// 평가 수정
    @Transactional
    public void updateScore(Long userId, Long listId, Long restaurantId, UpdateScoreRequest request) {
        getOwnedList(userId, listId);
        ListRestaurant listRestaurant = listRestaurantRepository
                .findByIdAndUserListId(restaurantId, listId)
                .orElseThrow(() -> new EntityNotFoundException("식당을 찾을 수 없습니다."));
        listRestaurant.updateScore(
                request.tasteScore(),
                request.valueScore(),
                request.moodScore()
        );
    }

	// 리스트 식당 삭제
    @Transactional
    public void removeRestaurant(Long userId, Long listId, Long restaurantId) {
        getOwnedList(userId, listId);
        validateMinimumRestaurantCountForRemoval(listId);
        ListRestaurant listRestaurant = listRestaurantRepository
                .findByUserListIdAndRestaurantId(listId, restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("식당을 찾을 수 없습니다."));
        listRestaurantRepository.delete(listRestaurant);
    }

	// 소유자 검증
    private UserList getOwnedList(Long userId, Long listId) {
        UserList userList = userListRepository.findByIdAndIsDeletedFalse(listId)
                .orElseThrow(() -> new EntityNotFoundException("리스트를 찾을 수 없습니다."));
        if (!userList.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("리스트 소유자가 아닙니다.");
        }
        return userList;
    }

    private ListRestaurant toListRestaurant(UserList userList, AddRestaurantRequest request) {
        Restaurant restaurant = getVisibleRestaurant(request.restaurantId());
        validateRegionMatch(userList.getRegionName(), restaurant.getRegionName());

        return ListRestaurant.builder()
                .userList(userList)
                .restaurant(restaurant)
                .tasteScore(request.tasteScore())
                .valueScore(request.valueScore())
                .moodScore(request.moodScore())
                .build();
    }

    private List<Restaurant> validateAndLoadRestaurants(String listRegionName, List<AddRestaurantRequest> requests) {
        List<Restaurant> restaurants = new ArrayList<>();
        for (AddRestaurantRequest request : requests) {
            Restaurant restaurant = getVisibleRestaurant(request.restaurantId());
            validateRegionMatch(listRegionName, restaurant.getRegionName());
            restaurants.add(restaurant);
        }
        return restaurants;
    }

    private Restaurant getVisibleRestaurant(Long restaurantId) {
        return restaurantRepository.findByIdAndIsDeletedFalseAndIsHiddenFalse(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("식당을 찾을 수 없습니다."));
    }

    private void validateMinimumRestaurantCount(List<AddRestaurantRequest> restaurants) {
        if (restaurants == null || restaurants.size() < MIN_RESTAURANT_COUNT) {
            throw new BusinessException("리스트는 최소 5개의 식당을 포함해야 합니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateNoDuplicateRestaurants(List<AddRestaurantRequest> restaurants) {
        long uniqueRestaurantCount = restaurants.stream()
                .map(AddRestaurantRequest::restaurantId)
                .distinct()
                .count();

        if (uniqueRestaurantCount != restaurants.size()) {
            throw new BusinessException("같은 리스트에 동일한 식당을 중복으로 추가할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateDuplicateRestaurant(Long listId, Long restaurantId) {
        if (listRestaurantRepository.findByUserListIdAndRestaurantId(listId, restaurantId).isPresent()) {
            throw new BusinessException("같은 리스트에 동일한 식당을 중복으로 추가할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateRegionMatch(String listRegionName, String restaurantRegionName) {
        if (!listRegionName.equals(restaurantRegionName)) {
            throw new BusinessException("리스트 지역과 식당 지역이 일치해야 합니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateMinimumRestaurantCountForRemoval(Long listId) {
        if (listRestaurantRepository.countByUserListId(listId) <= MIN_RESTAURANT_COUNT) {
            throw new BusinessException("리스트는 최소 5개의 식당을 유지해야 합니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private PcmapRestaurantCandidate resolveExternalCandidate(AddExternalRestaurantRequest request) {
        return pcmapSearchClient.searchRestaurants(request.searchQuery(), 20).stream()
                .filter(candidate -> request.externalPlaceId().equals(candidate.placeId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "검색 결과에서 선택한 외부 식당을 다시 확인할 수 없습니다.",
                        HttpStatus.BAD_REQUEST
                ));
    }

    private Restaurant createRestaurantFromExternalCandidate(
            PcmapRestaurantCandidate candidate,
            String listRegionName
    ) {
        String address = resolveExternalAddress(candidate);
        if (address == null || address.isBlank()) {
            throw new BusinessException("외부 식당 주소가 없어 리스트에 추가할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        return Restaurant.builder()
                .name(candidate.name())
                .address(address)
                .roadAddress(normalizeText(candidate.roadAddress()))
                .categoryName(normalizeText(candidate.categoryName()))
                .regionName(listRegionName)
                .regionFilterNames(buildExternalRegionFilterNames(candidate, listRegionName))
                .lat(toBigDecimal(candidate.y()))
                .lng(toBigDecimal(candidate.x()))
                .imageUrl(normalizeText(candidate.imageUrl()))
                .pcmapPlaceId(candidate.placeId())
                .build();
    }

    private void validateVisibleRestaurant(Restaurant restaurant) {
        if (Boolean.TRUE.equals(restaurant.getIsDeleted()) || Boolean.TRUE.equals(restaurant.getIsHidden())) {
            throw new BusinessException("선택한 식당은 현재 리스트에 추가할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateExternalRegionMatch(String listRegionName, PcmapRestaurantCandidate candidate) {
        String joinedAddress = String.join(" ",
                normalizeText(candidate.address()) == null ? "" : normalizeText(candidate.address()),
                normalizeText(candidate.roadAddress()) == null ? "" : normalizeText(candidate.roadAddress()),
                normalizeText(candidate.fullAddress()) == null ? "" : normalizeText(candidate.fullAddress())
        );

        if (!containsAllRegionTokens(joinedAddress, listRegionName)) {
            throw new BusinessException("리스트 지역과 외부 식당 지역이 일치해야 합니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private boolean containsAllRegionTokens(String addressText, String regionName) {
        String normalizedAddressText = normalizeText(addressText);
        String normalizedRegionName = normalizeText(regionName);
        if (normalizedAddressText == null || normalizedRegionName == null) {
            return false;
        }

        for (String token : normalizedRegionName.split("\\s+")) {
            if (!normalizedAddressText.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private List<String> buildExternalRegionFilterNames(PcmapRestaurantCandidate candidate, String listRegionName) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.add(listRegionName);
        addIfNotBlank(names, candidate.address());
        addIfNotBlank(names, candidate.roadAddress());
        addIfNotBlank(names, candidate.fullAddress());
        return new ArrayList<>(names);
    }

    private String resolveExternalAddress(PcmapRestaurantCandidate candidate) {
        String roadAddress = normalizeText(candidate.roadAddress());
        if (roadAddress != null) {
            return roadAddress;
        }
        String address = normalizeText(candidate.address());
        if (address != null) {
            return address;
        }
        return normalizeText(candidate.fullAddress());
    }

    private BigDecimal toBigDecimal(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void addIfNotBlank(LinkedHashSet<String> values, String value) {
        String normalized = normalizeText(value);
        if (normalized != null) {
            values.add(normalized);
        }
    }
}
