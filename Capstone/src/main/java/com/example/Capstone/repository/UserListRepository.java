package com.example.Capstone.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.Capstone.domain.UserList;

@Repository
public interface UserListRepository extends JpaRepository<UserList, Long> {
    List<UserList> findAllByUserIdAndIsDeletedFalse(Long userId);
    Optional<UserList> findByIdAndIsDeletedFalse(Long id);
    Optional<UserList> findByUserIdAndIsRepresentativeTrueAndIsDeletedFalse(Long id);
    boolean existsByUserIdAndIsRepresentativeTrueAndIsDeletedFalse(Long userId);
    long countByUserIdAndIsPublicTrueAndIsDeletedFalse(Long userId);

    @Query("SELECT ul FROM UserList ul " +
           "JOIN FETCH ul.user " +
           "WHERE ul.user.id = :userId " +
           "AND ul.isDeleted = false")
    List<UserList> findAllByUserIdWithDetails(@Param("userId") Long userId);

    @Query("SELECT ul FROM UserList ul " +
           "JOIN FETCH ul.user " +
           "LEFT JOIN FETCH ul.listRestaurants lr " +
           "LEFT JOIN FETCH lr.restaurant " +
           "WHERE ul.id = :id " +
           "AND ul.isDeleted = false")
    Optional<UserList> findByIdWithDetails(@Param("id") Long id);
}
