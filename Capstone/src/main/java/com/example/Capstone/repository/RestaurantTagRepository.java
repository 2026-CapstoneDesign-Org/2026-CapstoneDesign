package com.example.Capstone.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.Capstone.domain.RestaurantTag;

@Repository
public interface RestaurantTagRepository extends JpaRepository<RestaurantTag, Long> {
    void deleteAllByRestaurantId(Long restaurantId);
    boolean existsByTagId(Long tagId);

    @Query("""
            select rt
            from RestaurantTag rt
            join fetch rt.tag t
            where rt.restaurant.id = :restaurantId
              and t.isActive = true
            order by rt.isPrimary desc, rt.matchedMenuCount desc, t.tagName asc, rt.id asc
            """)
    List<RestaurantTag> findActiveTagsByRestaurantId(@Param("restaurantId") Long restaurantId);
}
