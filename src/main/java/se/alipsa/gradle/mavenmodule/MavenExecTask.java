package se.alipsa.gradle.mavenmodule;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getPomFile();

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

        // Try to find mvn on the system PATH
        String found = findOnPath("mvn");
        if (found != null) {
            return found;
        }

        // Try resolving via login shell (handles sdkman, nvm-style PATH setup
        // where PATH is configured in shell init scripts not inherited by the daemon)
        found = resolveViaShell("mvn");
        if (found != null) {
            return found;
        }

        // Fall back to bare mvn
        return "mvn";
    }

    private String findOnPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null) return null;
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        for (String dir : path.split(File.pathSeparator)) {
            File file = new File(dir, executable);
            if (file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
            if (isWindows) {
                for (String ext : new String[]{".cmd", ".bat", ".exe"}) {
                    file = new File(dir, executable + ext);
                    if (file.isFile()) {
                        return file.getAbsolutePath();
                    }
                }
            }
        }
        return null;
    }

    private String resolveViaShell(String executable) {
        try {
            boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
            ProcessBuilder pb;
            if (isWindows) {
                pb = new ProcessBuilder("cmd", "/c", "where", executable);
            } else {
                String shell = System.getenv("SHELL");
                if (shell == null || shell.isEmpty()) {
                    shell = "/bin/sh";
                }
                // Use -l (login) and -i (interactive) to source all init files
                // (e.g. .bashrc, .zshrc) where tools like sdkman set up PATH
                pb = new ProcessBuilder(shell, "-lic", "command -v " + executable);
            }
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            boolean exited = process.waitFor(5, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() == 0 && !output.isEmpty()) {
                // Filter for lines that are actual file paths (skip shell noise)
                return output.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && new File(line).isFile())
                    .findFirst()
                    .orElse(null);
            }
        } catch (Exception e) {
            getLogger().debug("Failed to resolve {} via shell: {}", executable, e.getMessage());
        }
        return null;
    }

    List<String> buildCommandLine(String executable) {
        List<String> cmd = new ArrayList<>();
        cmd.add(executable);

        if (getPomFile().isPresent()) {
            File pomFile = getPomFile().get().getAsFile();
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
