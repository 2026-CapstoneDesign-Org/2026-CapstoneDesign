package com.example.Capstone.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.Capstone.domain.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderUserId(String provider, String providerUserId);
    boolean existsByNickname(String nickname);
    boolean existsByNicknameAndIsDeletedFalse(String nickname);
    Optional<User> findByIdAndIsDeletedFalse(Long id);
}
