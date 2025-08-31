package io.testomat.commands;

import picocli.CommandLine;

@CommandLine.Command(
        name = "sync",
        aliases = {"update-ids"},
        description = "Run export then importId",
        mixinStandardHelpOptions = true)
public class SyncCommand implements Runnable {
    private static final String DEFAULT_URL = "https://app.testomat.io";
    private static final String VERSION = "v.0.1.8";

    @CommandLine.Option(
            names = {"-key", "--apikey"},

            description = "API key for Testomat.io",
            defaultValue = "${env:TESTOMATIO}")
    private String apiKey;

    @CommandLine.Option(
            names = {"--url"},
            description = "Server URL"
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
        System.out.println("JAVA-CHECK-TESTS " + VERSION);
        defineUrl();
        CommandLine parent = spec.parent().commandLine();
        handeCommandExecution(parent, getImportArgsForCommand("import"));
        handeCommandExecution(parent, getImportArgsForCommand("pull-ids"));
    }

    private void defineUrl() {
        if (url == null || url.trim().isEmpty()) {
            String envUrl = System.getenv("TESTOMATIO_URL");
            if (envUrl == null || envUrl.trim().isEmpty()) {
                url = DEFAULT_URL;
            } else {
                url = envUrl;
            }
        }
    }

    private String[] getImportArgsForCommand(String command) {
        return verbose
                ? new String[]{command,
                    "--apikey=" + apiKey,
                    "--url=" + url,
                    "--directory=" + directory,
                    "-v"}
                : new String[]{command,
                    "--apikey=" + apiKey,
                    "--url=" + url,
                    "--directory=" + directory};
    }

    private void handeCommandExecution(CommandLine parent, String[] args) {
        System.out.println("Running " + args[0] + " command...");
        int code1 = parent.execute(args);
        if (code1 != 0) {
            spec.commandLine().getErr().println("import failed with code " + code1);
            System.exit(code1);
        }
    }
}
