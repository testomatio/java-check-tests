package io.testomat.commands;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "testomat",
        description = "Testomat.io CLI - Export tests and import IDs",
        mixinStandardHelpOptions = true,
        version = "1.0.0",
        subcommands = {
                TestExportCommand.class,
                UpdateIdsCommand.class,
                CommandLine.HelpCommand.class,
                AllCommand.class,
                PurgeCommand.class
        }
)
public class TestomatCliCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("Testomat.io CLI v0.1.0");
        System.out.println();
        System.out.println("Available commands:");
        System.out.println("  export    - Export JUnit and TestNG test methods to testomat.io");
        System.out.println("  importId  - Import test IDs into the codebase");
        System.out.println("  help      - Show help information");
        System.out.println();
        System.out.println("Use 'testomat <command> --help' for more information on a command.");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new TestomatCliCommand()).execute(args);
        System.exit(exitCode);
    }
}
