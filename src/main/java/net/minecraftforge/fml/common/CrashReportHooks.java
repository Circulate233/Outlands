package net.minecraftforge.fml.common;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.crash.ICrashReportDetail;

/**
 * Registry for {@link ICrashReportContributor}s, the crash report extension point that actually sees the throwable.
 *
 * <p>Hooked into the patched {@link CrashReport}: sections are collected from the constructor, the stack-trace text
 * when the stack trace is rendered - both with the same throwable, in registration order.
 *
 * <p>Every call is isolated: a misbehaving contributor is logged and skipped, it never prevents the report from being
 * written.
 *
 * <pre>{@code
 * CrashReportHooks.register("examplemod", new ICrashReportContributor() {
 *     @Override
 *     public String getStackTraceReport(Throwable cause) {
 *         return cause instanceof NoClassDefFoundError ? "\nexamplemod: this class moved, see ..." : null;
 *     }
 * });
 * }</pre>
 */
public final class CrashReportHooks {

    private static final List<Entry> CONTRIBUTORS = new CopyOnWriteArrayList<Entry>();

    private CrashReportHooks() {
    }

    /**
     * Registers a contributor.
     *
     * @param owner free-form origin ("mymod", a class name); only used for logging and duplicate detection
     * @param contributor the contributor; registering the same owner+class twice replaces nothing and logs at debug
     *     level, which makes double registration from mod construction harmless
     */
    public static void register(String owner, ICrashReportContributor contributor) {
        if (contributor == null) {
            return;
        }
        String effectiveOwner = owner == null || owner.isEmpty() ? contributor.getClass().getName() : owner;
        for (Entry entry : CONTRIBUTORS) {
            if (entry.owner.equals(effectiveOwner) && entry.contributor.getClass() == contributor.getClass()) {
                FMLLog.log.debug("Crash report contributor '{}' is already registered, ignoring the duplicate",
                        effectiveOwner);
                return;
            }
        }
        CONTRIBUTORS.add(new Entry(effectiveOwner, contributor));
        FMLLog.log.debug("Registered crash report contributor '{}'", effectiveOwner);
    }

    /** @return whether anything is registered, so callers can skip the work of preparing to say nothing */
    public static boolean hasContributors() {
        return !CONTRIBUTORS.isEmpty();
    }

    /* ------------------------------------------------------------------ invoked by the CrashReport patch */

    /**
     * Collects the {@code -- name --} sections. Called from the {@link CrashReport} constructor.
     */
    public static void addSections(CrashReport report, Throwable cause) {
        if (CONTRIBUTORS.isEmpty()) {
            return;
        }
        Map<String, CrashReportCategory> categories = new HashMap<String, CrashReportCategory>();
        addSections(cause, new ICrashReportContributor.SectionSink() {
            @Override
            public void add(String section, String key, Object value) {
                if (section == null || key == null) {
                    return;
                }
                // one heading per name, so several contributors can share a section
                CrashReportCategory category = categories.get(section);
                if (category == null) {
                    category = report.makeCategory(section);
                    categories.put(section, category);
                }
                if (value instanceof ICrashReportDetail) {
                    @SuppressWarnings("unchecked")
                    ICrashReportDetail<String> detail = (ICrashReportDetail<String>) value;
                    category.addDetail(key, detail);
                } else {
                    category.addCrashSection(key, value);
                }
            }
        });
    }

    /**
     * @return everything contributors want appended after the stack trace, already concatenated, never {@code null}
     */
    public static String getStackTraceReport(Throwable cause) {
        if (CONTRIBUTORS.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Entry entry : CONTRIBUTORS) {
            String text;
            try {
                text = entry.contributor.getStackTraceReport(cause);
            } catch (Throwable t) {
                FMLLog.log.warn("Crash report contributor '{}' failed while describing the stack trace", entry.owner,
                        t);
                continue;
            }
            if (text == null || text.isEmpty()) {
                continue;
            }
            if (text.charAt(0) != '\n') {
                out.append('\n');
            }
            out.append(text);
        }
        return out.toString();
    }

    /* ------------------------------------------------------------------ sink only, unit testable */

    /**
     * Same as {@link #addSections(CrashReport, Throwable)} without a report attached; the report bound overload is a
     * thin adapter over this one.
     */
    static void addSections(Throwable cause, ICrashReportContributor.SectionSink sink) {
        for (Entry entry : CONTRIBUTORS) {
            try {
                entry.contributor.addSections(cause, sink);
            } catch (Throwable t) {
                FMLLog.log.warn("Crash report contributor '{}' failed while adding a report section", entry.owner, t);
            }
        }
    }

    /** Removes everything again; only meant for tests. */
    static void clear() {
        CONTRIBUTORS.clear();
    }

    private record Entry(String owner, ICrashReportContributor contributor) { }
}
