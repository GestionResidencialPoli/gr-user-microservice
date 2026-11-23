package com.uni.usermicroservice.communications;

import com.uni.usermicroservice.internal.InternalServiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class CommunicationsClient implements SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommunicationsClient.class);
    private static final String SERVICE_NAME = "user-microservice";
    private static final long CHECK_SECONDS = 3;
    private static final long SILENCE_LIMIT_NANOS = Duration.ofSeconds(60).toNanos();
    private static final int WORKERS = 4;

    private final String url;
    private final String token;
    private final CommunicationsRequestHandler handler;

    private ScheduledExecutorService supervisor;
    private ExecutorService workers;
    private ExecutorService sender;
    private HttpClient httpClient;
    private volatile Listener current;
    private volatile boolean running;

    public CommunicationsClient(
            @Value("${communications.url:}") String url,
            InternalServiceProperties internalServiceProperties,
            CommunicationsRequestHandler handler
    ) {
        this.url = url.strip();
        this.token = internalServiceProperties.token();
        this.handler = handler;
    }

    @Override
    public synchronized void start() {
        if (running || url.isEmpty()) {
            return;
        }
        supervisor = Executors.newSingleThreadScheduledExecutor();
        workers = Executors.newFixedThreadPool(WORKERS);
        sender = Executors.newSingleThreadExecutor();
        httpClient = HttpClient.newHttpClient();
        running = true;
        supervisor.scheduleWithFixedDelay(this::supervise, 0, CHECK_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        supervisor.shutdownNow();
        workers.shutdownNow();
        sender.shutdownNow();
        if (current != null) {
            current.abort();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public void notifyUser(String eventName, Long userId) {
        Listener listener = current;
        if (!running || listener == null || !listener.isAlive()) {
            LOGGER.warn("No se pudo emitir {}: el hub no esta conectado", eventName);
            return;
        }
        String frame = "{\"type\":\"event\",\"name\":\"%s\",\"audience\":{\"userIds\":[%d]},\"payload\":{}}"
                .formatted(eventName, userId);
        try {
            sender.execute(() -> listener.send(frame));
        } catch (RejectedExecutionException exception) {
            LOGGER.warn("No se pudo emitir {}: el servicio se esta deteniendo", eventName);
        }
    }

    private void supervise() {
        if (current != null && current.isAlive()) {
            return;
        }
        if (current != null) {
            current.abort();
        }

        Listener listener = new Listener();
        try {
            httpClient.newWebSocketBuilder()
                    .header("X-Internal-Token", token)
                    .header("X-Service-Name", SERVICE_NAME)
                    .connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(URI.create(url), listener)
                    .get(10, TimeUnit.SECONDS);
            current = listener;
            LOGGER.info("Conectado al hub de comunicaciones");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            listener.abort();
            LOGGER.warn("Hub de comunicaciones no disponible, se reintenta: {}", exception.getMessage());
        }
    }

    private final class Listener implements WebSocket.Listener {

        private final StringBuilder buffer = new StringBuilder();
        private volatile WebSocket socket;
        private volatile long lastActivity = System.nanoTime();
        private volatile boolean closed;

        boolean isAlive() {
            return !closed && System.nanoTime() - lastActivity < SILENCE_LIMIT_NANOS;
        }

        void abort() {
            closed = true;
            if (socket != null) {
                socket.abort();
            }
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            socket = webSocket;
            if (closed) {
                webSocket.abort();
                return;
            }
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            lastActivity = System.nanoTime();
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                workers.execute(() -> process(message));
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            lastActivity = System.nanoTime();
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed = true;
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closed = true;
            LOGGER.warn("Conexion con el hub interrumpida: {}", error.getMessage());
        }

        private void process(String message) {
            try {
                handler.handle(message).ifPresent(response -> sender.execute(() -> send(response)));
            } catch (RuntimeException exception) {
                LOGGER.warn("Mensaje del hub descartado: {}", exception.getMessage());
            }
        }

        void send(String message) {
            try {
                socket.sendText(message, true).get(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                LOGGER.warn("No se pudo responder al hub: {}", exception.getMessage());
                abort();
            }
        }
    }
}
