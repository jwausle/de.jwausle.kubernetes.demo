package de.jwausle.kubernetes;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.exporter.common.TextFormat;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;

/**
 * Http server on {@code -Dport=8080} to provide this endpoints:
 *
 * <li>GET /               - return pod address</li>
 * <li>GET /liveness       - return LIVING|DIEING</li>
 * <li>GET /liveness/die   - switch to liveness/DIEING</li>
 * <li>GET /liveness/alive - switch to liveness/LIVING</li>
 *
 * <li>GET /readiness                    - return READY|UNREADY</li>
 * <li>GET/POST /readiness/unready [INT] - switch to readiness/UNREADY (optional for INT requests)</li>
 * <li>GET /readiness/ready              - switch to readiness/READY</li>
 *
 * <li>GET/POST /memory [0 - 100] - set java memory consumption in [%]</li>
 * <li>GET/POST /stress [0 - ..]  - set seconds to stress the system</li>
 * <li>GET /stress/start [0 - ..] - set seconds to stress the system</li>
 * <li>GET /stress/stop           - try to stop the system stress</li>
 * <li>GET /kill                  - exit java process</li>
 * <li>GET /metrics               - return tracked metrics</li>
 */
public class HttpServer {
    private static final String DEFAULT_PORT = "8080";
    private static final int HTTP_REQUEST_PROCESSING_RANGE_IN_MILLIS = 1000;
    // endpoints
    private final HomeEndpoint home = new HomeEndpoint(metricRegistry);
    private final LivenessProbe liveness = new LivenessProbe(metricRegistry);
    private final ReadinessProbe readiness = new ReadinessProbe(metricRegistry);
    private final MemoryEndpoint memory = new MemoryEndpoint();
    private final StressEndpoint stresser = new StressEndpoint();
    // responses
    private static final int STATUS_OK = 200;
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int STATUS_GONE = 410;
    private static final int STATUS_LOCKED = 423;
    private static final int STATUS_INTERNAL_ERROR = 500;
    private static final String READY = "READY";
    private static final String UNREADY = "UNREADY";
    private static final int AUTO_HEALING_COUNT = 3;
    private static final String ALIVE = "LIVING";
    private static final String DIEING = "DIEING";
    // metrics
    private final MetricEndpoint metrics = new MetricEndpoint(prometheus);
    private static final MetricRegistry metricRegistry = new MetricRegistry();
    private static final DropwizardExports prometheus = new DropwizardExports(metricRegistry);
    private static final String LIVENESS_GAUGH = "liveness_gaugh";
    private static final String READINESS_GAUGH = "readiness_gaugh";
    private static final String REQUEST_TIMER = "request_timer";

    /**
     * {@code httpServer [-Dport=PORT]} with default port=8080.
     *
     * @param args -Dport=INT, default=8080
     */
    public static void main(String[] args) {
        CollectorRegistry.defaultRegistry.register(prometheus);
        HttpServer server = new HttpServer();
        server.startAndWait();
    }

