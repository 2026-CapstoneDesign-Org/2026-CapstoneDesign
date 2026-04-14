package com.example.Capstone.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Repository;

import com.example.Capstone.repository.RestaurantRecommendationRepository.CandidateRestaurantRow;
import com.example.Capstone.repository.RestaurantRecommendationRepository.NeighborInteractionRow;
import com.example.Capstone.repository.RestaurantRecommendationRepository.RankingSignalRow;
import com.example.Capstone.repository.RestaurantRecommendationRepository.UserInteractionRow;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

@Repository
public class RestaurantRecommendationRepositoryImpl implements RestaurantRecommendationRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<UserInteractionRow> findUserInteractions(Long userId) {
        Query query = entityManager.createNativeQuery("""
                with eligible_entries as (
                    select
                        lr.restaurant_id,
                        r.region_name,
                        lr.auto_score,
                        lr.updated_at
                    from list_restaurants lr
                    join user_lists ul on ul.id = lr.list_id
                    join restaurants r on r.id = lr.restaurant_id
                    where ul.user_id = :userId
                      and ul.is_deleted = false
                      and ul.is_hidden = false
                      and r.is_deleted = false
                      and r.is_hidden = false
                ),
                user_restaurant_best as (
                    select
                        restaurant_id,
                        region_name,
                        max(auto_score) as best_auto_score,
                        max(updated_at) as latest_updated_at
                    from eligible_entries
                    group by restaurant_id, region_name
                )
                select
                    restaurant_id,
                    region_name,
                    best_auto_score,
                    latest_updated_at
                from user_restaurant_best
                order by
                    best_auto_score desc,
                    latest_updated_at desc,
                    restaurant_id asc
                """);
        query.setParameter("userId", userId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<UserInteractionRow> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new UserInteractionRow(
                    toLong(row[0]),
                    (String) row[1],
                    toBigDecimal(row[2]),
                    toLocalDateTime(row[3])
            ));
        }
        return result;
    }

    @Override
    public List<CandidateRestaurantRow> findSameRegionCandidates(Long userId, String regionName, int limit, int smoothingConstant) {
        return findCandidates(userId, regionName, limit, smoothingConstant, true);
    }

    @Override
    public List<CandidateRestaurantRow> findFallbackCandidates(Long userId, String excludedRegionName, int limit, int smoothingConstant) {
        return findCandidates(userId, excludedRegionName, limit, smoothingConstant, false);
    }

    @Override
    public List<NeighborInteractionRow> findNeighborInteractions(Long userId, int minimumOverlap, int limit) {
        Query query = entityManager.createNativeQuery("""
                with eligible_entries as (
                    select
                        u.id as user_id,
                        lr.restaurant_id,
                        lr.auto_score
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
                ),
                user_restaurant_best as (
                    select
                        user_id,
                        restaurant_id,
                        max(auto_score) as user_best_auto_score
                    from eligible_entries
                    group by user_id, restaurant_id
                ),
                current_user_best as (
                    select restaurant_id
                    from user_restaurant_best
                    where user_id = :userId
                ),
                preliminary_neighbors as (
                    select
                        urb.user_id,
                        count(*) as overlap_count
                    from user_restaurant_best urb
                    join current_user_best cub on cub.restaurant_id = urb.restaurant_id
                    where urb.user_id <> :userId
                    group by urb.user_id
                    having count(*) >= :minimumOverlap
                    order by
                        overlap_count desc,
                        urb.user_id asc
                    limit :limit
                )
                select
                    urb.user_id,
                    urb.restaurant_id,
                    urb.user_best_auto_score
                from user_restaurant_best urb
                join preliminary_neighbors pn on pn.user_id = urb.user_id
                order by
                    urb.user_id asc,
                    urb.restaurant_id asc
                """);
        query.setParameter("userId", userId);
        query.setParameter("minimumOverlap", minimumOverlap);
        query.setParameter("limit", limit);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<NeighborInteractionRow> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new NeighborInteractionRow(
                    toLong(row[0]),
                    toLong(row[1]),
                    toBigDecimal(row[2])
            ));
        }
        return result;
    }

    @Override
    public List<RankingSignalRow> findRankingSignals(List<Long> restaurantIds, int smoothingConstant) {
        if (restaurantIds == null || restaurantIds.isEmpty()) {
            return List.of();
        }

        String idPlaceholders = IntStream.range(0, restaurantIds.size())
                .mapToObj(index -> ":restaurantId" + index)
                .collect(Collectors.joining(", "));

        String sql = """
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
                    r.id as restaurant_id,
                    (
                        (
                            cast(coalesce(rs.evaluation_count, 0) as numeric)
                            / cast(coalesce(rs.evaluation_count, 0) + :m as numeric)
                        ) * coalesce(rs.average_auto_score, 0)
                        +
                        (
                            cast(:m as numeric)
                            / cast(coalesce(rs.evaluation_count, 0) + :m as numeric)
                        ) * ss.global_mean
                    ) as adjusted_score,
                    coalesce(rs.average_auto_score, 0) as average_auto_score,
                    coalesce(rs.evaluation_count, 0) as evaluation_count
                from restaurants r
                left join restaurant_stats rs on rs.restaurant_id = r.id
                join scope_stats ss on 1 = 1
                where r.id in (%s)
                order by r.id asc
                """.formatted(idPlaceholders);

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("m", smoothingConstant);
        bindRestaurantIds(query, restaurantIds);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<RankingSignalRow> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new RankingSignalRow(
                    toLong(row[0]),
                    toBigDecimal(row[1]),
                    toBigDecimal(row[2]),
                    toLong(row[3])
            ));
        }
        return result;
    }

    private List<CandidateRestaurantRow> findCandidates(
            Long userId,
            String regionValue,
            int limit,
            int smoothingConstant,
            boolean sameRegion
    ) {
        String regionCondition = sameRegion
                ? "r.region_name = :regionValue"
                : "r.region_name <> :regionValue";

        String sql = """
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
                    r.id as restaurant_id,
                    r.name as restaurant_name,
                    coalesce(r.road_address, r.address) as address,
                    r.region_name,
                    r.image_url,
                    (
                        (
                            cast(coalesce(rs.evaluation_count, 0) as numeric)
                            / cast(coalesce(rs.evaluation_count, 0) + :m as numeric)
                        ) * coalesce(rs.average_auto_score, 0)
                        +
                        (
                            cast(:m as numeric)
                            / cast(coalesce(rs.evaluation_count, 0) + :m as numeric)
                        ) * ss.global_mean
                    ) as adjusted_score,
                    coalesce(rs.evaluation_count, 0) as evaluation_count
                from restaurants r
                left join restaurant_stats rs on rs.restaurant_id = r.id
                join scope_stats ss on 1 = 1
                where r.is_deleted = false
                  and r.is_hidden = false
                  and %s
                  and not exists (
                      select 1
                      from list_restaurants my_lr
                      join user_lists my_ul on my_ul.id = my_lr.list_id
                      join restaurants my_r on my_r.id = my_lr.restaurant_id
                      where my_ul.user_id = :userId
                        and my_ul.is_deleted = false
                        and my_ul.is_hidden = false
                        and my_r.is_deleted = false
                        and my_r.is_hidden = false
                        and my_lr.restaurant_id = r.id
                  )
                order by
                    adjusted_score desc,
                    evaluation_count desc,
                    r.id asc
                """.formatted(regionCondition);

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("userId", userId);
        query.setParameter("regionValue", regionValue);
        query.setParameter("m", smoothingConstant);
        query.setMaxResults(limit);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<CandidateRestaurantRow> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new CandidateRestaurantRow(
                    toLong(row[0]),
                    (String) row[1],
                    (String) row[2],
                    (String) row[3],
                    (String) row[4]
            ));
        }
        return result;
    }

    private void bindRestaurantIds(Query query, List<Long> restaurantIds) {
        for (int index = 0; index < restaurantIds.size(); index++) {
            query.setParameter("restaurantId" + index, restaurantIds.get(index));
        }
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

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        throw new IllegalArgumentException("지원하지 않는 시간 타입입니다: " + value.getClass().getName());
    }
}
