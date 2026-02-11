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