package de.jwausle.kubernetes;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SystemStresser {
    private static final String STRESS_COMMAND = "stress";
    private static final int INITIAL_STRESS_COUNT = 1;

    private Optional<ProcessHandle> process = Optional.empty();
    private int stressCount = INITIAL_STRESS_COUNT;

    public synchronized void stress(Optional<Duration> stressPeriod) {
        int nextStressCount = stressCount;
        try {
            unstress();
            String cmd = cmd(stressPeriod, nextStressCount++);
            process = Optional.of(Runtime.getRuntime().exec(cmd).toHandle());
            stressCount = nextStressCount;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public void unstress() {
        process.ifPresent(process -> {
            String cmd = toString();
            stressCount = INITIAL_STRESS_COUNT;
            process.children().forEach(SystemStresser::safeKill);
            safeKill(process);
            System.out.println(">> kill stress pid '" + process.pid() + "' cmd '" + cmd + "'");
        });
        allStressProcessHandle().forEach(SystemStresser::safeKill);
    }

    @Override
    public String toString() {
        long stressProcessCount = allStressProcessHandle().count();
        return stressProcessCount == 0 ?
                "no 'stess' command started" :
                String.format("%s 'stress' commands started.", stressProcessCount);
    }

    private static String cmd(Optional<Duration> stressPeriod, int stressFactor) {
        return Stream.of(STRESS_COMMAND,
                "--cpu", Integer.toString(stressFactor * 10),
                "--io", Integer.toString(stressFactor * 10),
                "--vm", Integer.toString(stressFactor * 10),
                "--hdd", Integer.toString(stressFactor * 10),
                // optional '--timeout SECONDS'
                stressPeriod.map(__ -> "--timeout").orElse(""),
                stressPeriod.map(duration -> duration.getSeconds())
                        .map(seconds -> Long.toString(seconds))
                        .orElse(""))
                .collect(Collectors.joining(" "));
    }

    @NotNull
    private static Stream<ProcessHandle> allStressProcessHandle() {
        return ProcessHandle.allProcesses()
                .filter(process -> process.info().command().map(cmd -> cmd.endsWith("stress")).orElse(false));
    }

    private static void safeKill(ProcessHandle processHandle) {
        try {
            processHandle.destroyForcibly();
        } catch (Exception e) {
            System.out.println(">> error during killing of process " + processHandle.pid());
        }
    }
}
