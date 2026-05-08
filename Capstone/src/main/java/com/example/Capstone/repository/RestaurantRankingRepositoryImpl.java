package com.example.Capstone.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.util.StringUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

public class RestaurantRankingRepositoryImpl implements RestaurantRankingRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<RestaurantRankingRow> findRestaurantRankings(
            String regionName,
            String category,
            int limit,
            int smoothingConstant
    ) {
        Query query = entityManager.createNativeQuery(buildRankingSql(regionName, category));

        if (StringUtils.hasText(regionName)) {
            query.setParameter("regionName", regionName);
        }
        if (StringUtils.hasText(category)) {
            query.setParameter("category", category);
        }
        query.setParameter("m", smoothingConstant);
        query.setMaxResults(limit);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<RestaurantRankingRow> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new RestaurantRankingRow(
                    toLong(row[0]),
                    (String) row[1],
                    (String) row[2],
                    (String) row[3],
                    toBigDecimal(row[4]),
                    toLong(row[5]),
                    toBigDecimal(row[6])
            ));
        }
        return result;
    }

    private String buildRankingSql(String regionName, String category) {
        StringBuilder sql = new StringBuilder("""
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
                """);

        if (StringUtils.hasText(regionName)) {
            sql.append("\n  and r.region_name = :regionName");
        }

        if (StringUtils.hasText(category)) {
            sql.append("\n  and (r.category_name = :category or r.primary_category_name = :category)");
        }

        sql.append("""

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
                        avg(user_best_auto_score) as global_mean
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
                    r.region_name,
                    r.image_url,
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
                    rs.evaluation_count desc,
                    rs.average_auto_score desc,
                    rs.restaurant_id asc
                """);

        return sql.toString();
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
