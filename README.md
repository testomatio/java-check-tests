# Java-Check-Tests

Command-line interface for the Testomat.io Java Reporter library.

## Usage

This CLI tool can be used to:

- Export your test source code to the server
- Import test IDs from the server into your codebase
- Remove test IDs when needed

---

## Supported frameworks
| Framework |  Status  |
|-----------|:--------:|
| TestNG    |    ✅     |
| JUnit     |    ✅     |

> New frameworks support will be added soon.

---

## Commands

### `export`

Exports the code of your test methods to the server.

Use this command before running tests to see the code and have proper package structure in the UI.  
Will dry run if apikey is not provided.

>**Options:**
>- `--apikey` / `-key` - Your Testomat.io project API key (required)
>- `--url` - Server URL, e.g. https://app.testomat.io (required)
>- `--directory` / `-d` - Directory to scan (optional, defaults to current directory)
>- `--verbose` / `-v` - Enable verbose output (optional)
>- `--dry-run` - Show what would be exported without sending (optional)

### `import`

Imports test IDs from the server into your codebase and adds the necessary imports to test classes.

Should be used after the `export` command succeeds.

- IDs will be added as `@TestId` annotation values
- Automatically imports `io.testomat.core.annotation.TestId`
- Updates existing IDs if they have changed on the server  
**In general, please, use `update-ids`** to import IDs

>**Options:**
>- `--apikey` / `-key` - Your Testomat.io project API key (required)
>- `--url` - Server URL (required)
>- `--directory` / `-d` - Directory to scan (optional, defaults to current directory)

### `update-ids`

Executes `export` and `importId` commands sequentially.

Convenience command for typical workflow.

>**Options:**
>- `--apikey` / `-key` - Your Testomat.io project API key (required)
>- `--url` - Server URL (required)
>- `--directory` / `-d` - Directory to scan (optional, defaults to current directory)

### `purge`

Removes `@TestId` annotations and related imports from all classes in the directory recursively.  
**Runs locally only**

>**Options:**
>- `--directory` / `-d` - Directory to clean (optional, defaults to current directory)
>- `--verbose` / `-v` - Enable verbose output (optional)
>- `--dry-run` - Show what would be removed without making changes (optional)

### `clean-ids`
Removes `@TestId` annotations in the classes in the directory recursively, but only related to the project with  
the particular apikey that is provided as --apikey.

>- `--apikey` / `-key` - Your Testomat.io project API key (required)
>- `--url` - Server URL (required)
>- `--directory` / `-d` - Directory to scan (optional, defaults to current directory)

---

## Examples

```bash
    # Export tests
    java -jar java-check-tests-0.1.0.jar import --apikey tstmt_your_key --url https://app.testomat.io
    
    # Run export and import 
    java -jar java-check-tests-0.1.0.jar sync --apikey tstmt_your_key --url https://app.testomat.io

    # Clean up test IDs (locally)
    java -jar java-check-tests-0.1.0.jar purge --directory ./src/test/java

```
---

## Oneliners

You can use these oneliners to **download and update ids in one move  
(the `update-ids` command will be executed)



>- UNIX, MACOS:  
`export TESTOMATIO_URL=... && \export TESTOMATIO=... && curl -L -O https://github.com/testomatio/java-check-tests/releases/latest/download/java-check-tests.jar && java -jar java-check-tests.jar update-ids`

>- WINDOWS cdm:  
  `set TESTOMATIO_URL=...&& set TESTOMATIO=...&& curl -L -O https://github.com/testomatio/java-check-tests/releases/latest/download/java-check-tests.jar&& java -jar java-check-tests.jar update-ids`

**Where TESTOMATIO_URL is server url and TESTOMATIO is your porject api key.**  
**Be patient to the whitespaces in the Windows command.**
