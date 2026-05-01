package com.example.Capstone.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Repository;

import com.example.Capstone.repository.ListRecommendationRepository.CandidateListRestaurantRow;
import com.example.Capstone.repository.ListRecommendationRepository.CandidateListSummaryRow;
import com.example.Capstone.repository.ListRecommendationRepository.OwnerInteractionRow;
import com.example.Capstone.repository.ListRecommendationRepository.UserListInteractionRow;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

@Repository
public class ListRecommendationRepositoryImpl implements ListRecommendationRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<UserListInteractionRow> findUserListInteractions(Long userId) {
        Query query = entityManager.createNativeQuery("""
                select
                    ul.id as list_id,
                    lr.restaurant_id,
                    ul.region_name,
                    lr.taste_score,
                    lr.value_score,
                    lr.mood_score,
                    lr.auto_score,
                    lr.updated_at
                from user_lists ul
                join list_restaurants lr on lr.list_id = ul.id
                join restaurants r on r.id = lr.restaurant_id
                where ul.user_id = :userId
                  and ul.is_deleted = false
                  and ul.is_hidden = false
                  and r.is_deleted = false
                  and r.is_hidden = false
                order by
                    lr.updated_at desc,
                    lr.restaurant_id asc
                """);
        query.setParameter("userId", userId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<UserListInteractionRow> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new UserListInteractionRow(
                    toLong(row[0]),
                    toLong(row[1]),
                    (String) row[2],
                    toBigDecimal(row[3]),
                    toBigDecimal(row[4]),
                    toBigDecimal(row[5]),
                    toBigDecimal(row[6]),
                    toLocalDateTime(row[7])
            ));
        }
        return result;
    }

    @Override
    public List<CandidateListSummaryRow> findSameRegionCandidateLists(
            Long userId,
            String regionName,
            int limit,
            int minRestaurantCount,
            int smoothingConstant
    ) {
        return findCandidateLists(userId, regionName, limit, minRestaurantCount, smoothingConstant, true);
    }

    @Override
    public List<CandidateListSummaryRow> findFallbackCandidateLists(
            Long userId,
            String regionName,
            int limit,
            int minRestaurantCount,
            int smoothingConstant
    ) {
        return findCandidateLists(userId, regionName, limit, minRestaurantCount, smoothingConstant, false);
    }

    @Override
    public List<CandidateListRestaurantRow> findCandidateListRestaurants(List<Long> listIds) {
        if (listIds == null || listIds.isEmpty()) {
            return List.of();
        }

        String placeholders = buildPlaceholders("listId", listIds.size());
        String sql = """
                select
                    lr.list_id,
                    lr.restaurant_id,
                    r.name as restaurant_name,
                    lr.taste_score,
                    lr.value_score,
                    lr.mood_score,
                    lr.auto_score
                from list_restaurants lr
                join user_lists ul on ul.id = lr.list_id
                join users u on u.id = ul.user_id
                join restaurants r on r.id = lr.restaurant_id
                where lr.list_id in (%s)
                  and ul.is_public = true
                  and ul.is_deleted = false
                  and ul.is_hidden = false
                  and u.is_deleted = false
                  and u.is_hidden = false
                  and r.is_deleted = false
                  and r.is_hidden = false
                order by
                    lr.list_id asc,
                    lr.auto_score desc,
                    lr.restaurant_id asc
                """.formatted(placeholders);

        Query query = entityManager.createNativeQuery(sql);
        bindIds(query, "listId", listIds);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<CandidateListRestaurantRow> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new CandidateListRestaurantRow(
                    toLong(row[0]),
                    toLong(row[1]),
                    (String) row[2],
                    toBigDecimal(row[3]),
                    toBigDecimal(row[4]),
                    toBigDecimal(row[5]),
                    toBigDecimal(row[6])
            ));
        }
        return result;
    }

    @Override
    public List<OwnerInteractionRow> findCandidateOwnerInteractions(Long userId, List<Long> ownerIds, int minimumOverlap) {
        if (ownerIds == null || ownerIds.isEmpty()) {
            return List.of();
        }

        String placeholders = buildPlaceholders("ownerId", ownerIds.size());
        String sql = """
                with eligible_entries as (
                    select
                        u.id as owner_id,
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
                        owner_id,
                        restaurant_id,
                        max(auto_score) as user_best_auto_score
                    from eligible_entries
                    group by owner_id, restaurant_id
                ),
                current_user_best as (
                    select restaurant_id
                    from user_restaurant_best
                    where owner_id = :userId
                ),
                candidate_owner_overlap as (
                    select
                        urb.owner_id,
                        count(*) as overlap_count
                    from user_restaurant_best urb
                    join current_user_best cub on cub.restaurant_id = urb.restaurant_id
                    where urb.owner_id in (%s)
                      and urb.owner_id <> :userId
                    group by urb.owner_id
                    having count(*) >= :minimumOverlap
                )
                select
                    urb.owner_id,
                    urb.restaurant_id,
                    urb.user_best_auto_score
                from user_restaurant_best urb
                join candidate_owner_overlap coo on coo.owner_id = urb.owner_id
                order by
                    urb.owner_id asc,
                    urb.restaurant_id asc
                """.formatted(placeholders);

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("userId", userId);
        query.setParameter("minimumOverlap", minimumOverlap);
        bindIds(query, "ownerId", ownerIds);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<OwnerInteractionRow> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new OwnerInteractionRow(
                    toLong(row[0]),
                    toLong(row[1]),
                    toBigDecimal(row[2])
            ));
        }
        return result;
    }

    private List<CandidateListSummaryRow> findCandidateLists(
            Long userId,
            String regionName,
            int limit,
            int minRestaurantCount,
            int smoothingConstant,
            boolean sameRegion
    ) {
        String regionCondition = sameRegion
                ? "ul.region_name = :regionName"
                : "ul.region_name <> :regionName";

        String sql = """
                with eligible_entries as (
                    select
                        ul.id as list_id,
                        ul.title,
                        ul.description,
                        ul.region_name,
                        ul.updated_at as list_updated_at,
                        u.id as owner_id,
                        u.nickname as owner_nickname,
                        u.profile_image_url as owner_profile_image_url,
                        lr.auto_score
                    from user_lists ul
                    join users u on u.id = ul.user_id
                    join list_restaurants lr on lr.list_id = ul.id
                    join restaurants r on r.id = lr.restaurant_id
                    where ul.user_id <> :userId
                      and ul.is_public = true
                      and ul.is_deleted = false
                      and ul.is_hidden = false
                      and u.is_deleted = false
                      and u.is_hidden = false
                      and r.is_deleted = false
                      and r.is_hidden = false
                      and %s
                ),
                candidate_list_stats as (
                    select
                        list_id,
                        title,
                        description,
                        region_name,
                        owner_id,
                        owner_nickname,
                        owner_profile_image_url,
                        count(*) as restaurant_count,
                        avg(auto_score) as average_auto_score,
                        max(list_updated_at) as updated_at
                    from eligible_entries
                    group by
                        list_id,
                        title,
                        description,
                        region_name,
                        owner_id,
                        owner_nickname,
                        owner_profile_image_url
                    having count(*) >= :minRestaurantCount
                ),
                scope_stats as (
                    select
                        coalesce(avg(average_auto_score), 0) as global_mean
                    from candidate_list_stats
                )
                select
                    cls.list_id,
                    cls.title,
                    cls.description,
                    cls.region_name,
                    cls.owner_id,
                    cls.owner_nickname,
                    cls.owner_profile_image_url,
                    cls.restaurant_count,
                    cls.average_auto_score,
                    (
                        (
                            cast(cls.restaurant_count as double precision)
                            / cast(cls.restaurant_count + :m as double precision)
                        ) * cls.average_auto_score
                        +
                        (
                            cast(:m as double precision)
                            / cast(cls.restaurant_count + :m as double precision)
                        ) * ss.global_mean
                    ) as adjusted_quality_score,
                    cls.updated_at
                from candidate_list_stats cls
                join scope_stats ss on 1 = 1
                order by
                    adjusted_quality_score desc,
                    cls.restaurant_count desc,
                    cls.updated_at desc,
                    cls.list_id asc
                """.formatted(regionCondition);

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("userId", userId);
        query.setParameter("regionName", regionName);
        query.setParameter("minRestaurantCount", minRestaurantCount);
        query.setParameter("m", smoothingConstant);
        query.setMaxResults(limit);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<CandidateListSummaryRow> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new CandidateListSummaryRow(
                    toLong(row[0]),
                    (String) row[1],
                    (String) row[2],
                    (String) row[3],
                    toLong(row[4]),
                    (String) row[5],
                    (String) row[6],
                    toLong(row[7]),
                    toBigDecimal(row[8]),
                    toBigDecimal(row[9]),
                    toLocalDateTime(row[10])
            ));
        }
        return result;
    }

    private String buildPlaceholders(String prefix, int size) {
        return IntStream.range(0, size)
                .mapToObj(index -> ":" + prefix + index)
                .collect(Collectors.joining(", "));
    }

    private void bindIds(Query query, String prefix, List<Long> ids) {
        for (int index = 0; index < ids.size(); index++) {
            query.setParameter(prefix + index, ids.get(index));
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
