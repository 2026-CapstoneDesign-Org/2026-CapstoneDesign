package com.example.Capstone.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
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

    Page<Restaurant> findByNameContainingAndIsDeletedFalseAndIsHiddenFalse(String keyword, Pageable pageable);

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

    @Query(value = """
            select r.*
            from restaurants r
            where r.is_deleted = false
              and r.is_hidden = false
              and (
                    lower(r.name) like lower(concat('%', :keyword, '%'))
                 or lower(r.address) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.road_address, '')) like lower(concat('%', :keyword, '%'))
                 or lower(r.region_name) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.region_city_name, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.region_district_name, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.region_county_name, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.region_town_name, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.region_filter_names, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.category_name, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.primary_category_name, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.conveniences, '')) like lower(concat('%', :keyword, '%'))
              )
            order by
              case
                when lower(r.name) like lower(concat(:keyword, '%')) then 0
                when lower(r.name) like lower(concat('%', :keyword, '%')) then 1
                when lower(coalesce(r.category_name, '')) like lower(concat('%', :keyword, '%'))
                  or lower(coalesce(r.primary_category_name, '')) like lower(concat('%', :keyword, '%')) then 2
                else 3
              end,
              r.name asc,
              r.id asc
            """, nativeQuery = true)
    List<Restaurant> searchVisibleRestaurantsByCoreKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query(value = """
            select distinct r.*
            from restaurant_menu_items mi
            join restaurants r on r.id = mi.restaurant_id
            where r.is_deleted = false
              and r.is_hidden = false
              and (
                    lower(coalesce(mi.menu_name, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(mi.normalized_menu_name, '')) like lower(concat('%', :keyword, '%'))
              )
            order by r.name asc, r.id asc
            """, nativeQuery = true)
    List<Restaurant> searchVisibleRestaurantsByMenuKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query(value = """
            select distinct r.*
            from restaurant_tags rt
            join tags t on t.id = rt.tag_id
            join restaurants r on r.id = rt.restaurant_id
            where r.is_deleted = false
              and r.is_hidden = false
              and t.is_active = true
              and lower(coalesce(t.tag_name, '')) like lower(concat('%', :keyword, '%'))
            order by r.name asc, r.id asc
            """, nativeQuery = true)
    List<Restaurant> searchVisibleRestaurantsByTagKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query(value = """
            select r.*
            from restaurants r
            where r.is_deleted = false
              and r.is_hidden = false
              and not exists (
                    select 1
                    from regexp_split_to_table(:keyword, '\\s+') as token(value)
                    where btrim(token.value) <> ''
                      and not (
                            lower(r.name) like lower(concat('%', token.value, '%'))
                         or lower(r.address) like lower(concat('%', token.value, '%'))
                         or lower(coalesce(r.road_address, '')) like lower(concat('%', token.value, '%'))
                         or lower(r.region_name) like lower(concat('%', token.value, '%'))
                         or lower(coalesce(r.region_city_name, '')) like lower(concat('%', token.value, '%'))
                         or lower(coalesce(r.region_district_name, '')) like lower(concat('%', token.value, '%'))
                         or lower(coalesce(r.region_county_name, '')) like lower(concat('%', token.value, '%'))
                         or lower(coalesce(r.region_town_name, '')) like lower(concat('%', token.value, '%'))
                         or lower(coalesce(r.region_filter_names, '')) like lower(concat('%', token.value, '%'))
                         or lower(coalesce(r.category_name, '')) like lower(concat('%', token.value, '%'))
                         or lower(coalesce(r.primary_category_name, '')) like lower(concat('%', token.value, '%'))
                         or lower(coalesce(r.conveniences, '')) like lower(concat('%', token.value, '%'))
                         or exists (
                                select 1
                                from restaurant_menu_items mi
                                where mi.restaurant_id = r.id
                                  and (
                                        lower(coalesce(mi.menu_name, '')) like lower(concat('%', token.value, '%'))
                                     or lower(coalesce(mi.normalized_menu_name, '')) like lower(concat('%', token.value, '%'))
                                  )
                           )
                         or exists (
                                select 1
                                from restaurant_tags rt
                                join tags t on t.id = rt.tag_id
                                where rt.restaurant_id = r.id
                                  and t.is_active = true
                                  and lower(coalesce(t.tag_name, '')) like lower(concat('%', token.value, '%'))
                           )
                      )
              )
            order by
              case
                when lower(r.name) like lower(concat(:keyword, '%')) then 0
                when lower(r.name) like lower(concat('%', :keyword, '%')) then 1
                when lower(coalesce(r.category_name, '')) like lower(concat('%', :keyword, '%'))
                  or lower(coalesce(r.primary_category_name, '')) like lower(concat('%', :keyword, '%')) then 2
                else 3
              end,
              r.name asc,
              r.id asc
            """, nativeQuery = true)
    List<Restaurant> searchVisibleRestaurantsBySearchTokens(@Param("keyword") String keyword, Pageable pageable);

    @Query(value = """
            select r.*
            from restaurants r
            where r.is_deleted = false
              and r.is_hidden = false
              and (
                    lower(r.region_name) like lower(concat('%', :regionKeyword, '%'))
                 or lower(coalesce(r.region_city_name, '')) like lower(concat('%', :regionKeyword, '%'))
                 or lower(coalesce(r.region_district_name, '')) like lower(concat('%', :regionKeyword, '%'))
                 or lower(coalesce(r.region_county_name, '')) like lower(concat('%', :regionKeyword, '%'))
                 or lower(coalesce(r.region_town_name, '')) like lower(concat('%', :regionKeyword, '%'))
                 or lower(coalesce(r.region_filter_names, '')) like lower(concat('%', :regionKeyword, '%'))
              )
              and not exists (
                    select 1
                    from regexp_split_to_table(:keyword, '\\s+') as token(value)
                    where btrim(token.value) <> ''
                      and not (
                            lower(r.name) like lower(concat('%', token.value, '%'))
                         or lower(r.address) like lower(concat('%', token.value, '%'))
                         or lower(coalesce(r.road_address, '')) like lower(concat('%', token.value, '%'))
                         or lower(coalesce(r.category_name, '')) like lower(concat('%', token.value, '%'))
                         or lower(coalesce(r.primary_category_name, '')) like lower(concat('%', token.value, '%'))
                         or lower(coalesce(r.conveniences, '')) like lower(concat('%', token.value, '%'))
                         or exists (
                                select 1
                                from restaurant_menu_items mi
                                where mi.restaurant_id = r.id
                                  and (
                                        lower(coalesce(mi.menu_name, '')) like lower(concat('%', token.value, '%'))
                                     or lower(coalesce(mi.normalized_menu_name, '')) like lower(concat('%', token.value, '%'))
                                  )
                           )
                         or exists (
                                select 1
                                from restaurant_tags rt
                                join tags t on t.id = rt.tag_id
                                where rt.restaurant_id = r.id
                                  and t.is_active = true
                                  and lower(coalesce(t.tag_name, '')) like lower(concat('%', token.value, '%'))
                           )
                      )
              )
            order by
              case
                when lower(r.name) like lower(concat(:keyword, '%')) then 0
                when lower(r.name) like lower(concat('%', :keyword, '%')) then 1
                when lower(coalesce(r.category_name, '')) like lower(concat('%', :keyword, '%'))
                  or lower(coalesce(r.primary_category_name, '')) like lower(concat('%', :keyword, '%')) then 2
                else 3
              end,
              r.name asc,
              r.id asc
            """, nativeQuery = true)
    List<Restaurant> searchVisibleRestaurantsByRegionAndSearchTokens(
            @Param("regionKeyword") String regionKeyword,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query(value = """
            select r.*
            from restaurants r
            where r.is_deleted = false
              and r.is_hidden = false
              and (
                    lower(r.region_name) like lower(concat('%', :regionKeyword, '%'))
                 or lower(coalesce(r.region_city_name, '')) like lower(concat('%', :regionKeyword, '%'))
                 or lower(coalesce(r.region_district_name, '')) like lower(concat('%', :regionKeyword, '%'))
                 or lower(coalesce(r.region_county_name, '')) like lower(concat('%', :regionKeyword, '%'))
                 or lower(coalesce(r.region_town_name, '')) like lower(concat('%', :regionKeyword, '%'))
                 or lower(coalesce(r.region_filter_names, '')) like lower(concat('%', :regionKeyword, '%'))
              )
              and (
                    lower(r.name) like lower(concat('%', :keyword, '%'))
                 or lower(r.address) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.road_address, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.category_name, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.primary_category_name, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(r.conveniences, '')) like lower(concat('%', :keyword, '%'))
              )
            order by
              case
                when lower(r.name) like lower(concat(:keyword, '%')) then 0
                when lower(r.name) like lower(concat('%', :keyword, '%')) then 1
                when lower(coalesce(r.category_name, '')) like lower(concat('%', :keyword, '%'))
                  or lower(coalesce(r.primary_category_name, '')) like lower(concat('%', :keyword, '%')) then 2
                else 3
              end,
              r.name asc,
              r.id asc
            """, nativeQuery = true)
    List<Restaurant> searchVisibleRestaurantsByRegionAndCoreKeyword(
            @Param("regionKeyword") String regionKeyword,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query(value = """
            select distinct r.*
            from restaurant_menu_items mi
            join restaurants r on r.id = mi.restaurant_id
            where r.is_deleted = false
              and r.is_hidden = false
              and (
                    lower(r.region_name) like lower(concat('%', :regionKeyword, '%'))
                 or lower(coalesce(r.region_city_name, '')) like lower(concat('%', :regionKeyword, '%'))
                 or lower(coalesce(r.region_district_name, '')) like lower(concat('%', :regionKeyword, '%'))
                 or lower(coalesce(r.region_county_name, '')) like lower(concat('%', :regionKeyword, '%'))
                 or lower(coalesce(r.region_town_name, '')) like lower(concat('%', :regionKeyword, '%'))
                 or lower(coalesce(r.region_filter_names, '')) like lower(concat('%', :regionKeyword, '%'))
              )
              and (
                    lower(coalesce(mi.menu_name, '')) like lower(concat('%', :keyword, '%'))
                 or lower(coalesce(mi.normalized_menu_name, '')) like lower(concat('%', :keyword, '%'))
              )
            order by r.name asc, r.id asc
            """, nativeQuery = true)
    List<Restaurant> searchVisibleRestaurantsByRegionAndMenuKeyword(
            @Param("regionKeyword") String regionKeyword,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query(value = """
            select distinct r.*
            from restaurant_tags rt
            join tags t on t.id = rt.tag_id
            join restaurants r on r.id = rt.restaurant_id
            where r.is_deleted = false
              and r.is_hidden = false
              and t.is_active = true
              and (
                    lower(r.region_name) like lower(concat('%', :regionKeyword, '%'))
                 or lower(coalesce(r.region_city_name, '')) like lower(concat('%', :regionKeyword, '%'))
                 or lower(coalesce(r.region_district_name, '')) like lower(concat('%', :regionKeyword, '%'))
                 or lower(coalesce(r.region_county_name, '')) like lower(concat('%', :regionKeyword, '%'))
                 or lower(coalesce(r.region_town_name, '')) like lower(concat('%', :regionKeyword, '%'))
                 or lower(coalesce(r.region_filter_names, '')) like lower(concat('%', :regionKeyword, '%'))
              )
              and lower(coalesce(t.tag_name, '')) like lower(concat('%', :keyword, '%'))
            order by r.name asc, r.id asc
            """, nativeQuery = true)
    List<Restaurant> searchVisibleRestaurantsByRegionAndTagKeyword(
            @Param("regionKeyword") String regionKeyword,
            @Param("keyword") String keyword,
            Pageable pageable
    );

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
