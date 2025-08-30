package io.testomat.model;

import lombok.Getter;

@Getter
public class CleanupResult {
    private final int removedAnnotations;
    private final int removedImports;

    public CleanupResult(int removedAnnotations, int removedImports) {
        this.removedAnnotations = removedAnnotations;
        this.removedImports = removedImports;
    }
}
