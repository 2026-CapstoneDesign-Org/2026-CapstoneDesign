package com.example.Capstone.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

@Repository
public class HiddenGemRecommendationRepositoryImpl implements HiddenGemRecommendationRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<HiddenGemRestaurantRow> findHiddenGemCandidates(
            String regionTownName,
            int smoothingConstant
    ) {
        Query query = entityManager.createNativeQuery("""
                with eligible_entries as (
                    select
                        u.id as user_id,
                        r.id as restaurant_id,
                        lr.auto_score as auto_score
                    from list_restaurants lr
                    join user_lists ul on ul.id = lr.list_id
                    join users u on u.id = ul.user_id
                    join restaurants r on r.id = lr.restaurant_id
                    where ul.is_deleted = false
                      and ul.is_hidden = false
                      and u.is_deleted = false
                      and u.is_hidden = false
                      and r.is_deleted = false
                      and r.is_hidden = false
                      and r.region_town_name = :regionTownName
                ),
                user_restaurant_best as (
                    select
                        user_id,
                        restaurant_id,
                        max(auto_score) as user_best_auto_score
                    from eligible_entries
                    group by user_id, restaurant_id
                ),
                scope_stats as (
                    select
                        coalesce(avg(user_best_auto_score), 0) as global_mean
                    from user_restaurant_best
                ),
                restaurant_stats as (
                    select
                        restaurant_id,
                        avg(user_best_auto_score) as average_auto_score,
                        count(*) as evaluation_count
                    from user_restaurant_best
                    group by restaurant_id
                )
                select
                    rs.restaurant_id,
                    r.name as restaurant_name,
                    coalesce(nullif(r.road_address, ''), r.address) as address,
                    r.region_name,
                    r.region_town_name,
                    rs.average_auto_score,
                    rs.evaluation_count,
                    (
                        (
                            cast(rs.evaluation_count as double precision)
                            / cast(rs.evaluation_count + :m as double precision)
                        ) * rs.average_auto_score
                        +
                        (
                            cast(:m as double precision)
                            / cast(rs.evaluation_count + :m as double precision)
                        ) * ss.global_mean
                    ) as adjusted_score
                from restaurant_stats rs
                join scope_stats ss on 1 = 1
                join restaurants r on r.id = rs.restaurant_id
                order by
                    adjusted_score desc,
                    rs.average_auto_score desc,
                    rs.restaurant_id asc
                """);
        query.setParameter("regionTownName", regionTownName);
        query.setParameter("m", smoothingConstant);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<HiddenGemRestaurantRow> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new HiddenGemRestaurantRow(
                    toLong(row[0]),
                    (String) row[1],
                    (String) row[2],
                    (String) row[3],
                    (String) row[4],
                    toBigDecimal(row[5]),
                    toLong(row[6]),
                    toBigDecimal(row[7])
            ));
        }
        return result;
    }

    private Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return BigDecimal.valueOf(((Number) value).doubleValue());
    }
}
