package com.example.Capstone.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.Capstone.domain.ListLike;

@Repository
public interface ListLikeRepository extends JpaRepository<ListLike, Long> {
    boolean existsByUserIdAndUserListId(Long userId, Long listId);
    void deleteByUserIdAndUserListId(Long userId, Long listId);
    long countByUserListId(Long listId);
    long countByUserListUserId(Long userId);
}
