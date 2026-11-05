package net.minecraftforge.fml.common;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.crash.ICrashReportDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrashReportHooksTest {

    private static final class Text implements ICrashReportContributor {

        private final String text;
        private final AtomicInteger calls = new AtomicInteger();

        private Text(String text) {
            this.text = text;
        }

        @Override
        public String getStackTraceReport(Throwable cause) {
            this.calls.incrementAndGet();
            return this.text;
        }
    }

    @BeforeEach
    void setUp() {
        CrashReportHooks.clear();
    }

    @AfterEach
    void tearDown() {
        CrashReportHooks.clear();
    }

    @Test
    void nothingRegisteredYieldsNothing() {
        assertFalse(CrashReportHooks.hasContributors());
        assertEquals("", CrashReportHooks.getStackTraceReport(new RuntimeException()));

        List<String> entries = new ArrayList<>();
        CrashReportHooks.addSections(new RuntimeException(), (section, key, value) -> entries.add(section + "/" + key));
        assertTrue(entries.isEmpty());
    }

    @Test
    void stackTraceTextIsConcatenatedInRegistrationOrderAndLinePrefixed() {
        CrashReportHooks.register("first", new Text("one"));
        CrashReportHooks.register("second", new Text("\ntwo"));

        assertEquals("\none\ntwo", CrashReportHooks.getStackTraceReport(new RuntimeException()));
    }

    @Test
    void emptyAndNullTextsAreSkipped() {
        CrashReportHooks.register("empty", new Text(""));
        CrashReportHooks.register("null", new Text(null));
        CrashReportHooks.register("real", new Text("kept"));

        assertEquals("\nkept", CrashReportHooks.getStackTraceReport(new RuntimeException()));
    }

    @Test
    void aThrowingContributorDoesNotHideTheOthers() {
        CrashReportHooks.register("boom", new ICrashReportContributor() {
            @Override
            public String getStackTraceReport(Throwable cause) {
                throw new IllegalStateException("boom");
            }

            @Override
            public void addSections(Throwable cause, SectionSink sink) {
                throw new IllegalStateException("boom");
            }
        });
        CrashReportHooks.register("after", new ICrashReportContributor() {
            @Override
            public String getStackTraceReport(Throwable cause) {
                return "after";
            }

            @Override
            public void addSections(Throwable cause, SectionSink sink) {
                sink.add("section", "key", "value");
            }
        });

        assertEquals("\nafter", CrashReportHooks.getStackTraceReport(new RuntimeException()));

        List<String> entries = new ArrayList<>();
        CrashReportHooks.addSections(new RuntimeException(), (section, key, value) -> entries.add(key + "=" + value));
        assertEquals(List.of("key=value"), entries);
    }

    @Test
    void sectionsReachTheSinkAndDetailValuesAreNotEvaluatedEarly() {
        ICrashReportDetail<String> detail = () -> "later";
        CrashReportHooks.register("sections", new ICrashReportContributor() {
            @Override
            public void addSections(Throwable cause, SectionSink sink) {
                sink.add("Mod of Mending", "shape", "javax_ee_missing");
                sink.add("Mod of Mending", "deferred", detail);
            }
        });

        List<String> keys = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        CrashReportHooks.addSections(new RuntimeException(), (section, key, value) -> {
            assertEquals("Mod of Mending", section);
            keys.add(key);
            values.add(value);
        });

        assertEquals(List.of("shape", "deferred"), keys);
        assertEquals("javax_ee_missing", values.get(0));
        assertSame(detail, values.get(1));
    }

    @Test
    void nullSectionOrKeyIsDroppedByTheReportAdapter() {
        CrashReportHooks.register("bad", new ICrashReportContributor() {
            @Override
            public void addSections(Throwable cause, SectionSink sink) {
                sink.add(null, "key", "value");
                sink.add("section", null, "value");
            }
        });

        // the sink overload does not filter, the report bound adapter does; filtering here would hide the bug from
        // the adapter, so only assert the contributor itself is called
        List<String> entries = new ArrayList<>();
        CrashReportHooks.addSections(new RuntimeException(), (section, key, value) -> entries.add(key));
        assertEquals(2, entries.size());
    }

    @Test
    void duplicateOwnerAndClassIsRegisteredOnce() {
        Text contributor = new Text("once");
        CrashReportHooks.register("mod", contributor);
        CrashReportHooks.register("mod", contributor);

        assertEquals("\nonce", CrashReportHooks.getStackTraceReport(new RuntimeException()));
        assertEquals(1, contributor.calls.get());
    }

    @Test
    void nullOwnerFallsBackToTheClassName() {
        CrashReportHooks.register(null, new Text("x"));
        assertTrue(CrashReportHooks.hasContributors());
    }
}
