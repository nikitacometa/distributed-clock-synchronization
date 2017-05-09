package ru.spbau.gorokhov.ats.server;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.spbau.gorokhov.ats.utils.ServerRequest;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


public class Server {
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);

    private static String DEFAULT_HOSTNAME = "localhost";
    private static final int DEFAULT_PORT = 8080;

    private final String hostname;
    private final int port;

    private final List<String> clientIps = new ArrayList<>();

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
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                while (running) {
                    Socket newConnection = serverSocket.accept();
                    new Thread(new RequestHandler(newConnection)).start();
                }
            } catch (IOException e) {
                // TODO
                LOG.error("hz");
            }
        }).start();
    }

    public void stop() {
        running = false;
    }

    public static void main(String[] args) {
        new Server().start();
    }

    private List<String> getHeighbours(String clientIp) {
        // TODO something clever
        return clientIps.stream().filter(s -> !s.equals(clientIp)).collect(Collectors.toList());
    }

    @RequiredArgsConstructor
    private class RequestHandler implements Runnable {
        private final Socket clientSocket;

        @Override
        public void run() {
            String clientIp = clientSocket.getInetAddress().getHostAddress();

            try (DataInputStream clientOutput = new DataInputStream(clientSocket.getInputStream());
                 DataOutputStream clientInput = new DataOutputStream(clientSocket.getOutputStream())) {
                int requestId = clientOutput.readInt();
                switch (requestId) {
                    case ServerRequest.REGISTER:
                        synchronized (clientIps) {
                            clientIps.add(clientIp);
                        }
                        break;

                    case ServerRequest.UPDATE_NEIGHBOURS:
                        List<String> neighbourIps;

                        synchronized (Server.this) {
                            neighbourIps = getHeighbours(clientIp);
                        }

                        clientInput.writeInt(neighbourIps.size());
                        for (String ip : neighbourIps) {
                            clientInput.writeUTF(ip);
                        }

                        break;

                    case ServerRequest.SEND_TIME:
                        // TODO
                }
            } catch (IOException e) {
                LOG.error("Failed to handle new connection.");
            }
        }
    }
}
