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
public interface RestaurantRepository extends JpaRepository<Restaurant, Long>, RestaurantRankingRepository {
    List<Restaurant> findByNameContainingAndIsDeletedFalseAndIsHiddenFalse(String keyword);

    Optional<Restaurant> findByIdAndIsDeletedFalseAndIsHiddenFalse(Long id);

    Optional<Restaurant> findByPcmapPlaceId(String pcmapPlaceId);

    Optional<Restaurant> findByNameAndAddress(String name, String address);

    @Query("""
            select distinct r
            from Restaurant r
            left join r.menuItems mi
            left join r.restaurantTags rt
            left join rt.tag t
            where r.isDeleted = false
              and r.isHidden = false
              and (
                    lower(r.name) like lower(concat('%', :keyword, '%'))
                 or lower(r.address) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.roadAddress, '')) like lower(concat('%', :keyword, '%'))
                 or lower(r.regionName) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.regionCityName, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.regionDistrictName, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.regionCountyName, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.regionTownName, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.categoryName, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.primaryCategoryName, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(mi.menuName, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(mi.normalizedMenuName, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(t.tagName, '')) like lower(concat('%', :keyword, '%'))
              )
            order by r.name asc, r.id asc
            """)
    List<Restaurant> searchVisibleRestaurantsBySearchKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            select r
            from Restaurant r
            where r.isDeleted = false
              and r.isHidden = false
              and (
                    lower(r.name) like lower(concat('%', :keyword, '%'))
                 or lower(r.address) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.roadAddress, '')) like lower(concat('%', :keyword, '%'))
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
                 or lower(coalesce(r.regionTownName, '')) like lower(concat('%', :keyword, '%'))
              )
            order by r.regionName asc, r.name asc
            """)
    List<Restaurant> searchVisibleRestaurantsByRegion(@Param("keyword") String keyword, Pageable pageable);

    @Query(value = """
            select *
            from restaurants r
            where r.is_deleted = false
              and r.is_hidden = false
              and (
                    lower(r.region_name) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.region_city_name, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.region_district_name, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.region_county_name, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.region_town_name, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.region_filter_names, '')) like lower(concat('%', :keyword, '%'))
              )
            order by r.region_name asc, r.name asc, r.id asc
            """, nativeQuery = true)
    List<Restaurant> searchVisibleRestaurantsByRegionSignal(@Param("keyword") String keyword, Pageable pageable);
}
