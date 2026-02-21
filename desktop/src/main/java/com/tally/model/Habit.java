package com.tally.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirrors the backend Habit entity / HabitResponse DTO.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Habit {

    private Long id;
    private String name;
    private String description;
    private String color;
    private int displayOrder;
    private boolean archived;

    public Habit() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
}
