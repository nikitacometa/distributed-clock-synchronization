package ru.spbau.gorokhov.ats.client;

import ru.spbau.gorokhov.ats.client.utils.Clock;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Client {
    private final String id = UUID.randomUUID().toString();

    private static final String DEFAULT_SERVER_HOSTNAME = "localhost";
    private static final int DEFAULT_SERVER_PORT = 8080;

    private final String serverHostname;
    private final int serverPort;

    private final Clock clock;

    private static final double relativeSkewTune = 0.6;
    private static final double skewTune = 0.6;
    private static final double offsetErrorTune = 0.6;

    private final Map<String, Double> relativeSkew = new HashMap<>();

    // relative to virtual clock skew estimate
    private double skew = 1;

    private double offsetError = 0;


    public Client(String serverHostname, int serverPort) {
        this.serverHostname = serverHostname;
        this.serverPort = serverPort;

        clock = new Clock();
    }

    public Client() {
        this(DEFAULT_SERVER_HOSTNAME, DEFAULT_SERVER_PORT);
    }

    public void connect() throws IOException {
        SocketChannel channel = SocketChannel.open(new InetSocketAddress(serverHostname, serverPort));
        channel.configureBlocking(false);

        Selector selector = Selector.open();
        channel.register(selector, SelectionKey.OP_READ);

        long start = System.currentTimeMillis();
        int i = 0;

        while (true) {
            if (System.currentTimeMillis() - start < 3000) {
                int readyKeys = selector.selectNow();

                if (readyKeys == 0) {
                    continue;
                }

                System.out.println("lal");

                ByteBuffer buffer = ByteBuffer.allocate(2048);
                channel.read(buffer);
                String message = new String(buffer.array()).trim();

                System.out.println(message);
            } else {
                i++;
                String message = id + " " + i;
                ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
                channel.write(buffer);

                start = System.currentTimeMillis();
            }
        }
    }

    public long getTime() {
        return (long) (skew * clock.getTime() + offsetError);
    }

    public static void main(String[] args) throws IOException {
        new Client().connect();
    }
}
