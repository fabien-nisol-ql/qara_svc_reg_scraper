package com.qaralink.regscraper.svc.workload.k8s;

import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;

import java.util.List;

class PodExitSummary {
    private final boolean allZero;
    private final Integer exitCode;
    private final String details;

    private PodExitSummary(boolean allZero, Integer exitCode, String details) {
        this.allZero = allZero;
        this.exitCode = exitCode;
        this.details = details;
    }

    String format() {
        return "exit=" + (exitCode == null ? "unknown" : exitCode)
                + (details == null || details.isBlank() ? "" : "\n" + details);
    }

    static PodExitSummary from(V1Pod pod) {
        V1PodStatus st = pod.getStatus();
        if (st == null) return new PodExitSummary(true, null, "no status");

        StringBuilder sb = new StringBuilder();
        var init = appendContainerStatuses(sb, "init", st.getInitContainerStatuses());
        var main = appendContainerStatuses(sb, "main", st.getContainerStatuses());
        boolean ok = init.allZero && main.allZero;
        Integer exit = pickExitCode(init.exitCode, main.exitCode);

        if (sb.isEmpty()) sb.append("no container statuses");
        return new PodExitSummary(ok, exit, sb.toString());
    }

    private static Integer pickExitCode(Integer a, Integer b) {
        Integer nonZero = (a != null && a != 0) ? a : (b != null && b != 0) ? b : null;
        if (nonZero != null) return nonZero;
        if ((a != null && a == 0) || (b != null && b == 0)) return 0;
        return null;
    }

    private static ExitAgg appendContainerStatuses(StringBuilder sb, String kind, List<V1ContainerStatus> list) {
        if (list == null || list.isEmpty()) return new ExitAgg(true, null);

        boolean ok = true;
        Integer aggExit = null;

        for (var cs : list) {
            if (cs == null) continue;
            var term = cs.getState() != null ? cs.getState().getTerminated() : null;
            if (term == null) continue;

            Integer exitCode = term.getExitCode();
            String name = cs.getName();
            String reason = term.getReason();
            String message = term.getMessage();

            if (exitCode == null) {
                ok = false;
            } else if (exitCode != 0) {
                ok = false;
                if (aggExit == null || aggExit == 0) aggExit = exitCode;
            } else {
                if (aggExit == null) aggExit = 0;
            }

            if (!sb.isEmpty()) sb.append('\n');
            sb.append(kind).append(" container ").append(name)
                    .append(": exit=").append(exitCode)
                    .append(reason != null ? ", reason=" + reason : "")
                    .append(message != null && !message.isBlank() ? ", msg=" + trim(message, 200) : "");
        }

        return new ExitAgg(ok, aggExit);
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private record ExitAgg(boolean allZero, Integer exitCode) {
    }
}
