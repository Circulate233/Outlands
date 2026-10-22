package com.cleanroommc.gradle.helpers.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Turns {@code templates/CleanroomVersion.java} into the {@code CleanroomVersion} class that the loader reports at
 * runtime.
 *
 * The version used to be read from the palantir git-version plugin's extra properties, which made the task depend on a
 * plugin being applied to the root project and on an undocumented extra-property closure. Everything needed is already
 * in {@code rootProject.version} (the root build computes it as {@code <lastTag>+build.<commitDistance>}), so the task
 * derives both placeholders from that instead:
 *
 * <pre>
 *   %VERSION%        -> the tag part         0.6.13-alpha
 *   %BUILD_VERSION%  -> the full version     0.6.13-alpha+build.20
 * </pre>
 *
 * With {@code -Prelease} the root version is the bare tag, in which case both placeholders carry the same value.
 */
abstract class GenVersionTask extends DefaultTask {

    /** Full project version, e.g. {@code 0.6.13-alpha+build.20}. */
    @Input
    String version = project.rootProject.version.toString()

    @InputFile
    File getTemplate() {
        return project.rootProject.file('templates/CleanroomVersion.java')
    }

    /**
     * Generated into the build directory on purpose: the class used to be written back into {@code src/main/java} and
     * gitignored, so a stale copy silently shipped an old version string whenever nobody re-ran the task.
     */
    @OutputFile
    File getVersionClass() {
        return new File(project.layout.buildDirectory.get().asFile, 'generated/sources/cleanroomVersion/com/cleanroommc/common/CleanroomVersion.java')
    }

    @TaskAction
    void action() {
        String tag = version.contains('+') ? version.substring(0, version.indexOf('+')) : version
        File output = getVersionClass()
        output.parentFile.mkdirs()
        output.withWriter { writer ->
            getTemplate().eachLine { line ->
                writer.write(line.replace('%VERSION%', tag).replace('%BUILD_VERSION%', version) + '\n')
            }
        }
    }
}
