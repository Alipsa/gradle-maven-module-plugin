package se.alipsa.gradle.mavenmodule;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for a single Maven module within the {@code mavenModules} container.
 *
 * <pre>
 * mavenModules {
 *     app {
 *         pomFile = file('bom.xml')
 *         profiles = ['ci']
 *         systemProperties = ['skipTests': 'true']
 *         args = ['-X']
 *         workingDir = project.projectDir
 *         environment = ['JAVA_HOME': '/usr/lib/jvm/java-17']
 *         mustRunAfter 'bom'
 *     }
 * }
 * </pre>
 */
public class MavenModule {

    private final String name;
    private final RegularFileProperty pomFile;
    private final Property<String> mavenExecutable;
    private final ListProperty<String> profiles;
    private final MapProperty<String, String> systemProperties;
    private final ListProperty<String> args;
    private final Property<File> workingDir;
    private final MapProperty<String, String> environment;
    private final List<String> mustRunAfterModules = new ArrayList<>();

    /**
     * Creates a new Maven module configuration.
     * @param name the module name used to derive task names
     * @param objects the Gradle object factory
     */
    @Inject
    public MavenModule(String name, ObjectFactory objects) {
        this.name = name;
        this.pomFile = objects.fileProperty();
        this.mavenExecutable = objects.property(String.class);
        this.profiles = objects.listProperty(String.class);
        this.systemProperties = objects.mapProperty(String.class, String.class);
        this.args = objects.listProperty(String.class);
        this.workingDir = objects.property(File.class);
        this.environment = objects.mapProperty(String.class, String.class);
    }

    /**
     * @return the module name
     */
    public String getName() {
        return name;
    }

    /**
     * The POM file to use. Defaults to {@code pom.xml} in the project directory.
     * Set this to use an alternative POM file (e.g. {@code bom.xml}).
     * @return the pom file property
     */
    public RegularFileProperty getPomFile() {
        return pomFile;
    }

    /**
     * The Maven executable to use. If not set, auto-detects mvnw or falls back to system mvn.
     * @return the maven executable property
     */
    public Property<String> getMavenExecutable() {
        return mavenExecutable;
    }

    /**
     * Maven profiles to activate (-P).
     * @return the profiles property
     */
    public ListProperty<String> getProfiles() {
        return profiles;
    }

    /**
     * System properties to pass to Maven (-D).
     * @return the system properties
     */
    public MapProperty<String, String> getSystemProperties() {
        return systemProperties;
    }

    /**
     * Additional CLI arguments to pass to Maven.
     * @return the additional arguments property
     */
    public ListProperty<String> getArgs() {
        return args;
    }

    /**
     * Working directory for the Maven execution. Defaults to the POM file's parent directory.
     * @return the working directory property
     */
    public Property<File> getWorkingDir() {
        return workingDir;
    }

    /**
     * Environment variables to set for the Maven process.
     * @return the environment variables
     */
    public MapProperty<String, String> getEnvironment() {
        return environment;
    }

    /**
     * Declares that all tasks of this module must run after all tasks of the specified modules.
     * @param modules the names of modules that must run first
     */
    public void mustRunAfter(String... modules) {
        Collections.addAll(mustRunAfterModules, modules);
    }

    /**
     * @return the list of module names this module must run after
     */
    public List<String> getMustRunAfterModules() {
        return Collections.unmodifiableList(mustRunAfterModules);
    }
}
