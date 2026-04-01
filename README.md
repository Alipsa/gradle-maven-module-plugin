# gradle-maven-module-plugin

[![CI](https://github.com/Alipsa/gradle-maven-module-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/Alipsa/gradle-maven-module-plugin/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-17+-blue)](https://docs.oracle.com/en/java/javase/17/)
[![Javadoc](https://javadoc.io/badge2/se.alipsa.gradle.maven-module/se.alipsa.gradle.maven-module.gradle.plugin/javadoc.svg)](https://javadoc.io/doc/se.alipsa.gradle.maven-module/se.alipsa.gradle.maven-module.gradle.plugin)

A Gradle plugin that allows Maven projects (with `pom.xml`) to participate in a Gradle multi-project build. It maps Gradle lifecycle tasks to Maven phases so that standard Gradle commands work seamlessly with Maven subprojects.

## Use Case

In a multi-project Gradle build where some subprojects are Maven projects:

```
root/
├── settings.gradle
├── gradle-module/
│   └── build.gradle              (standard Gradle Java project)
└── maven-module/
    ├── build.gradle              (applies this plugin)
    ├── pom.xml                   (main build)
    └── bom.xml                   (optional additional POM)
```

## Setup

Apply the plugin in the Maven subproject's `build.gradle` and declare your modules:

```groovy
plugins {
    id 'se.alipsa.gradle.maven-module' version '0.2.0'
}

mavenModules {
    app {}  // uses pom.xml by default
}
```

Include it in your root `settings.gradle`:

```groovy
rootProject.name = 'my-project'
include 'gradle-module', 'maven-module'
```

## Task Mapping

Each module named `<name>` gets tasks prefixed with `maven<Name>`:

| Gradle Lifecycle Task  | Module Task (e.g. `app`) | Maven Phase          |
|------------------------|--------------------------|----------------------|
| `clean`                | `mavenAppClean`          | `clean`              |
| `assemble`             | `mavenAppPackage`        | `package`            |
| `check`                | `mavenAppVerify`         | `verify`             |
| `build`                | (inherited)              | `assemble` + `check` |
| `publishToMavenLocal`* | `mavenAppInstall`        | `install`            |
| `publish`*             | `mavenAppDeploy`         | `deploy`             |

\* `publishToMavenLocal` and `publish` are only created and wired by this plugin when no other plugin (e.g. `maven-publish`) has already registered them. You can always invoke `maven<Name>Install` or `maven<Name>Deploy` directly.

Additional fine-grained tasks: `maven<Name>Compile`, `maven<Name>Test`

## Configuration

```groovy
mavenModules {
    app {
        // POM file (defaults to pom.xml in the project directory)
        pomFile = file('custom-pom.xml')

        // Maven executable (auto-detects mvnw, falls back to mvn on PATH)
        mavenExecutable = '/path/to/mvn'

        // Maven profiles to activate (-P)
        profiles = ['ci', 'integration']

        // System properties passed as -D flags
        systemProperties = ['skipTests': 'true', 'maven.javadoc.skip': 'true']

        // Additional CLI arguments
        args = ['--batch-mode', '-X']

        // Working directory (defaults to the POM file's parent directory)
        workingDir = file('some/other/dir')

        // Environment variables for the Maven process
        environment = ['JAVA_HOME': '/usr/lib/jvm/java-17']
    }
}
```

## Multiple Maven Modules

A single project directory can contain multiple POM files (e.g., a BOM and a main build). Define them as separate named modules and use `mustRunAfter` to control ordering:

```groovy
mavenModules {
    bom {
        pomFile = file('bom.xml')
    }
    app {
        // pomFile defaults to pom.xml
        mustRunAfter 'bom'
    }
}
```

This registers separate task sets (`mavenBom*` and `mavenApp*`). The `mustRunAfter` declaration ensures `bom` tasks complete before `app` tasks when both are requested. Lifecycle tasks like `build` aggregate all modules.

Run individual module tasks directly:

```bash
./gradlew mavenBomInstall mavenAppVerify
```

## Subproject Ordering

When Maven modules need to run before or after other Gradle subprojects, the plugin provides ordering methods that wire `mustRunAfter` constraints against standard lifecycle tasks (`clean`, `assemble`, `check`, `build`, `jar`, `publishToMavenLocal`, `publish`).

### Run after all subprojects

Use `mustRunAfterSubprojects()` when a Maven module depends on artifacts produced by other Gradle subprojects:

```groovy
mavenModules {
    app {
        mustRunAfterSubprojects()
    }
}
```

### Run before all subprojects

Use `mustRunBeforeSubprojects()` when other Gradle subprojects depend on this Maven module (e.g., a BOM or shared library):

```groovy
mavenModules {
    bom {
        pomFile = file('bom.xml')
        mustRunBeforeSubprojects()
    }
}
```

### Run after specific subprojects

Use `mustRunAfterSubproject` to order the Maven module after one or more named subprojects:

```groovy
mavenModules {
    app {
        mustRunAfterSubproject 'lib', 'common'
    }
}
```

### Run before specific subprojects

Use `mustRunBeforeSubproject` to ensure specific subprojects run after this Maven module:

```groovy
mavenModules {
    app {
        mustRunBeforeSubproject 'integration-tests'
    }
}
```

These can be combined freely with each other and with the intra-container `mustRunAfter` ordering.

### Depend on published subproject artifacts

When a Maven module depends on artifacts produced by Gradle subprojects, use `dependsOnPublishedSubproject` to ensure those artifacts are published to the local Maven repository (`~/.m2/repository`) before Maven runs. Unlike the `mustRunAfter` methods which only control ordering, this creates a hard `dependsOn` relationship — the subprojects' `publishToMavenLocal` tasks **will** execute automatically.

```groovy
mavenModules {
    app {
        // Publish specific subprojects to local Maven repo before Maven runs
        dependsOnPublishedSubproject 'lib', 'common'
    }
}
```

To depend on all subprojects:

```groovy
mavenModules {
    app {
        dependsOnAllPublishedSubprojects()
    }
}
```

**Note:** The specified subprojects must have the `maven-publish` plugin applied (which provides the `publishToMavenLocal` task). The plugin will warn if a subproject lacks this task.

## Maven Wrapper Support

The plugin automatically detects `mvnw` (or `mvnw.cmd` on Windows) in the working directory and parent directories. If found, it uses the wrapper instead of the system `mvn`. You can override this by setting `mavenExecutable`.

## POM Integration

The plugin parses each module's POM file and:

- **Sets project metadata** — `group`, `version`, and `description` are set from the POM (only when the project hasn't already set them). If a `pom.xml` exists in the project directory it is parsed eagerly at plugin-apply time, so the values are available to other plugins during configuration. If no default `pom.xml` is present, metadata is applied after evaluation from the first module's configured `pomFile`.
- **Exposes built artifacts** — Artifacts (e.g., `target/*.jar`) are registered in Gradle's dependency system so other Gradle modules can depend on the Maven module:

```groovy
// In another subproject's build.gradle
dependencies {
    implementation project(':maven-module')
}
```
## Plugin portal and Maven Central Publications
This plugin is published to both the Gradle Plugin Portal and Maven Central. The plugin coordinates are:
- Gradle Plugin Portal: `se.alipsa.gradle.maven-module` (use `id 'se.alipsa.gradle.maven-module' version '0.2.0'` in `build.gradle`)
- Maven Central: 
  ```xml
  <groupId>se.alipsa.gradle.maven-module</groupId>
  <artifactId>se.alipsa.gradle.maven-module.gradle.plugin</artifactId>
  ```
The reason for the different artifactId in Maven Central is to avoid confusion with the plugin ID and to follow a common convention for Gradle plugins published to Maven Central. It also means that if the pluginPortal is unavailable to you for some reason you can easily switch to the Maven Central artifact by adding the following to your settings.gradle:
```groovy
pluginManagement {
  repositories {
    mavenCentral()
  }
}
```
No changes to the plugin id in build.gradle are needed.

## Building the Plugin

```bash
./gradlew build                  # Build and test
./gradlew publishToMavenLocal    # Publish to local Maven repo
```

## License

MIT License - see [LICENSE](LICENSE) for details.
