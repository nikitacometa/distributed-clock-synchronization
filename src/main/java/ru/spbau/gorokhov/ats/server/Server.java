package ru.spbau.gorokhov.ats.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class Server {
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);

    private static String DEFAULT_HOSTNAME = "localhost";
    private static final int DEFAULT_PORT = 8080;

    private final String hostname;
    private final int port;

    private boolean running = false;

    public Server(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
    }

    public Server() {
        this(DEFAULT_HOSTNAME, DEFAULT_PORT);
    }

    public void start() {
        running = true;

        new Thread(() -> {
            try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
                 Selector selector = Selector.open()
            ) {
                serverSocketChannel.bind(new InetSocketAddress(hostname, port));
                serverSocketChannel.configureBlocking(false);
                serverSocketChannel.register(selector, serverSocketChannel.validOps());

                LOG.info("Server is running...");

                while (running) {
                    int readyChannels = selector.selectNow();

                    if (readyChannels == 0) {
                        continue;
                    }

                    LOG.info("Have a new connection.");

                    Set<SelectionKey> keys = selector.selectedKeys();
                    Iterator<SelectionKey> it = keys.iterator();

                    List<SelectionKey> clientKeys = new ArrayList<>();

                    while (it.hasNext()) {
                        SelectionKey key = it.next();
                        it.remove();

                        if (key.isAcceptable()) {
                            SocketChannel client = serverSocketChannel.accept();
                            client.configureBlocking(false);
                            SelectionKey clientKey = client.register(selector, SelectionKey.OP_READ);
                            clientKeys.add(clientKey);
                        } else if (key.isReadable()) {
                            SocketChannel client = (SocketChannel) key.channel();
                            ByteBuffer buffer = ByteBuffer.allocate(256);
                            client.read(buffer);
                            String output = new String(buffer.array()).trim();

                            System.out.println("Message read from client: " + output);

                            for (SelectionKey keey : clientKeys) {
                                SocketChannel channel = (SocketChannel) keey.channel();
                                buffer.rewind();
                                channel.write(buffer);
                            }
                        }
                    }

                }
            } catch (IOException e) {
                System.out.println("Server will stop unexpectedly:");
                e.printStackTrace();
            }
        }).start();
    }

    public void stop() {
        running = false;
    }

    public static void main(String[] args) {
        new Server().start();
    }
}
