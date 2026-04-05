package com.example.Capstone.service;

import com.example.Capstone.domain.*;
import com.example.Capstone.dto.request.*;
import com.example.Capstone.dto.response.RestaurantResponse;
import com.example.Capstone.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantCategoryRepository restaurantCategoryRepository;
    private final UserRepository userRepository;
    private final UserListRepository userListRepository;

    // 식당 등록
    @Transactional
    public RestaurantResponse createRestaurant(CreateRestaurantRequest request) {
        Restaurant restaurant = Restaurant.builder()
                .name(request.name())
                .address(request.address())
                .regionName(request.regionName())
                .lat(request.lat())
                .lng(request.lng())
                .imageUrl(request.imageUrl())
                .build();
        return RestaurantResponse.from(restaurantRepository.save(restaurant));
    }

    // 식당 정보 수정
    @Transactional
    public RestaurantResponse updateRestaurant(Long restaurantId, UpdateRestaurantRequest request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("식당을 찾을 수 없습니다."));
        restaurant.updateInfo(
                request.name(),
                request.address(),
                request.regionName(),
                request.lat(),
                request.lng(),
                request.imageUrl()
        );
        return RestaurantResponse.from(restaurant);
    }

    // 카테고리 수정
    @Transactional
    public void updateCategories(Long restaurantId, UpdateCategoryRequest request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("식당을 찾을 수 없습니다."));

        // 기존 카테고리 전부 삭제 후 새로 저장
        restaurantCategoryRepository.deleteAllByRestaurantId(restaurantId);

        List<RestaurantCategory> categories = request.categories().stream()
                .map(categoryName -> RestaurantCategory.builder()
                        .restaurant(restaurant)
                        .categoryName(categoryName)
                        .build())
                .toList();

        restaurantCategoryRepository.saveAll(categories);
    }

    // 식당 비노출
    @Transactional
    public void hideRestaurant(Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("식당을 찾을 수 없습니다."));
        restaurant.hide();
    }

    // 유저 비노출
    @Transactional
    public void hideUser(Long userId) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다."));
        user.hide();
    }

    // 리스트 비노출
    @Transactional
    public void hideList(Long listId) {
        UserList userList = userListRepository.findByIdAndIsDeletedFalse(listId)
                .orElseThrow(() -> new EntityNotFoundException("리스트를 찾을 수 없습니다."));
        userList.hide();
    }
}
