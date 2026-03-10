package se.alipsa.gradle.mavenmodule;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MavenModulePluginTest {

    @TempDir
    File projectDir;

    @BeforeEach
    void setUp() throws IOException {
        // Create a minimal pom.xml
        File pomFile = new File(projectDir, "pom.xml");
        Files.writeString(pomFile.toPath(), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-module</artifactId>
                    <version>1.0.0</version>
                    <packaging>jar</packaging>
                    <description>A test module</description>
                </project>
                """);
    }

    @Test
    void pluginAppliesSuccessfully() {
        Project project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build();

        project.getPlugins().apply("se.alipsa.gradle.maven-module");

        assertNotNull(project.getExtensions().findByName("mavenModule"));
    }

    @Test
    void pluginSetsProjectMetadataFromPom() {
        Project project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build();

        project.getPlugins().apply("se.alipsa.gradle.maven-module");

        assertEquals("com.example", project.getGroup());
        assertEquals("1.0.0", project.getVersion());
        assertEquals("A test module", project.getDescription());
    }

    @Test
    void pluginRegistersAllMavenTasks() {
        Project project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build();

        project.getPlugins().apply("se.alipsa.gradle.maven-module");

        assertNotNull(project.getTasks().findByName("mavenClean"));
        assertNotNull(project.getTasks().findByName("mavenCompile"));
        assertNotNull(project.getTasks().findByName("mavenTest"));
        assertNotNull(project.getTasks().findByName("mavenPackage"));
        assertNotNull(project.getTasks().findByName("mavenVerify"));
        assertNotNull(project.getTasks().findByName("mavenInstall"));
        assertNotNull(project.getTasks().findByName("mavenDeploy"));
    }

    @Test
    void pluginRegistersLifecycleTasks() {
        Project project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build();

        project.getPlugins().apply("se.alipsa.gradle.maven-module");

        assertNotNull(project.getTasks().findByName("clean"));
        assertNotNull(project.getTasks().findByName("assemble"));
        assertNotNull(project.getTasks().findByName("check"));
        assertNotNull(project.getTasks().findByName("build"));
        assertNotNull(project.getTasks().findByName("publishToMavenLocal"));
        assertNotNull(project.getTasks().findByName("publish"));
    }

    @Test
    void lifecycleTasksDependOnMavenTasks() {
        Project project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build();

        project.getPlugins().apply("se.alipsa.gradle.maven-module");

        assertTrue(project.getTasks().getByName("clean")
                .getDependsOn().stream().anyMatch(d -> d.toString().contains("mavenClean")));
        assertTrue(project.getTasks().getByName("assemble")
                .getDependsOn().stream().anyMatch(d -> d.toString().contains("mavenPackage")));
        assertTrue(project.getTasks().getByName("check")
                .getDependsOn().stream().anyMatch(d -> d.toString().contains("mavenVerify")));
    }

    @Test
    void extensionDefaultsAreCorrect() {
        Project project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build();

        project.getPlugins().apply("se.alipsa.gradle.maven-module");

        MavenModuleExtension ext = project.getExtensions()
                .getByType(MavenModuleExtension.class);

        assertFalse(ext.getMavenExecutable().isPresent());
        assertEquals(projectDir, ext.getWorkingDir().get());
        assertTrue(ext.getProfiles().get().isEmpty());
        assertTrue(ext.getSystemProperties().get().isEmpty());
        assertTrue(ext.getArgs().get().isEmpty());
        assertTrue(ext.getEnvironment().get().isEmpty());
    }

    @Test
    void pomParsingHandlesParentInheritance() throws IOException {
        // Overwrite pom.xml with parent-based version
        File pomFile = new File(projectDir, "pom.xml");
        Files.writeString(pomFile.toPath(), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>com.example.parent</groupId>
                        <artifactId>parent-pom</artifactId>
                        <version>2.0.0</version>
                    </parent>
                    <artifactId>child-module</artifactId>
                </project>
                """);

        Project project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build();

        project.getPlugins().apply("se.alipsa.gradle.maven-module");

        assertEquals("com.example.parent", project.getGroup());
        assertEquals("2.0.0", project.getVersion());
    }

    @Test
    void commandLineBuildingIncludesProfilesAndProperties() {
        Project project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build();

        project.getPlugins().apply("se.alipsa.gradle.maven-module");

        MavenExecTask task = (MavenExecTask) project.getTasks().getByName("mavenCompile");
        task.getProfiles().set(List.of("ci", "integration"));
        task.getSystemProperties().put("skipTests", "true");
        task.getArgs().set(List.of("-X", "--batch-mode"));

        List<String> cmd = task.buildCommandLine("mvn");

        assertTrue(cmd.contains("mvn"));
        assertTrue(cmd.contains("compile"));
        assertTrue(cmd.contains("-P"));
        assertTrue(cmd.contains("ci,integration"));
        assertTrue(cmd.contains("-DskipTests=true"));
        assertTrue(cmd.contains("-X"));
        assertTrue(cmd.contains("--batch-mode"));
    }
}
