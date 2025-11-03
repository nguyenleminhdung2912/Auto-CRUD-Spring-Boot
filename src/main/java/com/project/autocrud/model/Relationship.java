package com.project.autocrud.model;

import lombok.Data;

@Data
public class Relationship {
    private String fkColumn;           // profile_id
    private String targetTable;        // user_profiles
    private String targetClass;        // UserProfiles
    private String fieldName;          // userProfile
    private boolean inferred = false;
    private String relationshipType;   // OneToOne, ManyToOne, ManyToMany
    private String cascade;            // ALL, PERSIST, ...
    private String mappedBy;           // "userProfile"
    private boolean nullable;
    private boolean orphanRemoval;

    // For ManyToMany join metadata
    private String joinTableName;
    private String joinColumn; // column in join table referencing this entity
    private String inverseJoinColumn; // column in join table referencing target entity
}
