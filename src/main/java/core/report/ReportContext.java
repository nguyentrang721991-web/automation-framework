package core.report;

import java.util.ArrayList;
import java.util.List;

public final class ReportContext {

    private static final ThreadLocal<List<StepLog>> STEP_LOGS = ThreadLocal.withInitial(ArrayList::new);

    private ReportContext() {
    }

    public static void clear() {
        STEP_LOGS.get().clear();
    }

    public static void logStep(String status, String title, String detail) {
        STEP_LOGS.get().add(new StepLog(nullToEmpty(status), nullToEmpty(title), nullToEmpty(detail)));
    }

    public static List<StepLog> stepLogs() {
        return new ArrayList<>(STEP_LOGS.get());
    }

    public static void remove() {
        STEP_LOGS.remove();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record StepLog(String status, String title, String detail) {
    }
}
