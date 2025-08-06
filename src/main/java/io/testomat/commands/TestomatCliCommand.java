package io.testomat.commands;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "java -jar java-check-tests-{version}",
        description = "[Java-check-tests] v0.1.0",
        mixinStandardHelpOptions = true,
        version = "0.1.0",
        subcommands = {
                TestExportCommand.class,
                UpdateIdsCommand.class,
                CommandLine.HelpCommand.class,
                AllCommand.class,
                PurgeCommand.class,
                CleanIdsCommand.class
        }
)
public class TestomatCliCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("Testomat.io CLI v0.1.0");
        System.out.println();
        System.out.println("Available commands:");
        System.out.println("  export     - Export JUnit and TestNG test methods to testomat.io");
        System.out.println("  update-ids - Import test IDs into the codebase");
        System.out.println("  purge      - Remove @TestId annotations and imports from test files");
        System.out.println(
                "  clean-ids  - Remove @TestId annotations for tests that exist on the server");
        System.out.println("  all        - Run export then update-ids");
        System.out.println("  help       - Show help information");
        System.out.println();
        System.out.println("Use ' <command> --help' for more information on a command.");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new TestomatCliCommand()).execute(args);
        System.exit(exitCode);
    }
}
