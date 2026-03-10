package se.alipsa.gradle.mavenmodule;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.io.File;

/**
 * DSL extension for configuring the Maven module plugin.
 *
 * <pre>
 * mavenModule {
 *     mavenExecutable = 'mvn'
 *     profiles = ['ci']
 *     systemProperties = ['skipTests': 'true']
 *     args = ['-X']
 *     workingDir = project.projectDir
 *     environment = ['JAVA_HOME': '/usr/lib/jvm/java-17']
 * }
 * </pre>
 */
public abstract class MavenModuleExtension {

    /**
     * The Maven executable to use. If not set, the plugin will auto-detect
     * mvnw/mvnw.cmd in the project directory, or fall back to system mvn.
     */
    public abstract Property<String> getMavenExecutable();

    /**
     * Maven profiles to activate (-P).
     */
    public abstract ListProperty<String> getProfiles();

    /**
     * System properties to pass to Maven (-D).
     */
    public abstract MapProperty<String, String> getSystemProperties();

    /**
     * Additional CLI arguments to pass to Maven.
     */
    public abstract ListProperty<String> getArgs();

    /**
     * Working directory for the Maven execution. Defaults to the project directory.
     */
    public abstract Property<File> getWorkingDir();

    /**
     * Environment variables to set for the Maven process.
     */
    public abstract MapProperty<String, String> getEnvironment();
}
