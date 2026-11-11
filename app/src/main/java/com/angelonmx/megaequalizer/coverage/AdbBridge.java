package com.angelonmx.megaequalizer.coverage;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;

/**
 * Small ADB client used to read the playback sessions of the device itself.
 * The connection goes to loopback ({@code 127.0.0.1}), which is exactly the
 * daemon that wireless debugging exposes; LibADB upgrades plain TCP to TLS
 * automatically when the daemon requests it.
 *
 * <p>LibADB's streams cannot reliably be opened twice on the same connection,
 * so instead of asking for {@code dumpsys audio} on every poll we keep one
 * connection with one long-lived shell loop that prints a snapshot followed by
 * a marker every couple of seconds. Readers consume snapshots as they arrive.
 */
public final class AdbBridge implements Closeable {

    private static final int CONNECT_TIMEOUT_SECONDS = 12;
    private static final int PAIR_TIMEOUT_SECONDS = 20;
    private static final int SNAPSHOT_TIMEOUT_SECONDS = 25;
    private static final int MAX_DUMP_BYTES = 4 * 1024 * 1024;
    private static final int SNAPSHOT_INTERVAL_SECONDS = 2;
    /** Sentinel so a dropped stream never leaves the poller waiting forever. */
    private static final String END_MARKER = "__MEGA_EQ_9F3C_END__";

    private final AdbIdentity identity;
    private AbsAdbConnectionManager manager;
    private int connectedPort;
    private SnapshotSession session;

    public AdbBridge(@NonNull AdbIdentity identity) {
        this.identity = identity;
    }

    public synchronized boolean isConnected() {
        return manager != null && manager.isConnected();
    }

    public synchronized int getConnectedPort() {
        return connectedPort;
    }

    /**
     * Connects to adbd on the loopback interface.
     *
     * @return true when a new connection was established, false when it was
     * already connected to the same port
     */
    public synchronized boolean connect(int port) throws Exception {
        if (manager != null && manager.isConnected() && connectedPort == port) {
            return false;
        }
        disconnectLocked();
        connectLocked(port);
        return true;
    }

    private void connectLocked(int port) throws Exception {
        manager = new Manager(identity);
        manager.setApi(Build.VERSION.SDK_INT);
        manager.setHostAddress("127.0.0.1");
        manager.setTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        manager.setThrowOnUnauthorised(true);
        boolean connected = manager.connect("127.0.0.1", port);
        if (!connected) {
            disconnectLocked();
            throw new IOException("ADB no aceptó la conexión");
        }
        connectedPort = port;
    }

