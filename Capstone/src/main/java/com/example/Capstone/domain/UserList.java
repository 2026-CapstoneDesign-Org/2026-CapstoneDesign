package com.example.Capstone.domain;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_lists")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class UserList {

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

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

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