package com.tally.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "habits")
public class Habit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 7)
    private String color; // Hex color code (e.g., #3498db)

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private Boolean archived = false;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @OneToMany(mappedBy = "habit", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DailyLog> dailyLogs = new ArrayList<>();

    // JPA requires a no-arg constructor
    protected Habit() {
    }

    public Habit(Long userId, String name, String description, String color) {
        this.userId = userId;
        this.name = name;
        this.description = description;
        this.color = color;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (archived == null) {
            archived = false;
        }
        if (displayOrder == null) {
            displayOrder = 0;
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Boolean getArchived() {
        return archived;
    }

    public void setArchived(Boolean archived) {
        this.archived = archived;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(LocalDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public List<DailyLog> getDailyLogs() {
        return dailyLogs;
    }

    public void setDailyLogs(List<DailyLog> dailyLogs) {
        this.dailyLogs = dailyLogs;
    }
}
