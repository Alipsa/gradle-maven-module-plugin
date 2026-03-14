# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Gradle plugin (`se.alipsa.gradle.maven-module`) that allows Maven projects with `pom.xml` to participate in Gradle multi-project builds. It maps Gradle lifecycle tasks to Maven phases (e.g., `assemble`→`package`, `check`→`verify`). Modules are configured via a `mavenModules` container, supporting multiple Maven modules per Gradle project.

## Build Commands

```bash
./gradlew build                  # Build, test (unit + functional), and validate
./gradlew test                   # Unit tests only
./gradlew functionalTest         # Functional tests only (uses GradleRunner/TestKit)
./gradlew publishToMavenLocal    # Publish plugin to local Maven repo

# Run a single test
./gradlew test --tests MavenModulePluginTest.pluginAppliesSuccessfully
./gradlew functionalTest --tests MavenModulePluginFunctionalTest.cleanTaskInvokesMavenClean

# Test with a specific Gradle version
./gradlew functionalTest -DtestGradleVersion=8.14.4
```

## Architecture

Three classes in `se.alipsa.gradle.mavenmodule`:

- **MavenModulePlugin** — Plugin entry point. Applies `BasePlugin`, creates the `mavenModules` container (`NamedDomainObjectContainer<MavenModule>`), registers per-module `maven<Name><Phase>` tasks, wires them to Gradle lifecycle tasks, parses POM files for artifact integration, and supports cross-module ordering via `mustRunAfter`.

- **MavenExecTask** — Custom task that invokes a Maven phase via CLI. Uses `ExecOperations` for process execution. Auto-detects `mvnw` in project/parent directories, falls back to system `mvn`. Supports `-f` flag for custom POM files. Annotated `@DisableCachingByDefault` since Maven execution is not cacheable.

- **MavenModule** — Per-module configuration within the `mavenModules` container. Uses Gradle's `Property`/`ListProperty`/`MapProperty` API for lazy configuration (`pomFile`, `mavenExecutable`, `profiles`, `systemProperties`, `args`, `workingDir`, `environment`). Supports `mustRunAfter` for cross-module ordering.

## Testing

- **Unit tests** (`src/test/`) use `ProjectBuilder` for lightweight in-memory testing of plugin configuration, task registration, POM parsing, command-line building, and multi-module container behavior. Requires `gradleApi()` as explicit `testImplementation` dependency (Gradle 9.4+ moved it to `compileOnlyApi`).

- **Functional tests** (`src/functionalTest/`) use `GradleRunner` (TestKit) to execute real builds in temp directories with actual Maven invocations. Supports testing with different Gradle versions via `-DtestGradleVersion`. Registered via `gradlePlugin.testSourceSets()`.

## Key Gradle 9.4 Considerations

- `java-gradle-plugin` adds `gradleApi()` to `compileOnlyApi` (not `api`), so tests need explicit `testImplementation gradleApi()`.
- Published plugin tasks must have `@DisableCachingByDefault` or `@CacheableTask` annotation.
