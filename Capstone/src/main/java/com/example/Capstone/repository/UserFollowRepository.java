package com.example.Capstone.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.Capstone.domain.UserFollow;

@Repository
public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {
    
    // 팔로우 여부 확인
    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);
    void deleteByFollowerIdAndFollowingId(Long followerId, Long followingId);
    List<UserFollow> findAllByFollowingId(Long followingId); // 팔로워 목록
    List<UserFollow> findAllByFollowerId(Long followerId);   // 팔로잉 목록
    long countByFollowingId(Long followingId);
    long countByFollowerId(Long followerId);
}
