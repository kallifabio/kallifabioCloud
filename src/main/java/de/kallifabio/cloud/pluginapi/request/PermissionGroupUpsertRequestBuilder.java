package de.kallifabio.cloud.pluginapi.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PermissionGroupUpsertRequestBuilder {

    private String name;
    private String parent = "";
    private int weight = 0;
    private String prefix = "";
    private String suffix = "";
    private final List<String> permissions = new ArrayList<>();

    public static PermissionGroupUpsertRequestBuilder create() {
        return new PermissionGroupUpsertRequestBuilder();
    }

    public PermissionGroupUpsertRequestBuilder name(String name) {
        this.name = name;
        return this;
    }

    public PermissionGroupUpsertRequestBuilder parent(String parent) {
        this.parent = parent == null ? "" : parent;
        return this;
    }

    public PermissionGroupUpsertRequestBuilder weight(int weight) {
        this.weight = weight;
        return this;
    }

    public PermissionGroupUpsertRequestBuilder prefix(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
        return this;
    }

    public PermissionGroupUpsertRequestBuilder suffix(String suffix) {
        this.suffix = suffix == null ? "" : suffix;
        return this;
    }

    public PermissionGroupUpsertRequestBuilder addPermission(String permission) {
        if (permission != null && !permission.isBlank()) {
            this.permissions.add(permission);
        }
        return this;
    }

    public PermissionGroupUpsertRequest build() {
        return new PermissionGroupUpsertRequest(
                Objects.requireNonNull(name, "name"),
                parent,
                weight,
                prefix,
                suffix,
                List.copyOf(permissions)
        );
    }
}
