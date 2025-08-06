# Java-Check-Tests

Command-line interface for the Testomat.io Java Reporter library.

## Usage

This CLI tool can be used to:

- Export your test source code to the server
- Import test IDs from the server into your codebase
- Remove test IDs when needed

## Commands

### `export`

Exports the code of your test methods to the server.

Use this command before running tests to see the code and have proper package structure in the UI.

>**Options:**
>- `--apikey` / `-key` - Your Testomat.io project API key (required)
>- `--url` - Server URL, e.g. https://app.testomat.io (required)
>- `--directory` / `-d` - Directory to scan (optional, defaults to current directory)
>- `--verbose` / `-v` - Enable verbose output (optional)
>- `--dry-run` - Show what would be exported without sending (optional)

### `importId`

Imports test IDs from the server into your codebase and adds the necessary imports to test classes.

Should be used after the `export` command succeeds.

- IDs will be added as `@TestId` annotation values
- Automatically imports `io.testomat.core.annotation.TestId`
- Updates existing IDs if they have changed on the server

>**Options:**
>- `--apikey` / `-key` - Your Testomat.io project API key (required)
>- `--url` - Server URL (required)
>- `--directory` / `-d` - Directory to scan (optional, defaults to current directory)

### `all`

Executes `export` and `importId` commands sequentially.

Convenience command for typical workflow.

>**Options:**
>- `--apikey` / `-key` - Your Testomat.io project API key (required)
>- `--url` - Server URL (required)
>- `--directory` / `-d` - Directory to scan (optional, defaults to current directory)


### `wipeId`

Removes `@TestId` annotations and related imports from all classes in the directory recursively.

>**Options:**
>- `--directory` / `-d` - Directory to clean (optional, defaults to current directory)
>- `--verbose` / `-v` - Enable verbose output (optional)
>- `--dry-run` - Show what would be removed without making changes (optional)

## Examples

```bash
    # Export tests
    java -jar java-check-tests-0.1.0.jar export --apikey tstmt_your_key --url https://app.testomat.io
    
    # Import IDs back to code  
    java -jar java-check-tests-0.1.0.jar importId --apikey tstmt_your_key --url https://app.testomat.io
    
    # Run export and import 
    java -jar java-check-tests-0.1.0.jar all --apikey tstmt_your_key --url https://app.testomat.io
    
    # Clean up test IDs
    java -jar java-check-tests-0.1.0.jar wipeId --directory ./src/test/java
```