package io.testomat.commands;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "main",
        mixinStandardHelpOptions = true,
        subcommands = {
                ImportCommand.class,
                PullIdsCommand.class,
                CommandLine.HelpCommand.class,
                SyncCommand.class,
                CleanIdsCommand.class,
                CleanProjectIdsCommand.class
        }
)
public class TestomatCliCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("Testomat.io CLI v0.1.x");
        System.out.println();
        System.out.println("Available commands:");
        System.out.println("  import     - Export JUnit and TestNG test methods to testomat.io");
        System.out.println("  pull-ids - Import test IDs into the codebase");
        System.out.println("  clean-ids      - Remove @TestId annotations and imports from test files");
        System.out.println(
                "  clean-project-ids  - Remove @TestId annotations for tests that exist on the server");
        System.out.println("  sync        - Run export then update-ids. Alias `update-ids`");
        System.out.println("  help       - Show help information");
        System.out.println();
        System.out.println("Use ' <command> --help' for more information on a command.");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new TestomatCliCommand()).execute(args);
        System.exit(exitCode);
    }
}
