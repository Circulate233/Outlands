package net.minecraftforge.fml.common;

import net.minecraft.crash.CrashReport;
import net.minecraft.crash.ICrashReportDetail;

/**
 * A crash report extension that is handed the throwable the report is being built for.
 *
 * <p>{@link ICrashCallable} cannot do this: it is registered up front and evaluated from inside the
 * {@link CrashReport} constructor, where the cause is still a constructor argument, so a callable has no way to
 * reach it. A contributor is called with the throwable instead, which is what "explain this crash" needs.
 *
 * <p>Two placements are offered, because they answer different questions:
 * <ul>
 *   <li>{@link #getStackTraceReport(Throwable)} - free text appended right after the stack trace. Use it for a
 *       verdict, a hint, a link.</li>
 *   <li>{@link #addSections(Throwable, SectionSink)} - one or more {@code -- name --} sections of key/value
 *       entries. Use it for state a maintainer needs (which mixin config, which mod version, what was disabled).</li>
 * </ul>
 *
 * <p>Contract:
 * <ul>
 *   <li>Called while a crash report is being written: <b>never throw</b>. Exceptions are caught and logged, but the
 *       report loses your output.</li>
 *   <li>Called exactly once per report, on the crashing thread. Do not call
 *       {@link CrashReport#getCompleteReport()} or {@link CrashReport#saveToFile} from here.</li>
 *   <li>Contributors run in registration order.</li>
 *   <li>Keep it cheap and dependency free - the game is already broken at this point.</li>
 * </ul>
 *
 * @see CrashReportHooks#register(String, ICrashReportContributor)
 */
public interface ICrashReportContributor {

    /**
     * @param cause the throwable the report is being built for (may have been substituted for an
     *     {@link NullPointerException}/{@link StackOverflowError} wrapper by the report itself)
     * @return text appended verbatim after the stack trace, or {@code null}/{@code ""} for nothing
     */
    default String getStackTraceReport(Throwable cause) {
        return null;
    }

    /**
     * Adds report sections. Called while the report object is created, before anything is rendered.
     *
     * @param cause the throwable the report is being built for
     * @param sink where entries go; see {@link SectionSink#add(String, String, Object)}
     */
    default void addSections(Throwable cause, SectionSink sink) {
    }

    /**
     * Destination for {@link #addSections}. Section names and keys are rendered as-is; a value may be anything whose
     * {@code toString()} is useful, or an {@link ICrashReportDetail} to have it evaluated later.
     */
    interface SectionSink {

        /**
         * @param section the {@code -- <section> --} heading to put the entry under
         * @param key the label in front of the value
         * @param value the value, or an {@link ICrashReportDetail} to defer it
         */
        void add(String section, String key, Object value);
    }
}
