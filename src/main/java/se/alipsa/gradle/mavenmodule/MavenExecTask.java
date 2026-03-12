package se.alipsa.gradle.mavenmodule;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A Gradle task that executes a Maven phase by invoking the Maven CLI.
 */
@DisableCachingByDefault(because = "Maven build execution is not cacheable")
public abstract class MavenExecTask extends DefaultTask {

    /** @return the injected {@link ExecOperations} service */
    @Inject
    protected abstract ExecOperations getExecOperations();

    /**
     * The POM file to use. When it differs from the default {@code pom.xml}
     * in the working directory, {@code -f} is passed to Maven.
     * @return the pom file property
     */
    @Internal
    public abstract Property<File> getPomFile();

    /**
     * The Maven phase to execute (e.g. compile, test, package, verify, install, deploy, clean).
     * @return the phase property
     */
    @Input
    public abstract Property<String> getPhase();

    /**
     * The Maven executable to use.
     * @return the maven executable property
     */
    @Input
    @Optional
    public abstract Property<String> getMavenExecutable();

    /**
     * Maven profiles to activate.
     * @return the profiles property
     */
    @Input
    @Optional
    public abstract ListProperty<String> getProfiles();

    /**
     * System properties to pass to Maven.
     * @return the system properties
     */
    @Input
    @Optional
    public abstract MapProperty<String, String> getSystemProperties();

    /**
     * Additional CLI arguments.
     * @return the additional arguments property
     */
    @Input
    @Optional
    public abstract ListProperty<String> getArgs();

    /**
     * Working directory for Maven execution.
     * @return the working directory property
     */
    @Internal
    public abstract Property<File> getWorkingDir();

    /**
     * Environment variables for the Maven process.
     * @return the environment variables
     */
    @Internal
    public abstract MapProperty<String, String> getEnvironment();

    /** Executes the configured Maven phase. */
    @TaskAction
    public void exec() {
        File workDir = getWorkingDir().get();
        String executable = resolveMavenExecutable(workDir);
        List<String> commandLine = buildCommandLine(executable);

        getLogger().info("Executing Maven: {} in {}", commandLine, workDir);

        ExecResult result = getExecOperations().exec(execSpec -> {
            execSpec.setWorkingDir(workDir);
            execSpec.commandLine(commandLine);
            if (getEnvironment().isPresent() && !getEnvironment().get().isEmpty()) {
                execSpec.environment(getEnvironment().get());
            }
            execSpec.setIgnoreExitValue(true);
        });

        if (result.getExitValue() != 0) {
            throw new GradleException("Maven " + getPhase().get() + " failed with exit code " + result.getExitValue());
        }
    }

    private String resolveMavenExecutable(File workDir) {
        if (getMavenExecutable().isPresent()) {
            return getMavenExecutable().get();
        }

        // Check for mvnw in the working directory and parent directories
        File dir = workDir;
        while (dir != null) {
            boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
            String wrapperName = isWindows ? "mvnw.cmd" : "mvnw";
            File wrapper = new File(dir, wrapperName);
            if (wrapper.exists() && wrapper.canExecute()) {
                return wrapper.getAbsolutePath();
            }
            // Also check for mvnw without .cmd on Windows (some setups use the shell script)
            if (isWindows) {
                wrapper = new File(dir, "mvnw");
                if (wrapper.exists()) {
                    return wrapper.getAbsolutePath();
                }
            }
            dir = dir.getParentFile();
        }

        // Fall back to system mvn
        return "mvn";
    }

    List<String> buildCommandLine(String executable) {
        List<String> cmd = new ArrayList<>();
        cmd.add(executable);

        if (getPomFile().isPresent()) {
            File pomFile = getPomFile().get();
            File defaultPom = new File(getWorkingDir().get(), "pom.xml");
            if (!pomFile.getAbsoluteFile().equals(defaultPom.getAbsoluteFile())) {
                cmd.add("-f");
                cmd.add(pomFile.getAbsolutePath());
            }
        }

        cmd.add(getPhase().get());

        if (getProfiles().isPresent() && !getProfiles().get().isEmpty()) {
            cmd.add("-P");
            cmd.add(String.join(",", getProfiles().get()));
        }

        if (getSystemProperties().isPresent()) {
            getSystemProperties().get().forEach((key, value) -> {
                if (value != null && !value.isEmpty()) {
                    cmd.add("-D" + key + "=" + value);
                } else {
                    cmd.add("-D" + key);
                }
            });
        }

        if (getArgs().isPresent()) {
            cmd.addAll(getArgs().get());
        }

        return cmd;
    }
}
