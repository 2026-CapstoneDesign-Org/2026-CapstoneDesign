package com.example.Capstone.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.example.Capstone.domain.base.BaseTimeEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_lists")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserList extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 50)
    private String regionName;

    @Column(nullable = false)
    private Boolean isPublic = false;

    @Column(nullable = false)
    private Boolean isRepresentative = false;

    @Column(nullable = false)
    private Boolean isHidden = false;

    @Column(nullable = false)
    private Boolean isDeleted = false;

    @Column
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "userList", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ListRestaurant> listRestaurants = new ArrayList<>();

    @Builder
    private UserList(User user, String title, String description, String regionName) {
        this.user        = user;
        this.title       = title;
        this.description = description;
        this.regionName  = regionName;
        this.isPublic          = false;
        this.isRepresentative  = false;
        this.isHidden          = false;
        this.isDeleted         = false;
    }

    public void updateInfo(String title, String description) {
        this.title       = title;
        this.description = description;
    }

    public void toggleVisibility() {
        this.isPublic = !this.isPublic;
    }

    public void setRepresentative(boolean isRepresentative) {
        this.isRepresentative = isRepresentative;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public void delete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    public void hide()  { this.isHidden = true; }
    public void show()  { this.isHidden = false; }
}
