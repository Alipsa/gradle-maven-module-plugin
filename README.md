# gradle-maven-module-plugin

A Gradle plugin that allows Maven projects (with `pom.xml`) to participate in a Gradle multi-project build. It maps Gradle lifecycle tasks to Maven phases so that standard Gradle commands work seamlessly with Maven subprojects.

## Use Case

In a multi-project Gradle build where some subprojects are Maven projects:

```
root/
├── settings.gradle
├── gradle-module/
│   └── build.gradle          (standard Gradle Java project)
└── maven-module/
    ├── build.gradle           (applies this plugin)
    └── pom.xml
```

## Setup

Apply the plugin in the Maven subproject's `build.gradle` and declare your modules:

```groovy
plugins {
    id 'se.alipsa.gradle.maven-module' version '0.1.0-SNAPSHOT'
}

mavenModules {
    app {}
}
```

Include it in your root `settings.gradle`:

```groovy
rootProject.name = 'my-project'
include 'gradle-module', 'maven-module'
```

## Task Mapping

Each module named `<name>` gets tasks prefixed with `maven<Name>`:

| Gradle Lifecycle Task | Module Task (e.g. `app`) | Maven Phase |
|-----------------------|--------------------------|-------------|
| `clean`               | `mavenAppClean`          | `clean`     |
| `assemble`            | `mavenAppPackage`        | `package`   |
| `check`               | `mavenAppVerify`         | `verify`    |
| `build`               | (inherited)              | `assemble` + `check` |
| `publishToMavenLocal`* | `mavenAppInstall`        | `install`   |
| `publish`*             | `mavenAppDeploy`         | `deploy`    |

\* `publishToMavenLocal` and `publish` are only created and wired by this plugin when no other plugin (e.g. `maven-publish`) has already registered them. You can always invoke `maven<Name>Install` or `maven<Name>Deploy` directly.

Additional fine-grained tasks: `maven<Name>Compile`, `maven<Name>Test`

## Configuration

```groovy
mavenModules {
    app {
        // POM file to use (defaults to pom.xml)
        pomFile = file('bom.xml')

        // Maven executable (auto-detects mvnw if not set, or falls back to mvn on PATH)
        mavenExecutable = '/path/to/mvn'

        // Maven profiles to activate
        profiles = ['ci', 'integration']

        // System properties passed as -D flags
        systemProperties = ['skipTests': 'true', 'maven.javadoc.skip': 'true']

        // Additional CLI arguments
        args = ['--batch-mode', '-X']

        // Working directory (defaults to the POM file's parent directory)
        workingDir = file('myMavenProject')

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
        pomFile = file('myMavenProject/bom.xml')
    }
    app {
        pomFile = file('myMavenProject/pom.xml')
        mustRunAfter 'bom'
    }
}
```

This registers separate task sets (`mavenBom*` and `mavenApp*`). The `mustRunAfter` declaration ensures `bom` tasks complete before `app` tasks when both are requested. Lifecycle tasks like `build` aggregate all modules. The `workingDir` defaults to the POM file's parent directory.

Run individual module tasks directly:

```bash
./gradlew mavenBomInstall mavenAppVerify
```

## Maven Wrapper Support

The plugin automatically detects `mvnw` (or `mvnw.cmd` on Windows) in the project directory and parent directories. If found, it uses the wrapper instead of the system `mvn`. You can override this by setting `mavenExecutable`.

## POM Integration

The plugin parses each module's POM file and:

- **Sets project metadata** — `group`, `version`, and `description` are applied from the first module's POM (only when the project hasn't already set them). When the default `pom.xml` is present in the project directory, metadata is applied eagerly during configuration so other plugins can reference it. When the first module uses a non-default POM, metadata is applied after evaluation.
- **Exposes built artifacts** — Artifacts (e.g., `target/*.jar`) are registered in Gradle's dependency system so other Gradle modules can depend on the Maven module:

```groovy
// In another subproject's build.gradle
dependencies {
    implementation project(':maven-module')
}
```

## Building the Plugin

```bash
./gradlew build                  # Build and test
./gradlew publishToMavenLocal    # Publish to local Maven repo
```

## License

MIT License - see [LICENSE](LICENSE) for details.
