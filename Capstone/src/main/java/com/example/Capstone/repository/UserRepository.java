package com.example.Capstone.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.Capstone.domain.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderUserId(String provider, String providerUserId);
    boolean existsByNickname(String nickname);
    boolean existsByNicknameAndIsDeletedFalse(String nickname);
    Optional<User> findByIdAndIsDeletedFalse(Long id);

    @Query("""
            select u
            from User u
            where u.isDeleted = false
              and u.isHidden = false
              and lower(u.nickname) like lower(concat('%', :keyword, '%'))
            order by
              case
                when lower(u.nickname) like lower(concat(:keyword, '%')) then 0
                else 1
              end,
              u.nickname asc,
              u.id asc
            """)
    List<User> searchVisibleUsers(@Param("keyword") String keyword, Pageable pageable);
}
