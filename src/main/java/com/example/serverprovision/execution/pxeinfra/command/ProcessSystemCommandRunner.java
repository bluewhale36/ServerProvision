package com.example.serverprovision.execution.pxeinfra.command;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * {@link SystemCommandRunner} 의 실 구현 — {@link ProcessBuilder} argv 로 셸을 미경유해 명령을 실행하고,
 * 실패(타임아웃·부재·비0 종료)를 예외가 아닌 {@link CommandResult} 상태로 흡수한다(관측 경로는 예외로 끊기지 않는다).
 *
 * <p><b>데드락 회피</b>: {@code waitFor()} 후에 stdout 을 읽는 순진한 패턴({@code IsoPreparationService} 참조)은
 * 자식 프로세스가 파이프 버퍼(보통 64KB)를 채우고 블로킹하는 사이 부모도 {@code waitFor} 에서 블로킹해 서로를
 * 못 풀어 주는 교착에 빠질 수 있다. 여기서는 stdout·stderr 를 각각 전용 배수(drain) 스레드가 자식과 동시에
 * 비우므로 파이프가 막히지 않는다({@code redirectErrorStream(false)} 로 두 스트림을 분리 수집).</p>
 */
@Slf4j
@Component
public class ProcessSystemCommandRunner implements SystemCommandRunner {

    @Override
    public CommandResult run(AllowedCommand command, List<String> callerArgs) {
        List<String> argv = command.assembleArgv(callerArgs);
        long timeoutMillis = command.timeout().toMillis();
        Process process;
        try {
            process = new ProcessBuilder(argv)
                    .redirectErrorStream(false)
                    .start();
        } catch (IOException e) {
            // 바이너리 부재(ENOENT) 등 spawn 자체 실패 — 부재 상태로 흡수(예외 전파 금지).
            log.debug("[pxe-infra] 명령 spawn 실패 — 부재로 흡수. command={}, 원인={}", command, e.getMessage());
            return CommandResult.notFound();
        }

        StreamDrainer stdout = StreamDrainer.start(process.getInputStream());
        StreamDrainer stderr = StreamDrainer.start(process.getErrorStream());
        try {
            boolean finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("[pxe-infra] 명령 타임아웃({}ms) — 강제 종료. command={}", timeoutMillis, command);
                return CommandResult.timedOut();
            }
            // 정상 종료 — 자식이 스트림을 닫아 배수 스레드도 곧 EOF 로 끝난다. 짧게 합류 후 수집.
            stdout.join();
            stderr.join();
            return CommandResult.completed(process.exitValue(), stdout.content(), stderr.content());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            log.warn("[pxe-infra] 명령 대기 중 인터럽트 — 강제 종료. command={}", command);
            return CommandResult.timedOut();
        }
    }

    /**
     * 자식 프로세스의 한 출력 스트림을 전용 데몬 스레드로 즉시 비우는 배수기. 파이프 버퍼가 차서 자식이
     * 블로킹하는 것을 막아 {@code waitFor} 교착을 방지한다. 읽기 중 IO 오류는 부분 수집으로 흡수한다.
     */
    private static final class StreamDrainer {

        private final Thread thread;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        private StreamDrainer(InputStream in) {
            this.thread = new Thread(() -> drain(in));
            this.thread.setDaemon(true);
        }

        static StreamDrainer start(InputStream in) {
            StreamDrainer drainer = new StreamDrainer(in);
            drainer.thread.start();
            return drainer;
        }

        private void drain(InputStream in) {
            byte[] chunk = new byte[4096];
            try (in) {
                int read;
                while ((read = in.read(chunk)) != -1) {
                    synchronized (buffer) {
                        buffer.write(chunk, 0, read);
                    }
                }
            } catch (IOException e) {
                // 강제 종료로 스트림이 닫히면 여기 도달 — 그때까지 수집분만 보존한다.
            }
        }

        void join() throws InterruptedException {
            thread.join(TimeUnit.SECONDS.toMillis(1));
        }

        String content() {
            synchronized (buffer) {
                return buffer.toString(StandardCharsets.UTF_8);
            }
        }
    }
}
