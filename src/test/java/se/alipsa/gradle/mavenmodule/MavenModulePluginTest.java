package se.alipsa.gradle.mavenmodule;

import org.gradle.api.NamedDomainObjectContainer;
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

        assertNotNull(project.getExtensions().findByName("mavenModules"));
    }

    @Test
    void pluginRegistersAllMavenTasksForModule() {
        Project project = createProjectWithModule("app");

        assertNotNull(project.getTasks().findByName("mavenAppClean"));
        assertNotNull(project.getTasks().findByName("mavenAppCompile"));
        assertNotNull(project.getTasks().findByName("mavenAppTest"));
        assertNotNull(project.getTasks().findByName("mavenAppPackage"));
        assertNotNull(project.getTasks().findByName("mavenAppVerify"));
        assertNotNull(project.getTasks().findByName("mavenAppInstall"));
        assertNotNull(project.getTasks().findByName("mavenAppDeploy"));
    }

    @Test
    void pluginRegistersLifecycleTasks() {
        Project project = createProjectWithModule("app");

        // BasePlugin lifecycle tasks are registered eagerly
        assertNotNull(project.getTasks().findByName("clean"));
        assertNotNull(project.getTasks().findByName("assemble"));
        assertNotNull(project.getTasks().findByName("check"));
        assertNotNull(project.getTasks().findByName("build"));
        // publishToMavenLocal and publish are registered in afterEvaluate
        // to avoid conflicts with maven-publish; verified in functional tests
    }

    @Test
    void lifecycleTasksDependOnModuleTasks() {
        Project project = createProjectWithModule("app");

        assertTrue(project.getTasks().getByName("clean")
                .getDependsOn().stream().anyMatch(d -> d.toString().contains("mavenAppClean")));
        assertTrue(project.getTasks().getByName("assemble")
                .getDependsOn().stream().anyMatch(d -> d.toString().contains("mavenAppPackage")));
        assertTrue(project.getTasks().getByName("check")
                .getDependsOn().stream().anyMatch(d -> d.toString().contains("mavenAppVerify")));
    }

    @Test
    void moduleDefaultsAreCorrect() {
        Project project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build();
        project.getPlugins().apply("se.alipsa.gradle.maven-module");

        MavenModule module = getModules(project).create("app");

        assertFalse(module.getMavenExecutable().isPresent());
        assertEquals(projectDir, module.getWorkingDir().get());
        assertEquals(new File(projectDir, "pom.xml"), module.getPomFile().get().getAsFile());
        assertTrue(module.getProfiles().get().isEmpty());
        assertTrue(module.getSystemProperties().get().isEmpty());
        assertTrue(module.getArgs().get().isEmpty());
        assertTrue(module.getEnvironment().get().isEmpty());
        assertTrue(module.getMustRunAfterModules().isEmpty());
    }

    @Test
    void pomParsingHandlesParentInheritance() throws IOException {
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

        MavenModulePlugin.PomInfo info = MavenModulePlugin.parsePom(project, pomFile);

        assertNotNull(info);
        assertEquals("com.example.parent", info.groupId);
        assertEquals("2.0.0", info.version);
        assertEquals("child-module", info.artifactId);
    }

    @Test
    void commandLineBuildingIncludesProfilesAndProperties() {
        Project project = createProjectWithModule("app");

        MavenExecTask task = (MavenExecTask) project.getTasks().getByName("mavenAppCompile");
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

    @Test
    void customPomFileAddsDashF() throws IOException {
        File bomFile = new File(projectDir, "bom.xml");
        Files.writeString(bomFile.toPath(), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>my-bom</artifactId>
                    <version>3.0.0</version>
                    <packaging>pom</packaging>
                </project>
                """);

        Project project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build();
        project.getPlugins().apply("se.alipsa.gradle.maven-module");

        MavenModule bom = getModules(project).create("bom");
        bom.getPomFile().fileValue(bomFile);

        MavenExecTask task = (MavenExecTask) project.getTasks().getByName("mavenBomInstall");
        List<String> cmd = task.buildCommandLine("mvn");

        assertTrue(cmd.contains("-f"));
        assertTrue(cmd.contains(bomFile.getAbsolutePath()));
    }

    @Test
    void defaultPomFileDoesNotAddDashF() {
        Project project = createProjectWithModule("app");

        MavenExecTask task = (MavenExecTask) project.getTasks().getByName("mavenAppCompile");
        List<String> cmd = task.buildCommandLine("mvn");

        assertFalse(cmd.contains("-f"), "Default pom.xml should not add -f flag");
    }

    @Test
    void multipleModulesRegisterSeparateTasks() {
        Project project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build();
        project.getPlugins().apply("se.alipsa.gradle.maven-module");

        NamedDomainObjectContainer<MavenModule> modules = getModules(project);
        modules.create("bom");
        modules.create("app");

        assertNotNull(project.getTasks().findByName("mavenBomPackage"));
        assertNotNull(project.getTasks().findByName("mavenAppPackage"));
        assertNotNull(project.getTasks().findByName("mavenBomInstall"));
        assertNotNull(project.getTasks().findByName("mavenAppInstall"));
    }

    @Test
    void lifecycleTasksAggregateAllModules() {
        Project project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build();
        project.getPlugins().apply("se.alipsa.gradle.maven-module");

        NamedDomainObjectContainer<MavenModule> modules = getModules(project);
        modules.create("bom");
        modules.create("app");

        // Verify clean depends on both module clean tasks
        var cleanDeps = project.getTasks().getByName("clean").getDependsOn();
        assertTrue(cleanDeps.stream().anyMatch(d -> d.toString().contains("mavenBomClean")));
        assertTrue(cleanDeps.stream().anyMatch(d -> d.toString().contains("mavenAppClean")));

        // Verify assemble depends on both module package tasks
        var assembleDeps = project.getTasks().getByName("assemble").getDependsOn();
        assertTrue(assembleDeps.stream().anyMatch(d -> d.toString().contains("mavenBomPackage")));
        assertTrue(assembleDeps.stream().anyMatch(d -> d.toString().contains("mavenAppPackage")));
    }

    private Project createProjectWithModule(String moduleName) {
        Project project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build();
        project.getPlugins().apply("se.alipsa.gradle.maven-module");
        getModules(project).create(moduleName);
        return project;
    }

    @SuppressWarnings("unchecked")
    private NamedDomainObjectContainer<MavenModule> getModules(Project project) {
        return (NamedDomainObjectContainer<MavenModule>) project.getExtensions().getByName("mavenModules");
    }
}
