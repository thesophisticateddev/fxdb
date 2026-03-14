package org.fxsql.workspace;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Workspace {

    private String name;
    private List<Path> files;

    public Workspace() {
        this.files = new ArrayList<>();
    }

    public Workspace(String name) {
        this.name = name;
        this.files = new ArrayList<>();
    }

    public Workspace(String name, List<Path> files) {
        this.name = name;
        this.files = files != null ? new ArrayList<>(files) : new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Path> getFiles() {
        return files;
    }

    public void setFiles(List<Path> files) {
        this.files = files != null ? new ArrayList<>(files) : new ArrayList<>();
    }

    @Override
    public String toString() {
        return name;
    }
}