    /**
     * Pairs this device's ADB identity with adbd using a wireless-debugging
     * pairing port and code. Pairing uses its own short-lived connection.
     */
    public synchronized boolean pair(int pairingPort, @NonNull String pairingCode) throws Exception {
        AbsAdbConnectionManager pairing = new Manager(identity);
        pairing.setApi(Build.VERSION.SDK_INT);
        pairing.setHostAddress("127.0.0.1");
        pairing.setTimeout(PAIR_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        try {
            return pairing.pair("127.0.0.1", pairingPort, pairingCode);
        } finally {
            try {
                pairing.disconnect();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Returns the next {@code dumpsys audio} snapshot from the persistent
     * session, waiting for it when the shell loop has not produced one yet.
     */
    @NonNull
    public String readDumpsysAudio() throws Exception {
        SnapshotSession current;
        synchronized (this) {
            if (manager == null || !manager.isConnected()) {
                throw new IOException("ADB no está conectado");
            }
            if (session != null && !session.isAlive()) {
                // LibADB cannot reliably open a second stream on a connection
                // whose first stream ended, so replace the whole connection.
                int port = connectedPort;
                disconnectLocked();
                connectLocked(port);
            }
            if (session == null) {
                session = new SnapshotSession(manager);
                session.start();
            }
            current = session;
        }
        return current.awaitSnapshot(SNAPSHOT_TIMEOUT_SECONDS * 1000L);
    }

    @Override
    public void close() {
        disconnect();
    }

    public void disconnect() {
        SnapshotSession current;
        AbsAdbConnectionManager connection;
        synchronized (this) {
            current = session;
            connection = manager;
            session = null;
            manager = null;
            connectedPort = 0;
        }
        if (connection != null) {
            try {
                // Closing the socket kicks the LibADB connection thread, which
                // in turn wakes the stream reader with a closed stream.
                connection.disconnect();
            } catch (IOException ignored) {
            }
        }
        if (current != null) {
            current.close();
        }
    }

    private void disconnectLocked() {
        SnapshotSession current = session;
        session = null;
        if (current != null) {
            current.close();
        }
        if (manager != null) {
            try {
                manager.disconnect();
            } catch (IOException ignored) {
            }
        }
        manager = null;
        connectedPort = 0;
    }

    /** One connection plus one long-lived stream producing snapshots. */
    private final class SnapshotSession {
        private final AbsAdbConnectionManager connection;
        private final Object lock = new Object();
        private Thread reader;
        private volatile boolean closed;
        private String latest;
        private int sequence;
        private Throwable failure;

        private SnapshotSession(AbsAdbConnectionManager connection) {
            this.connection = connection;
        }

        private boolean isAlive() {
            synchronized (lock) {
                return !closed && failure == null && reader != null && reader.isAlive();
            }
        }

        private void start() {
            reader = new Thread(this::readLoop, "adb-snapshot-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readLoop() {
            AdbStream stream = null;
            try {
                stream = connection.openStream(
                        "shell:while true; do dumpsys audio; echo " + END_MARKER
                                + "; sleep " + SNAPSHOT_INTERVAL_SECONDS + "; done");
                byte[] marker = END_MARKER.getBytes("UTF-8");
                try (InputStream input = stream.openInputStream();
                     ByteArrayOutputStream buffer = new ByteArrayOutputStream(65536)) {
                    byte[] chunk = new byte[8192];
                    int read;
                    while (!closed && (read = input.read(chunk)) != -1) {
                        buffer.write(chunk, 0, read);
                        int index;
                        while (!closed && (index = indexOf(buffer.toByteArray(), marker)) >= 0) {
                            byte[] data = buffer.toByteArray();
                            String snapshot = new String(data, 0, index, "UTF-8");
                            publish(snapshot);
                            byte[] rest = Arrays.copyOfRange(data, index + marker.length, data.length);
                            buffer.reset();
                            buffer.write(rest, 0, rest.length);
                        }
                        if (buffer.size() > MAX_DUMP_BYTES) {
                            throw new IOException("La salida de dumpsys audio es demasiado grande");
                        }
                    }
                }
                if (!closed) {
                    throw new IOException("La sesión de ADB terminó inesperadamente");
                }
            } catch (Throwable error) {
                if (!closed) {
                    fail(error);
                }
            } finally {
                try {
                    if (stream != null) {
                        stream.close();
                    }
                } catch (IOException ignored) {
                }
                synchronized (lock) {
                    closed = true;
                    lock.notifyAll();
                }
            }
        }

        private void publish(String snapshot) {
            synchronized (lock) {
                latest = snapshot;
                sequence++;
                lock.notifyAll();
            }
        }

        private void fail(Throwable error) {
            synchronized (lock) {
                if (failure == null) {
                    failure = error;
                }
                lock.notifyAll();
            }
        }

        @NonNull
        private String awaitSnapshot(long timeoutMillis) throws Exception {
            long deadline = SystemClock.elapsedRealtime() + timeoutMillis;
            synchronized (lock) {
                int seen = sequence;
                while (!closed || sequence != seen) {
                    if (sequence != seen) {
                        return latest;
                    }
                    if (failure != null) {
                        throw new IOException("La sesión de ADB falló: " + failure.getMessage(),
                                failure);
                    }
                    if (closed) {
                        break;
                    }
                    long remaining = deadline - SystemClock.elapsedRealtime();
                    if (remaining <= 0) {
                        throw new IOException("Tiempo de espera agotado al leer las sesiones de audio");
                    }
                    lock.wait(remaining);
                }
            }
            throw new IOException("La sesión de ADB terminó sin datos");
        }

        private void close() {
            synchronized (lock) {
                closed = true;
                lock.notifyAll();
            }
            Thread current = reader;
            if (current != null) {
                current.interrupt();
                try {
                    current.join(2000L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static int indexOf(@NonNull byte[] haystack, @NonNull byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static final class Manager extends AbsAdbConnectionManager {
        private final AdbIdentity identity;

        private Manager(AdbIdentity identity) {
            this.identity = identity;
        }

        @Override
        protected PrivateKey getPrivateKey() {
            return identity.newPrivateKey();
        }

        @Override
        protected Certificate getCertificate() {
            return identity.getCertificate();
        }

        @Override
        protected String getDeviceName() {
            return "Mega Equalizer";
        }
    }
}