    private static void sleepOneSecond() {
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Start http(s)://localhost:[PORT]/{@code readiness|unready|ready} and {@code liveness|die|alive} and {@code kill}.
     * <p>
     * Set {@code PORT} via {@code -Dport=INT} to overwrite default {@code 8080}
     * </p>.
     */
    void startAndWait() {
        String port = System.getProperty("port", Optional.ofNullable(System.getenv("PORT")).orElse(DEFAULT_PORT));
        Spark.port(Integer.valueOf(port));

        Spark.get("/", home);

        Spark.get("/readiness", readiness);
        Spark.get("/readiness/unready", readiness::unready);
        Spark.post("/readiness/unready", readiness::unready);
        Spark.get("/readiness/ready", readiness::ready);

        Spark.get("/liveness", liveness);
        Spark.get("/liveness/die", liveness::die);
        Spark.get("/liveness/alive", liveness::alive);

        Spark.get("/memory", memory);
        Spark.get("/memory/start", memory::setConsumption);
        Spark.post("/memory/start", memory::setConsumption);
        Spark.get("/memory/stop", memory::stopConsumption);


        Spark.get("/stress", stresser);
        Spark.get("/stress/start", stresser::stress);
        Spark.post("/stress/start", stresser::stress);
        Spark.get("/stress/stop", stresser::unstress);

        Spark.get("/help", (__, ___) -> Formatter.formatResponse(Stream.of(
                "",
                "# Endpoints",
                "GET  /                                      - pod IP (200|500)",
                "",
                "GET  /liveness                              - liveness probe (200|410)",
                "GET  /liveness/die                          - set pod dieing",
                "GET  /liveness/alive                        - set pod alive",
                "",
                "GET  /readiness                             - readiness probe (200|423)",
                "GET  /readiness/unready [REQUEST_COUNT_INT] - set pod unready for REQUEST_COUNT_INT (default: 3)",
                "GET  /readiness/ready                       - set pod ready again",
                "POST /readiness/unready [REQUEST_COUNT_INT] - set pod unready for REQUEST_COUNT_INT (default: 3)",
                "",
                "GET  /stress                                - show stress state",
                "GET  /stress/start [PERIOD_IN_SEC]          - set system under stress for PERIOD_IN_SEC (default: forever)",
                "GET  /stress/stop                           - stop system stress",
                "POST /stress/start [PERIOD_IN_SEC]          - set system under stress for PERIOD_IN_SEC (default: forever)",
                "",
                "GET  /memory                                - show stress state",
                "GET  /memory/start [HEAP_USE_IN_PERCENT]    - set java HEAP_USE_IN_PERCENT (mandatory)",
                "GET  /memory/stop                           - set java HEAP_USE_IN_PERCENT = 5%",
                "POST /memory/start [HEAP_USE_IN_PERCENT]    - set java HEAP_USE_IN_PERCENT (mandatory)",
                "",
                "GET  /help                                  - show this help",
                "",
                "GET  /kill                                  - kill java process"
        ).collect(Collectors.joining("\n"))));

        Spark.get("/metrics", metrics);

        Spark.get("/kill", (__, ___) -> {
            CompletableFuture.runAsync(HttpServer::sleepOneSecond).thenRun(() -> System.exit(-1));
            return Formatter.formatResponse("shutting down ...");
        });
    }

    /**
     * Liveness endpoint {@code /liveness} to proof container availability. Return either {@code 200 ALIVE} or {@code 410 DIEING}.
     */
    private static class LivenessProbe implements Route {
        private boolean isAlive = true;
        private int index = 1;

        public LivenessProbe(MetricRegistry metricRegistry) {
            metricRegistry.gauge(LIVENESS_GAUGH, () -> (Gauge<Integer>) () -> isAlive ? 1 : 0);
        }

        @Override
        public Object handle(Request request, Response response) throws Exception {
            if (isAlive) {
                return Formatter.formatResponse(ALIVE);
            }
            response.status(STATUS_GONE);
            return Formatter.formatResponse(DIEING + " - " + index++, STATUS_GONE);
        }

        public Object swap(Request request, Response response) {
            isAlive = !isAlive;
            if (isAlive) {
                index = 1;
            }
            return Formatter.formatResponse("switched to " + (isAlive ? ALIVE : DIEING));

        }

        public Object alive(Request request, Response response) {
            if (!isAlive) {
                return swap(request, response);
            }
            return Formatter.formatResponse("already " + ALIVE);
        }

        public Object die(Request request, Response response) {
            if (isAlive) {
                return swap(request, response);
            }
            return Formatter.formatResponse("already " + DIEING);
        }
    }

    /**
     * Readyness endpoint {@code /readiness} to check service availability. Return either {@code 200 READY} or {@code 410 UNREADY}.
     */
    private static class ReadinessProbe implements Route {
        private final Gauge gaugh;
        private boolean isReady = true;
        private int index = 0;
        private int healingCout = AUTO_HEALING_COUNT;

        public ReadinessProbe(MetricRegistry metricRegistry) {
            this.gaugh = metricRegistry.gauge(READINESS_GAUGH, () -> (Gauge<Integer>) () -> isReady ? 1 : 0);
        }

        @Override
        public Object handle(Request request, Response response) throws Exception {
            gaugh.getValue();
            int status = STATUS_LOCKED;

            if (isReady) {
                return Formatter.formatResponse(READY);
            } else if (index == healingCout) {
                index = 0;
                return swap(request, response);
            } else if (index < healingCout) {
                index++;
            }
            String body = String.format("%s since %s requests - %s requests until switch back to %s", UNREADY, index, healingCout - index, READY);
            response.status(status);
            return Formatter.formatResponse(body, status);
        }

        public Object swap(Request request, Response response) {
            isReady = !isReady;
            if (isReady) {
                index = 0;
            } else {
                try {
                    healingCout = Integer.valueOf(request.body());
                    System.out.println(">> set healingCount=" + healingCout);
                } catch (Exception e) {
                    System.out.printf("Readiness swapping fail with %s because - %s\n",e.getClass().getSimpleName(), e.getMessage());
                    healingCout = AUTO_HEALING_COUNT;
                    System.out.println(">> set default healingCount=" + healingCout);
                }
            }
            return Formatter.formatResponse("switched to " + (isReady ? READY : UNREADY));
        }

        public Object unready(Request request, Response response) {
            if (isReady) {
                return swap(request, response);
            }
            return Formatter.formatResponse("already " + UNREADY, STATUS_GONE);
        }

        public Object ready(Request request, Response response) {
            if (!isReady) {
                return swap(request, response);
            }
            return Formatter.formatResponse("already " + READY);
        }
    }

    /**
     * Service endpoint {@code /} to simulate a http service endpoint under control of kubernetes.
     */
    private static class HomeEndpoint implements Route {
        private final Timer timer;

        public HomeEndpoint(MetricRegistry registry) {
            timer = registry.timer(REQUEST_TIMER);
        }

        @Override
        public Object handle(Request request, Response response) throws Exception {
            String body = "";

            try (Timer.Context context = timer.time()) {
                long millisToSleep = ThreadLocalRandom.current().nextLong(HTTP_REQUEST_PROCESSING_RANGE_IN_MILLIS);
                Thread.sleep(millisToSleep);
                body = Formatter.formatResponse("request time was '" + millisToSleep + "' millis");
                response.body(body);
            } catch (Exception e) {
                response.status(STATUS_INTERNAL_ERROR);
                body = Formatter.formatResponse(e.getClass().getSimpleName() + " - " + e.getMessage(), STATUS_INTERNAL_ERROR);
            }
            return body;
        }
    }

    /**
     * Wrapper surround the linux {@code stress [OPTION [ARG]]} command. <br/> Multiple calls increase the stress level like this.
     * <li>First call run - {@code stress --cpu 1 --io 1 --vm 1 --hdd 1 --timeout [SECONDS]}</li>
     * <li>Second call run - {@code stress --cpu 2 --io 2 --vm 2 --hdd 2 --timeout [SECONDS]}</li>
     * <li>Second call run - {@code stress --cpu 3 --io 3 --vm 3 --hdd 3 --timeout [SECONDS]}</li>
     *
     * @see {@link http://linux.die.net/man/1/stress}
     */
    private static class StressEndpoint implements Route {
        private final SystemStresser stresser = new SystemStresser();
        private boolean underStress = false;

        @Override
        public Object handle(Request request, Response response) throws Exception {
            String body = "System is not under stress. Use GET/POST /stress/start to stress the system.";
            if (underStress) {
                body = "System is under stress with - " + stresser.toString();
            }
            return Formatter.formatResponse(body);
        }

        public Object unstress(Request request, Response response) {
            String body = "System is not under stress. Use POST /stress [SECONDS] to start system stress. Afterwards it can unstressed.";
            if (underStress) {
                body = "Stop stressing of system - " + stresser.toString();
                try {
                    stresser.unstress();
                    underStress = false;
                } catch (Exception e) {
                    body = String.format("%s. %s because - %s", body, e.getClass().getSimpleName(), e.getMessage());
                    response.status(STATUS_INTERNAL_ERROR);
                }
            }
            return Formatter.formatResponse(body);
        }

        public Object stress(Request request, Response response) {
            String body = "";
            try {
                Optional<Duration> stressPeriodInSeconds = Optional.ofNullable(request.body())
                        .map(string -> string.isEmpty() ? null : string)
                        .map(Long::valueOf)
                        .map(Duration::ofSeconds);
                stresser.stress(stressPeriodInSeconds);
                body = stresser.toString();
                underStress = true;
            } catch (Exception e) {
                body = String.format("%s occur because - %s", e.getClass().getSimpleName(), e.getMessage());
                response.status(STATUS_INTERNAL_ERROR);
            }
            return Formatter.formatResponse(body);
        }
    }

    private static class MemoryEndpoint implements Route {

        private final HeapConsumer memory;

        MemoryEndpoint() {
            this.memory = new HeapConsumer();
        }

        @Override
        public Object handle(Request request, Response response) throws Exception {
            String body = request.body();
            String responseBody = "";
            if (body == null || body.isEmpty()) {
                responseBody = "Heap consumption is " + memory.toString() + ". Use GET/POST /memory/start [%] to reset the maximal value.";
            } else {
                responseBody = "Consume /memory [" + body + "]";
                try {
                    int memoryEatingBarrier = Integer.valueOf(body);
                    memory.consume(memoryEatingBarrier);
                    System.out.println(">> set memory consumption =" + memoryEatingBarrier);
                } catch (Exception e) {
                    responseBody = "/memory [" + body + "] is not betwenn [0 - 100].";
                    System.out.println(">>  " + responseBody);
                    response.status(STATUS_BAD_REQUEST);
                }
            }
            return Formatter.formatResponse(responseBody);
        }

        Object setConsumption(Request request, Response response) throws Exception {
            return handle(request, response);
        }

        public Object stopConsumption(Request request, Response response) {
            memory.consume(5);
            System.out.println(">> set memory consumption back to 5%");
            return Formatter.formatResponse("stop memory consumption");
        }
    }

    private static class MetricEndpoint implements Route {

        private final DropwizardExports prometheus;

        public MetricEndpoint(DropwizardExports prometheus) {
            this.prometheus = Objects.requireNonNull(prometheus, "'prometheus' must not be null.");
        }

        @Override
        public Object handle(Request request, Response response) throws Exception {
            String metrics = "";
            try (PrintWriter writer = response.raw().getWriter()) {
                TextFormat.write004(writer, new Vector<>(prometheus.collect()).elements());
                writer.flush();
                metrics = writer.toString();
                response.body(metrics);
            } catch (IOException e) {
                response.status(STATUS_INTERNAL_ERROR);
                response.body(e.getMessage());
            }
            return Formatter.formatResponse(metrics);
        }
    }

    /**
     * Formatter for response body.
     */
    private static class Formatter {
        private static String formatResponse(String message) {
            int status = STATUS_OK;
            return formatResponse(message, status);
        }

        private static String formatResponse(String message, int status) {
            return String.format("%3s [%s] %s", status, hostinfo(), message);
        }

        private static String hostinfo() {
            InetAddress localHost = null;
            try {
                localHost = InetAddress.getLocalHost();
            } catch (UnknownHostException e) {
                return String.format("Unexpected %s - because %s", e.getClass().getSimpleName(), e.getMessage());
            }
            String hostInfo = String.format("%-15s %15s", localHost.getHostAddress(), localHost.getHostName());
            return hostInfo;
        }
    }
}
