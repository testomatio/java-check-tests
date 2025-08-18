package io.testomat.commands;

import picocli.CommandLine;

@CommandLine.Command(
        name = "sync",
        aliases = {"update-ids"},
        description = "Run export then importId",
        mixinStandardHelpOptions = true)
public class SyncCommand implements Runnable {

    @CommandLine.Option(
            names = {"-key", "--apikey"},

            description = "API key for Testomat.io",
            defaultValue = "${env:TESTOMATIO}",
            required = true)
    private String apiKey;

    @CommandLine.Option(
            names = {"--url"},
            required = true,
            description = "Server URL",
            defaultValue = "${env:TESTOMATIO_URL}"
    )
    private String url;

    @CommandLine.Option(
            names = {"--directory", "-d"},
            description = "Directory",
            defaultValue = "."
    )
    private String directory;

    @CommandLine.Option(
            names = {"-v", "--verbose"},
            description = "Enable verbose output")
    private boolean verbose = false;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        CommandLine parent = spec.parent().commandLine();
        
        String[] importArgs = verbose 
            ? new String[]{"import", "--apikey=" + apiKey, "--url=" + url, 
                    "--directory=" + directory, "-v"}
            : new String[]{"import", "--apikey=" + apiKey, "--url=" + url, 
                    "--directory=" + directory};
            
        int code1 = parent.execute(importArgs);
        if (code1 != 0) {
            spec.commandLine().getErr().println("import failed with code " + code1);
            System.exit(code1);
        }
        
        String[] pullIdsArgs = verbose 
            ? new String[]{"pull-ids", "--apikey=" + apiKey, "--url=" + url, 
                    "--directory=" + directory, "-v"}
            : new String[]{"pull-ids", "--apikey=" + apiKey, "--url=" + url, 
                    "--directory=" + directory};
            
        System.out.println("Running pull-ids (1st time)...");
        int code2 = parent.execute(pullIdsArgs);
        if (code2 != 0) {
            spec.commandLine().getErr().println("pull-ids (1st run) failed with code " + code2);
            System.exit(code2);
        }
    }
}
