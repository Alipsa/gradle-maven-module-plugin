package se.alipsa.gradle.mavenmodule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Configuration spec for excluding subprojects from {@code dependsOnAllPublishedSubprojects}.
 *
 * <pre>
 * dependsOnAllPublishedSubprojects {
 *     exclude 'matrix-examples:candles'
 *     exclude group: 'matrix-examples'
 * }
 * </pre>
 */
public class PublishedSubprojectSpec {

    private final List<String> excludes = new ArrayList<>();
    private final List<String> excludeGroups = new ArrayList<>();

    /**
     * Excludes a subproject by name or path.
     * @param subproject the name or path of the subproject to exclude
     */
    public void exclude(String subproject) {
        excludes.add(subproject);
    }

    /**
     * Excludes subprojects using named parameters. Supports:
     * <ul>
     *   <li>{@code group} — excludes all subprojects under a parent project path
     *       (e.g. {@code exclude group: 'matrix-examples'} excludes
     *       {@code :matrix-examples:candles}, {@code :matrix-examples:foo}, etc.)</li>
     * </ul>
     *
     * @param args named parameters
     */
    public void exclude(Map<String, String> args) {
        String group = args.get("group");
        if (group != null) {
            excludeGroups.add(group);
        }
    }

    /** @return the list of individually excluded subproject names/paths */
    public List<String> getExcludes() {
        return Collections.unmodifiableList(excludes);
    }

    /** @return the list of excluded group prefixes */
    public List<String> getExcludeGroups() {
        return Collections.unmodifiableList(excludeGroups);
    }
}
