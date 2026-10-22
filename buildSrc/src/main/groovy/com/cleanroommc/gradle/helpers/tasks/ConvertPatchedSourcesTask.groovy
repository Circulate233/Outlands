package com.cleanroommc.gradle.helpers.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Rewrites the patched MCP-named source tree into the SRG namespace, which is what a distributable patch set has to
 * use: MCP names may not be redistributed, and SRG names are what Forge's own 1.12 userdev ships.
 *
 * The two trees are the same code seen through two decompiler runs, so the identifier correspondence is recovered by
 * aligning the MCP base tree with the SRG base tree line by line: identical lines give token position to token
 * position pairs. Using positions instead of a name lookup also covers parameter names (MCP `worldIn` vs SRG
 * `p_180663_1_`), which a class scoped name table cannot express.
 *
 * Files the patch set does not touch are copied straight out of the SRG base, so only the patched files need the
 * rewrite and no file has to be guessed.
 */
abstract class ConvertPatchedSourcesTask extends DefaultTask {

    /** Patched tree, MCP names. Patches are applied to a directory, so this is one too. */
    @InputDirectory
    abstract DirectoryProperty getPatched()

    /** Unpatched MCP sources jar, used to learn the identifier correspondence. */
    @InputFile
    abstract RegularFileProperty getMcpBase()

    /** Unpatched SRG sources jar, both the correspondence target and the source of untouched files. */
    @InputFile
    abstract RegularFileProperty getSrgBase()

    @OutputDirectory
    abstract DirectoryProperty getOutput()

    static final java.util.regex.Pattern TOKEN =
        java.util.regex.Pattern.compile('([A-Za-z_$][A-Za-z0-9_$]*)|("[^"\\\\]*(?:\\\\.[^"\\\\]*)*")|(\'[^\'\\\\]*(?:\\\\.[^\'\\\\]*)*\')|(.)')

    @TaskAction
    void convert() {
        File patched = getPatched().get().asFile
        File out = getOutput().get().asFile
        out.deleteDir()
        out.mkdirs()

        int copied = 0
        int rewritten = 0
        def failed = []

        new java.util.zip.ZipFile(getSrgBase().get().asFile).withCloseable { srg ->
            new java.util.zip.ZipFile(getMcpBase().get().asFile).withCloseable { mcp ->
                srg.entries().each { entry ->
                    if (entry.directory || !entry.name.endsWith('.java')) return
                    def relative = entry.name
                    File outFile = new File(out, relative)
                    outFile.parentFile.mkdirs()

                    def patchedFile = new File(patched, relative)
                    def mcpEntry = mcp.getEntry(relative)
                    if (!patchedFile.isFile() || mcpEntry == null) {
                        outFile.bytes = srg.getInputStream(entry).readAllBytes()
                        copied++
                        return
                    }
                    def mcpBytes = mcp.getInputStream(mcpEntry).readAllBytes()
                    def srgText = new String(srg.getInputStream(entry).readAllBytes(), 'UTF-8')
                    def mcpText = new String(mcpBytes, 'UTF-8')
                    def patchedText = patchedFile.getText('UTF-8')

                    if (patchedText == mcpText) {
                        // untouched by the patch set, the srg version is exactly right
                        outFile.setText(srgText, 'UTF-8')
                        copied++
                        return
                    }

                    def mapping = correspondence(mcpText, srgText)
                    if (mapping == null) {
                        failed << relative
                        outFile.setText(srgText, 'UTF-8')
                        return
                    }
                    outFile.setText(rewrite(patchedText, mapping, tokens(mcpText) as Set, relative, failed), 'UTF-8')
                    rewritten++
                }
            }
        }

        logger.lifecycle("convertPatchedSources: ${rewritten} rewritten, ${copied} copied straight from the srg base")
        if (!failed.isEmpty()) {
            throw new IllegalStateException("could not map ${failed.size()} file(s) back to srg, they would ship mcp names:\n  "
                + failed.take(20).join('\n  ') + (failed.size() > 20 ? "\n  ..." : ""))
        }
    }

    /**
     * Line aligned token correspondence mcp -> srg. Returns null when the trees do not line up, which means the file
     * cannot be converted safely.
     */
    static Map<String, String> correspondence(String mcpText, String srgText) {
        def mcpLines = mcpText.readLines()
        def srgLines = srgText.readLines()
        if (mcpLines.size() != srgLines.size()) return null

        def map = new HashMap<String, String>()
        def conflicts = new HashSet<String>()
        for (int i = 0; i < mcpLines.size(); i++) {
            def a = tokens(mcpLines[i])
            def b = tokens(srgLines[i])
            if (a.size() != b.size()) continue
            for (int t = 0; t < a.size(); t++) {
                // identity entries too: everything that lines up is known to be correct, so only genuine conflicts
                // (one mcp name, several srg names) end up reported
                def previous = map.put(a[t], b[t])
                if (previous != null && previous != b[t]) conflicts << a[t]
            }
        }
        // a token that maps to two different srg names is ambiguous; leaving it out means it stays as it was, which the
        // caller then reports instead of silently producing a wrong patch
        conflicts.each { map.remove(it) }
        return map
    }

    static final Set<String> JAVA_KEYWORDS = [
        'abstract', 'assert', 'boolean', 'break', 'byte', 'case', 'catch', 'char', 'class', 'const', 'continue',
        'default', 'do', 'double', 'else', 'enum', 'extends', 'final', 'finally', 'float', 'for', 'goto', 'if',
        'implements', 'import', 'instanceof', 'int', 'interface', 'long', 'native', 'new', 'package', 'private',
        'protected', 'public', 'return', 'short', 'static', 'strictfp', 'super', 'switch', 'synchronized', 'this',
        'throw', 'throws', 'transient', 'try', 'void', 'volatile', 'while', 'true', 'false', 'null'
    ] as Set

    static String rewrite(String source, Map<String, String> mapping, Set<String> baseTokens, String relative, List<String> failed) {
        def unresolved = new LinkedHashSet<String>()
        def matcher = TOKEN.matcher(source)
        def out = new StringBuilder(source.length())
        while (matcher.find()) {
            def token = matcher.group(1)
            if (token == null) {
                out.append(matcher.group())
                continue
            }
            def mapped = mapping.get(token)
            if (mapped != null) {
                out.append(mapped)
            } else {
                // Only a token that already existed in the unpatched tree and still has no mapping is suspicious: it
                // means the correspondence was ambiguous. Identifiers introduced by the patch itself cannot be derived
                // from the base trees, and keywords/imports never map.
                if (baseTokens.contains(token) && !JAVA_KEYWORDS.contains(token)
                        && !(token ==~ /(func|field)_\d+_[a-zA-Z]+|p_\d+_\d+_/)) unresolved << token
                out.append(token)
            }
        }
        if (!unresolved.isEmpty()) {
            failed << (relative + ' -> ' + unresolved.take(8).join(', '))
        }
        return out.toString()
    }

    static List<String> tokens(String line) {
        def result = []
        def matcher = TOKEN.matcher(line)
        while (matcher.find()) {
            if (matcher.group(1) != null) result << matcher.group(1)
        }
        return result
    }
}
