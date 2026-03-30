package de.kallifabio.cloud.master.permissions;

import java.util.ArrayList;
import java.util.List;

public class PermissionGroup {
    public String name;
    public String parent;
    public int weight;
    public String prefix;
    public String suffix;
    public List<String> permissions = new ArrayList<>();

    public PermissionGroup() {
    }

    public PermissionGroup(String name) {
        this.name = name;
    }
}
