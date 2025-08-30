package io.testomat.model;

import lombok.Getter;

@Getter
public class FilesProcessingResult {
    private int totalAnnotations = 0;
    private int totalImports = 0;
    private int modifiedFiles = 0;

    public void addResults(int annotations, int imports) {
        this.totalAnnotations += annotations;
        this.totalImports += imports;
        this.modifiedFiles++;
    }
}