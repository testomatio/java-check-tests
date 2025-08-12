package io.testomat.service;

import io.testomat.exception.CliException;
import java.io.File;

public class DirectoryValidator {

    public void validateDirectory(File directory) {
        if (directory == null) {
            throw new CliException("Directory cannot be null");
        }

        if (!directory.exists()) {
            throw new CliException("Directory does not exist: " + directory.getAbsolutePath());
        }

        if (!directory.isDirectory()) {
            throw new CliException("Path is not directory: " + directory.getAbsolutePath());
        }

        if (!directory.canRead()) {
            throw new CliException("Cannot read directory: " + directory.getAbsolutePath());
        }
    }
}
