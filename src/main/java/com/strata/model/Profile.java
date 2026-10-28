package com.strata.model;

/**
 * Profile metadata.
 *
 * @param name profile identifier (filesystem-safe)
 * @param description human-readable description
 */
public record Profile(String name, String description) {

    public Profile {
        name = name.strip();
        description = description == null ? "" : description;
    }
}
