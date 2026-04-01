package se.alipsa.gradle.mavenmodule;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MavenModulePluginFunctionalTest {

    @TempDir
    File projectDir;

    @BeforeEach
    void setUp() throws IOException {
        writeFile("build.gradle", """
                plugins {
                    id 'se.alipsa.gradle.maven-module'
                }
                mavenModules {
                    app {}
                }
                """);

        writeFile("settings.gradle", """
                rootProject.name = 'test-maven-module'
                """);

        writeFile("pom.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-maven-module</artifactId>
                    <version>1.0.0</version>
                    <packaging>jar</packaging>
                </project>
                """);

        Path srcDir = projectDir.toPath()
                .resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        Files.writeString(srcDir.resolve("Hello.java"), """
                package com.example;

                public class Hello {
                    public String greet() {
                        return "Hello from Maven module!";
                    }
                }
                """);
    }

    @Test
    void cleanTaskInvokesMavenClean() {
        BuildResult result = createRunner("clean")
                .build();

        assertEquals(TaskOutcome.SUCCESS,
                result.task(":mavenAppClean").getOutcome());
    }

    @Test
    void assembleTaskInvokesMavenPackage() {
        BuildResult result = createRunner("assemble")
                .build();

        assertEquals(TaskOutcome.SUCCESS,
                result.task(":mavenAppPackage").getOutcome());

        File artifact = new File(projectDir, "target/test-maven-module-1.0.0.jar");
        assertTrue(artifact.exists(), "Maven should have produced the jar artifact");
    }

    @Test
    void checkTaskInvokesMavenVerify() {
        BuildResult result = createRunner("check")
                .build();

        assertEquals(TaskOutcome.SUCCESS,
                result.task(":mavenAppVerify").getOutcome());
    }

    @Test
    void buildTaskRunsAssembleAndCheck() {
        BuildResult result = createRunner("build")
                .build();

        assertNotNull(result.task(":mavenAppPackage"));
        assertNotNull(result.task(":mavenAppVerify"));
        assertEquals(TaskOutcome.SUCCESS,
                result.task(":build").getOutcome());
    }

    @Test
    void publishToMavenLocalInvokesMavenInstall() {
        BuildResult result = createRunner("publishToMavenLocal")
                .build();

        assertEquals(TaskOutcome.SUCCESS,
                result.task(":mavenAppInstall").getOutcome());
    }

    @Test
    void mavenTasksCanBeRunDirectly() {
        BuildResult result = createRunner("mavenAppCompile")
                .build();

        assertEquals(TaskOutcome.SUCCESS,
                result.task(":mavenAppCompile").getOutcome());
    }

    @Test
    void customProfilesAndPropertiesArePassedToMaven() throws IOException {
        writeFile("build.gradle", """
                plugins {
                    id 'se.alipsa.gradle.maven-module'
                }

                mavenModules {
                    app {
                        systemProperties = ['skipTests': 'true']
                        args = ['--batch-mode']
                    }
                }
                """);

        BuildResult result = createRunner("assemble", "--info")
                .build();

        assertEquals(TaskOutcome.SUCCESS,
                result.task(":mavenAppPackage").getOutcome());
        assertTrue(result.getOutput().contains("-DskipTests=true"));
        assertTrue(result.getOutput().contains("--batch-mode"));
    }

    @Test
    void taskDependenciesAreCorrect() {
        BuildResult result = createRunner("tasks", "--all")
                .build();

        String output = result.getOutput();
        assertTrue(output.contains("mavenAppClean"));
        assertTrue(output.contains("mavenAppCompile"));
        assertTrue(output.contains("mavenAppTest"));
        assertTrue(output.contains("mavenAppPackage"));
        assertTrue(output.contains("mavenAppVerify"));
        assertTrue(output.contains("mavenAppInstall"));
        assertTrue(output.contains("mavenAppDeploy"));
    }

    @Test
    void multipleModulesWithCustomPomFile() throws IOException {
        writeFile("bom.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-bom</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>
                </project>
                """);

        writeFile("build.gradle", """
                plugins {
                    id 'se.alipsa.gradle.maven-module'
                }
                mavenModules {
                    bom {
                        pomFile = file('bom.xml')
                    }
                    app {
                        mustRunAfter 'bom'
                    }
                }
                """);

        // Verify both module tasks are registered
        BuildResult result = createRunner("tasks", "--all")
                .build();

        String output = result.getOutput();
        assertTrue(output.contains("mavenBomInstall"));
        assertTrue(output.contains("mavenBomPackage"));
        assertTrue(output.contains("mavenAppPackage"));
        assertTrue(output.contains("mavenAppInstall"));
    }

    @Test
    void multipleModulesRunInOrder() throws IOException {
        writeFile("bom.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-bom</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>
                </project>
                """);

        writeFile("build.gradle", """
                plugins {
                    id 'se.alipsa.gradle.maven-module'
                }
                mavenModules {
                    bom {
                        pomFile = file('bom.xml')
                    }
                    app {
                        mustRunAfter 'bom'
                    }
                }
                """);

        // Run install for both — bom should install before app starts
        BuildResult result = createRunner("mavenBomInstall", "mavenAppInstall")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":mavenBomInstall").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":mavenAppInstall").getOutcome());

        // Verify ordering: bom install appears before app install in output
        String output = result.getOutput();
        int bomPos = output.indexOf("mavenBomInstall");
        int appPos = output.indexOf("mavenAppInstall");
        assertTrue(bomPos < appPos, "bom install should run before app install");
    }

    @Test
    void pomMetadataIsAppliedToProject() throws IOException {
        writeFile("pom.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-maven-module</artifactId>
                    <version>2.5.0</version>
                    <packaging>jar</packaging>
                    <description>A test module description</description>
                </project>
                """);

        writeFile("build.gradle", """
                plugins {
                    id 'se.alipsa.gradle.maven-module'
                }
                mavenModules {
                    app {}
                }
                task showMetadata {
                    doLast {
                        println "PROJECT_GROUP=${project.group}"
                        println "PROJECT_VERSION=${project.version}"
                        println "PROJECT_DESCRIPTION=${project.description}"
                    }
                }
                """);

        BuildResult result = createRunner("showMetadata")
                .build();

        String output = result.getOutput();
        assertTrue(output.contains("PROJECT_GROUP=com.example"));
        assertTrue(output.contains("PROJECT_VERSION=2.5.0"));
        assertTrue(output.contains("PROJECT_DESCRIPTION=A test module description"));
    }

    @Test
    void mustRunAfterSubprojectsOrdersMavenTasksAfterSubprojectTasks() throws IOException {
        writeFile("settings.gradle", """
                rootProject.name = 'test-root'
                include 'lib'
                """);

        writeFile("build.gradle", """
                plugins {
                    id 'se.alipsa.gradle.maven-module'
                }
                mavenModules {
                    app {
                        mustRunAfterSubprojects()
                    }
                }
                """);

        writeFile("lib/build.gradle", """
                plugins {
                    id 'java'
                }
                """);

        writeFile("lib/src/main/java/com/example/Lib.java", """
                package com.example;
                public class Lib {}
                """);

        BuildResult result = createRunner("assemble")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":mavenAppPackage").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":lib:jar").getOutcome());

        String output = result.getOutput();
        int libJar = output.indexOf(":lib:jar");
        int mavenPackage = output.indexOf(":mavenAppPackage");
        assertTrue(libJar >= 0 && mavenPackage >= 0, "Both tasks should appear in output");
        assertTrue(libJar < mavenPackage,
                "lib:jar should run before mavenAppPackage");
    }

    @Test
    void mustRunBeforeSubprojectsOrdersMavenTasksBeforeSubprojectTasks() throws IOException {
        writeFile("settings.gradle", """
                rootProject.name = 'test-root'
                include 'lib'
                """);

        writeFile("build.gradle", """
                plugins {
                    id 'se.alipsa.gradle.maven-module'
                }
                mavenModules {
                    app {
                        mustRunBeforeSubprojects()
                    }
                }
                """);

        writeFile("lib/build.gradle", """
                plugins {
                    id 'java'
                }
                """);

        writeFile("lib/src/main/java/com/example/Lib.java", """
                package com.example;
                public class Lib {}
                """);

        BuildResult result = createRunner("assemble")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":mavenAppPackage").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":lib:jar").getOutcome());

        String output = result.getOutput();
        int mavenPackage = output.indexOf(":mavenAppPackage");
        int libJar = output.indexOf(":lib:jar");
        assertTrue(mavenPackage >= 0 && libJar >= 0, "Both tasks should appear in output");
        assertTrue(mavenPackage < libJar,
                "mavenAppPackage should run before lib:jar");
    }

    @Test
    void mustRunAfterSubprojectOrdersAfterNamedSubproject() throws IOException {
        writeFile("settings.gradle", """
                rootProject.name = 'test-root'
                include 'lib'
                include 'other'
                """);

        writeFile("build.gradle", """
                plugins {
                    id 'se.alipsa.gradle.maven-module'
                }
                mavenModules {
                    app {
                        mustRunAfterSubproject 'lib'
                    }
                }
                """);

        writeFile("lib/build.gradle", """
                plugins {
                    id 'java'
                }
                """);

        writeFile("lib/src/main/java/com/example/Lib.java", """
                package com.example;
                public class Lib {}
                """);

        writeFile("other/build.gradle", """
                plugins {
                    id 'java'
                }
                """);

        writeFile("other/src/main/java/com/example/Other.java", """
                package com.example;
                public class Other {}
                """);

        BuildResult result = createRunner("assemble")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":mavenAppPackage").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":lib:jar").getOutcome());

        String output = result.getOutput();
        int libJar = output.indexOf(":lib:jar");
        int mavenPackage = output.indexOf(":mavenAppPackage");
        assertTrue(libJar >= 0 && mavenPackage >= 0, "Both tasks should appear in output");
        assertTrue(libJar < mavenPackage,
                "lib:jar should run before mavenAppPackage");
    }

    @Test
    void dependsOnPublishedSubprojectPublishesBeforeMavenBuild() throws IOException {
        writeFile("settings.gradle", """
                rootProject.name = 'test-root'
                include 'lib'
                """);

        writeFile("build.gradle", """
                plugins {
                    id 'se.alipsa.gradle.maven-module'
                }
                mavenModules {
                    app {
                        dependsOnPublishedSubproject 'lib'
                    }
                }
                """);

        writeFile("lib/build.gradle", """
                plugins {
                    id 'java'
                    id 'maven-publish'
                }
                group = 'com.example'
                version = '1.0.0'
                publishing {
                    publications {
                        maven(MavenPublication) {
                            from components.java
                        }
                    }
                }
                """);

        writeFile("lib/src/main/java/com/example/Lib.java", """
                package com.example;
                public class Lib {}
                """);

        BuildResult result = createRunner("assemble")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":mavenAppPackage").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":lib:publishToMavenLocal").getOutcome());

        // publishToMavenLocal must run before Maven build phases
        String output = result.getOutput();
        int publishPos = output.indexOf(":lib:publishToMavenLocal");
        int mavenPackage = output.indexOf(":mavenAppPackage");
        assertTrue(publishPos >= 0 && mavenPackage >= 0, "Both tasks should appear in output");
        assertTrue(publishPos < mavenPackage,
                "lib:publishToMavenLocal should run before mavenAppPackage");
    }

    @Test
    void dependsOnAllPublishedSubprojectsPublishesAllBeforeMavenBuild() throws IOException {
        writeFile("settings.gradle", """
                rootProject.name = 'test-root'
                include 'lib'
                include 'core'
                """);

        writeFile("build.gradle", """
                plugins {
                    id 'se.alipsa.gradle.maven-module'
                }
                mavenModules {
                    app {
                        dependsOnAllPublishedSubprojects()
                    }
                }
                """);

        writeFile("lib/build.gradle", """
                plugins {
                    id 'java'
                    id 'maven-publish'
                }
                group = 'com.example'
                version = '1.0.0'
                publishing {
                    publications {
                        maven(MavenPublication) {
                            from components.java
                        }
                    }
                }
                """);

        writeFile("lib/src/main/java/com/example/Lib.java", """
                package com.example;
                public class Lib {}
                """);

        writeFile("core/build.gradle", """
                plugins {
                    id 'java'
                    id 'maven-publish'
                }
                group = 'com.example'
                version = '1.0.0'
                publishing {
                    publications {
                        maven(MavenPublication) {
                            from components.java
                        }
                    }
                }
                """);

        writeFile("core/src/main/java/com/example/Core.java", """
                package com.example;
                public class Core {}
                """);

        BuildResult result = createRunner("assemble")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":mavenAppPackage").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":lib:publishToMavenLocal").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":core:publishToMavenLocal").getOutcome());

        String output = result.getOutput();
        int libPublish = output.indexOf(":lib:publishToMavenLocal");
        int corePublish = output.indexOf(":core:publishToMavenLocal");
        int mavenPackage = output.indexOf(":mavenAppPackage");
        assertTrue(libPublish < mavenPackage,
                "lib:publishToMavenLocal should run before mavenAppPackage");
        assertTrue(corePublish < mavenPackage,
                "core:publishToMavenLocal should run before mavenAppPackage");
    }

    @Test
    void dependsOnAllPublishedSubprojectsExcludesNamedSubprojects() throws IOException {
        writeFile("settings.gradle", """
                rootProject.name = 'test-root'
                include 'lib'
                include 'examples'
                """);

        writeFile("build.gradle", """
                plugins {
                    id 'se.alipsa.gradle.maven-module'
                }
                mavenModules {
                    app {
                        dependsOnAllPublishedSubprojects {
                            exclude 'examples'
                        }
                    }
                }
                """);

        writeFile("lib/build.gradle", """
                plugins {
                    id 'java'
                    id 'maven-publish'
                }
                group = 'com.example'
                version = '1.0.0'
                publishing {
                    publications {
                        maven(MavenPublication) {
                            from components.java
                        }
                    }
                }
                """);

        writeFile("lib/src/main/java/com/example/Lib.java", """
                package com.example;
                public class Lib {}
                """);

        writeFile("examples/build.gradle", """
                plugins {
                    id 'java'
                }
                """);

        writeFile("examples/src/main/java/com/example/Example.java", """
                package com.example;
                public class Example {}
                """);

        BuildResult result = createRunner("assemble")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":mavenAppPackage").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":lib:publishToMavenLocal").getOutcome());

        // examples should NOT have publishToMavenLocal triggered
        assertNull(result.task(":examples:publishToMavenLocal"));
    }

    @Test
    void dependsOnAllPublishedSubprojectsExcludesGroup() throws IOException {
        writeFile("settings.gradle", """
                rootProject.name = 'test-root'
                include 'lib'
                include 'examples:demo'
                """);

        writeFile("build.gradle", """
                plugins {
                    id 'se.alipsa.gradle.maven-module'
                }
                mavenModules {
                    app {
                        dependsOnAllPublishedSubprojects {
                            exclude group: 'examples'
                        }
                    }
                }
                """);

        writeFile("lib/build.gradle", """
                plugins {
                    id 'java'
                    id 'maven-publish'
                }
                group = 'com.example'
                version = '1.0.0'
                publishing {
                    publications {
                        maven(MavenPublication) {
                            from components.java
                        }
                    }
                }
                """);

        writeFile("lib/src/main/java/com/example/Lib.java", """
                package com.example;
                public class Lib {}
                """);

        writeFile("examples/demo/build.gradle", """
                plugins {
                    id 'java'
                }
                """);

        writeFile("examples/demo/src/main/java/com/example/Demo.java", """
                package com.example;
                public class Demo {}
                """);

        BuildResult result = createRunner("assemble")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":mavenAppPackage").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":lib:publishToMavenLocal").getOutcome());

        // examples:demo should NOT have publishToMavenLocal triggered
        assertNull(result.task(":examples:demo:publishToMavenLocal"));
    }

    @Test
    void configurationCacheIsSupported() {
        // First run: stores the configuration cache entry
        BuildResult first = createRunner("--configuration-cache", "build")
                .build();
        assertTrue(first.getOutput().contains("Configuration cache entry stored"),
                "First run should store a configuration cache entry");

        // Second run: must reuse the cached configuration
        BuildResult second = createRunner("--configuration-cache", "build")
                .build();
        assertTrue(second.getOutput().contains("Reusing configuration cache"),
                "Second run should reuse the configuration cache entry");
    }

    @Test
    void mustRunBeforeSubprojectOrdersBeforeNamedSubproject() throws IOException {
        writeFile("settings.gradle", """
                rootProject.name = 'test-root'
                include 'lib'
                """);

        writeFile("build.gradle", """
                plugins {
                    id 'se.alipsa.gradle.maven-module'
                }
                mavenModules {
                    app {
                        mustRunBeforeSubproject 'lib'
                    }
                }
                """);

        writeFile("lib/build.gradle", """
                plugins {
                    id 'java'
                }
                """);

        writeFile("lib/src/main/java/com/example/Lib.java", """
                package com.example;
                public class Lib {}
                """);

        BuildResult result = createRunner("assemble")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":mavenAppPackage").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":lib:jar").getOutcome());

        String output = result.getOutput();
        int mavenPackage = output.indexOf(":mavenAppPackage");
        int libJar = output.indexOf(":lib:jar");
        assertTrue(mavenPackage >= 0 && libJar >= 0, "Both tasks should appear in output");
        assertTrue(mavenPackage < libJar,
                "mavenAppPackage should run before lib:jar");
    }

    private GradleRunner createRunner(String... arguments) {
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments(arguments)
                .withPluginClasspath()
                .forwardOutput();

        String testGradleVersion = System.getProperty("testGradleVersion", "");
        if (!testGradleVersion.isEmpty()) {
            runner.withGradleVersion(testGradleVersion);
        }

        return runner;
    }

    private void writeFile(String path, String content) throws IOException {
        Path filePath = projectDir.toPath().resolve(path);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
}
