package com.example.Capstone.domain;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(nullable = false, length = 100)
    private String providerUserId;

    @Column(nullable = false, unique = true, length = 30)
    private String nickname;

    @Column
    private Short birthYear;

    @Column
    private Short birthMonth;

    @Column
    private Short birthDay;

    @Column(length = 10)
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(length = 500)
    private String profileImageUrl;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(nullable = false)
    private Boolean isHidden = false;

    @Column(nullable = false)
    private Boolean isDeleted = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime deletedAt;

    public enum Gender {
        MALE, FEMALE
    }

    public enum Role {
        USER, ADMIN
    }

   @Builder
    private User(String provider, String providerUserId, String nickname,
                String profileImageUrl, Role role) {
        this.provider        = provider;
        this.providerUserId  = providerUserId;
        this.nickname        = nickname;
        this.profileImageUrl = profileImageUrl;
        this.role            = role != null ? role : Role.USER;
        this.isHidden        = false;
        this.isDeleted       = false;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updateProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void updateAdditionalInfo(Short birthYear, Short birthMonth,
                                     Short birthDay, Gender gender) {
        this.birthYear  = birthYear;
        this.birthMonth = birthMonth;
        this.birthDay   = birthDay;
        this.gender     = gender;
    }

    public void delete() {
        this.isDeleted  = true;
        this.deletedAt  = LocalDateTime.now();
    }

    public void hide() {
        this.isHidden = true;
    }

    public void show() {
        this.isHidden = false;
    }

}
