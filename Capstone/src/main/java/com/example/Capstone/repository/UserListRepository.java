package com.example.Capstone.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.Capstone.domain.UserList;

@Repository
public interface UserListRepository extends JpaRepository<UserList, Long> {
    List<UserList> findAllByUserIdAndIsDeletedFalse(Long userId);
    Optional<UserList> findByIdAndIsDeletedFalse(Long id);
}
