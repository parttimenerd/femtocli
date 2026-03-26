# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
### Changed
### Deprecated
### Removed
### Fixed
### Security

## [0.3.8] - 2026-03-26

### Fixed
- Same reflection issue as before

## [0.3.7] - 2026-03-26

### Fixed
- Issue with reflection and setAccessible on non-annotated fields

## [0.3.6] - 2026-03-23

### Added
- Add mutually exclusive options using {@link Option#prevents()}

## [0.3.5] - 2026-03-18

### Added
- FemtoCli.builder().removeCommands(Class<?>...) to remove commands dynamically at runtime

## [0.3.4] - 2026-03-16

### Fixed
- `customSynopsis` now renders with `Usage: ` prefix, consistent with auto-generated synopsis
- Commands with both positional parameters and subcommands now correctly forward remaining args to subcommands (e.g., `app <PID> start --verbose` no longer reports `--verbose` as unknown on the parent command)

## [0.3.3] - 2026-03-16

### Added
- Bare `help` token is now recognized in subcommand position (e.g., `app help` is equivalent to `app --help`)

### Changed
- Synopsis line now renders positional parameters before `[COMMAND]` (e.g., `Usage: app PID [COMMAND]` instead of `Usage: app [COMMAND] PID`)
- Converter exceptions now preserve the original error message instead of discarding it (e.g., `Invalid value for <file>: File does not exist: foo.jfr` instead of `Invalid value for <file>: foo.jfr`)

### Fixed
- `@Parameters(converter = ...)` and `@Parameters(converterMethod = ...)` are now applied when binding positional arguments (previously they were silently ignored)

### Security

## [0.3.2] - 2026-03-06

### Added
- Support for default commands

## [0.3.1] - 2026-03-06

### Added
- Support for accessing parent command options in subcommands

## [0.3.0] - 2026-02-16

### Added
- Suggest similar options when an unknown option is provided (e.g., `tip: a similar argument exists: '--input-file'`)
- `CommandConfig.suggestSimilarOptions` option to enable/disable similar option suggestions (enabled by default)
- Custom joiner syntax for `${COMPLETION-CANDIDATES:$JOINER}` placeholder

## [0.2.2] - 2026-02-15

### Changed
- Release as JDK 17 JAR

## [0.2.1] - 2026-02-13

### Changed
- `run` and `runCaptured` methods now accepts varargs of `String` instead of `String[]` for better usability

## [0.2.0] - 2026-02-11

### Changed
- Rename to femtocli, as there is already a CLI library named FemtoCli for PHP

## [0.1.13] - 2026-02-11

## [0.1.12] - 2026-02-11

### Added
- New unit tests: `BooleanOptionConverterTest` and `MixinConstructorAccessibilityTest` to cover converter-backed boolean parsing and mixin constructor accessibility.

### Fixed
- Boolean options that declare a per-option converter (via `converter` or `converterMethod`) or that have a registered converter are now treated as value-taking options. This ensures values like `--turn=on` or `--turn on` are passed to the converter instead of being parsed as a flag.
- Mixin initialization now makes the mixin constructor accessible before instantiation, avoiding `IllegalAccessException` for package-private/non-public mixin constructors used in tests and user code.
- Fix discovery of converter and verifier methods

### Changed
### Deprecated
### Removed
### Security

## [0.1.11] - 2026-02-11

### Added
- Agent argument support

## [0.1.10] - 2026-02-10

### Added
- Add `@IgnoreOptions` to ignore options from parent commands or mixin in subcommands

### Fixed
- Boolean options now accept explicit values as separate tokens (e.g. `--flag false`), in addition to `--flag` and `--flag=false`

## [0.1.9] - 2026-01-31

### Added
- Added basic `FemtoCli#run` method

### Fixed
- Subcommand handling in help
- Handling of private constructors and fields

## [0.1.8] - 2026-01-30

### Added
- `Command#hidden` to hide commands from help outpu in parent commands
- `Option#hidden` to hide options from help output in commands
- `Command#footer` to add custom footers to help output in commands

## [0.1.7] - 2026-01-28

### Added
- Support Java 17

## [0.1.6] - 2026-01-27

## [0.1.5] - 2026-01-27

### Added
- More flexible converters
- Verifier for options

## [0.1.4] - 2026-01-27

### Added
- Spec mechanism to access usage and print streams at runtime in commands

## [0.1.3] - 2026-01-27

### Fixed
- Hopefully fixed default value handling

## [0.1.2] - 2026-01-27

### Added
- Support for `Duration` parameters

## [0.1.1] - 2026-01-27


## [0.1.0] - 2026-01-27

### Added
- Initial implementation
