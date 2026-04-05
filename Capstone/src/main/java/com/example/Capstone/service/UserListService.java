package com.example.Capstone.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.domain.ListRestaurant;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.User;
import com.example.Capstone.domain.UserList;
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

    private final UserListRepository userListRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final ListRestaurantRepository listRestaurantRepository;

	// 리스트 생성
	@Transactional
    public UserListResponse createList(Long userId, CreateListRequest request) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다."));

        UserList userList = UserList.builder()
                .user(user)
                .title(request.title())
                .description(request.description())
                .regionName(request.regionName())
                .build();

        return UserListResponse.from(userListRepository.save(userList));
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
        userListRepository.findAllByUserIdAndIsDeletedFalse(userId)
                .forEach(list -> list.setRepresentative(false));
        UserList userList = getOwnedList(userId, listId);
        userList.setRepresentative(true);
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
        Restaurant restaurant = restaurantRepository
                .findByIdAndIsDeletedFalseAndIsHiddenFalse(request.restaurantId())
                .orElseThrow(() -> new EntityNotFoundException("식당을 찾을 수 없습니다."));

        ListRestaurant listRestaurant = ListRestaurant.builder()
                .userList(userList)
                .restaurant(restaurant)
                .tasteScore(request.tasteScore())
                .valueScore(request.valueScore())
                .moodScore(request.moodScore())
                .build();

        listRestaurantRepository.save(listRestaurant);
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
}
