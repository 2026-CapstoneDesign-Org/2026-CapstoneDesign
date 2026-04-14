package com.example.Capstone.service.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.domain.User;
import com.example.Capstone.domain.UserList;
import com.example.Capstone.repository.UserListRepository;
import com.example.Capstone.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminVisibilityService {

    private final UserRepository userRepository;
    private final UserListRepository userListRepository;

    @Transactional
    public void hideUser(Long userId) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다."));
        user.hide();
    }

    @Transactional
    public void hideList(Long listId) {
        UserList userList = userListRepository.findByIdAndIsDeletedFalse(listId)
                .orElseThrow(() -> new EntityNotFoundException("리스트를 찾을 수 없습니다."));
        userList.hide();
    }
}
