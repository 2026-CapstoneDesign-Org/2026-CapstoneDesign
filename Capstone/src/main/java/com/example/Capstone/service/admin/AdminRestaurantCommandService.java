package com.example.Capstone.service.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.dto.request.CreateRestaurantRequest;
import com.example.Capstone.dto.request.UpdateCategoryRequest;
import com.example.Capstone.dto.request.UpdateRestaurantRequest;
import com.example.Capstone.dto.response.RestaurantResponse;
import com.example.Capstone.repository.RestaurantRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminRestaurantCommandService {

    private final RestaurantRepository restaurantRepository;

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

    @Transactional
    public RestaurantResponse updateRestaurant(Long restaurantId, UpdateRestaurantRequest request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("?앸떦??李얠쓣 ???놁뒿?덈떎."));

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

    @Transactional
    public void updateCategories(Long restaurantId, UpdateCategoryRequest request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("?앸떦??李얠쓣 ???놁뒿?덈떎."));

        String categoryName = request.categories().stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("移댄뀒怨좊━媛 鍮꾩뼱 ?덉쓣 ???놁뒿?덈떎."));

        restaurant.updateCategoryName(categoryName);
    }

    @Transactional
    public void hideRestaurant(Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("?앸떦??李얠쓣 ???놁뒿?덈떎."));
        restaurant.hide();
    }
}
