package io.testomat.commands;

import picocli.CommandLine;

@CommandLine.Command(
        name = "all",
        description = "Run export then importId",
        mixinStandardHelpOptions = true)
public class AllCommand implements Runnable {

    @CommandLine.Option(
            names = {"-key", "--apikey"},
            required = true,
            description = "API key for Testomat.io")
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
            description = "Directory"
    )
    private String directory;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        CommandLine parent = spec.parent().commandLine();
        int code1 = parent.execute("export",
                "--apikey=" + apiKey,
                "--url=" + url,
                "--directory=" + directory);
        if (code1 != 0) {
            spec.commandLine().getErr().println("export failed with code " + code1);
            System.exit(code1);
        }
        int code2 = parent.execute("importId",
                "--apikey=" + apiKey,
                "--url=" + url,
                "--directory=" + directory);
        if (code2 != 0) {
            spec.commandLine().getErr().println("importId failed with code " + code2);
            System.exit(code2);
        }
    }
}
