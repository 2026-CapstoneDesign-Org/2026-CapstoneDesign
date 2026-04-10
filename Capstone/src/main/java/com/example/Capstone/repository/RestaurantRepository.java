package com.example.Capstone.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.Capstone.domain.Restaurant;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    List<Restaurant> findByNameContainingAndIsDeletedFalseAndIsHiddenFalse(String keyword);

    Optional<Restaurant> findByIdAndIsDeletedFalseAndIsHiddenFalse(Long id);

    Optional<Restaurant> findByPcmapPlaceId(String pcmapPlaceId);

    Optional<Restaurant> findByNameAndAddress(String name, String address);

    @Query("""
            select r
            from Restaurant r
            where r.isDeleted = false
              and r.isHidden = false
              and (
                    lower(r.name) like lower(concat('%', :keyword, '%'))
                 or lower(r.address) like lower(concat('%', :keyword, '%'))
              )
            order by
              case
                when lower(r.name) like lower(concat(:keyword, '%')) then 0
                else 1
              end,
              r.name asc
            """)
    List<Restaurant> searchVisibleRestaurants(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            select r
            from Restaurant r
            where r.isDeleted = false
              and r.isHidden = false
              and (
                    lower(r.regionName) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.regionCityName, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.regionDistrictName, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.regionCountyName, '')) like lower(concat('%', :keyword, '%'))
              )
            order by r.regionName asc, r.name asc
            """)
    List<Restaurant> searchVisibleRestaurantsByRegion(@Param("keyword") String keyword, Pageable pageable);
}
