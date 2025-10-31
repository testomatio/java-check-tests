package io.testomat.service;

public class VerboseLogger {
    private final boolean verbose;

    public VerboseLogger(boolean verbose) {
        this.verbose = verbose;
    }

    public void log(String message) {
        if (verbose) {
            System.out.println(message);
        }
    }

}
