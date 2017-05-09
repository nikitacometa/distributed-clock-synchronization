package ru.spbau.gorokhov.ats.client;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.spbau.gorokhov.ats.client.utils.Clock;
import ru.spbau.gorokhov.ats.utils.ClientRequest;
import ru.spbau.gorokhov.ats.utils.ServerRequest;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;

public class Client {
    private static final Logger LOG = LoggerFactory.getLogger(Client.class);

    private static final String DEFAULT_SERVER_HOSTNAME = "localhost";
    private static final int DEFAULT_SERVER_PORT = 8080;
    private static final int DEFAULT_PORT = 12345;

    private final String serverHostname;
    private final int serverPort;
    private final int port;

    private final Clock clock;

    private static final int SEND_DELAY = 1000;

    private static final double RELATIVE_SKEW_TUNE = 0.6;
    private static final double SKEW_TUNE = 0.6;
    private static final double OFFSET_ERROR_TUNE = 0.6;

    private final Map<String, Double> relativeSkew = new HashMap<>();
    private final Map<String, Long> lastClientTime = new HashMap<>();
    private final Map<String, Long> lastLocalTime = new HashMap<>();

    // relative to virtual clock skew estimate
    private double skew = 1;
    private double offsetError = 0;

    private final List<String> neighbourIps = new ArrayList<>();

    private boolean running = false;


    public Client(String serverHostname, int serverPort, int port) {
        this.serverHostname = serverHostname;
        this.serverPort = serverPort;
        this.port = port;

        clock = new Clock();
    }

    public Client() {
        this(DEFAULT_SERVER_HOSTNAME, DEFAULT_SERVER_PORT, DEFAULT_PORT);
    }

    public void connect() throws IOException {
        running = true;

        register();

        getNeighbours();

        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                while (running) {
                    Socket newConnection = serverSocket.accept();
                    new Thread(new RequestHandler(newConnection)).start();
                }
            } catch (IOException e) {
                // TODO
                LOG.error("smth went wrong");
            }
        }).start();

        new Thread(() -> {
            while (running) {
                sendData();

                try {
                    Thread.sleep(SEND_DELAY);
                } catch (InterruptedException e) {
                    // TODO
                    LOG.error("wtf");
                }
            }
        }).start();
    }

    private void register() {
        try (Socket serverSocket = new Socket(serverHostname, serverPort);
             DataOutputStream serverInput = new DataOutputStream(serverSocket.getOutputStream())) {

            serverInput.writeInt(ServerRequest.REGISTER);
        } catch (UnknownHostException e) {
            // TODO
            LOG.error("hz");
        } catch (IOException e) {
            // TODO
            LOG.error("hz1");
        }
    }

    private void getNeighbours() {
        try (Socket serverSocket = new Socket(serverHostname, serverPort);
            DataInputStream serverOutput = new DataInputStream(serverSocket.getInputStream());
            DataOutputStream serverInput = new DataOutputStream(serverSocket.getOutputStream())) {

            serverInput.writeInt(ServerRequest.UPDATE_NEIGHBOURS);

            int neighboursCount = serverOutput.readInt();

            synchronized (neighbourIps) {
                neighbourIps.clear();
                while (neighboursCount-- > 0) {
                    String neighbourIp = serverOutput.readUTF();

                    neighbourIps.add(neighbourIp);
                    if (!relativeSkew.containsKey(neighbourIp)) {
                        relativeSkew.put(neighbourIp, 1D);
                        lastClientTime.put(neighbourIp, 0L);
                        lastLocalTime.put(neighbourIp, 0L);
                    }
                }
            }
        } catch (UnknownHostException e) {
            // TODO
            LOG.error("hz");
        } catch (IOException e) {
            // TODO
            LOG.error("hz1");
        }
    }

    private void sendData() {
        Random random = new Random(System.currentTimeMillis());

        synchronized (this) {
            String targetIp = neighbourIps.get(random.nextInt(neighbourIps.size()));

            try (Socket socket = new Socket(targetIp, port);
                DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream())) {

                outputStream.writeInt(ClientRequest.SEND_DATA);

                outputStream.writeLong(clock.getTime());
                outputStream.writeDouble(skew);
                outputStream.writeDouble(offsetError);
            } catch (UnknownHostException e) {
                // TODO
                LOG.error("hz");
            } catch (IOException e) {
                // TODO
                LOG.error("hz1");
            }
        }
    }

    public void disconnect() {
        running = false;
    }

    public long getTime() {
        return (long) (skew * clock.getTime() + offsetError);
    }

    public static void main(String[] args) throws IOException {
        new Client().connect();
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
                    case ClientRequest.SEND_DATA:
                        long clientTime = clientOutput.readLong();
                        long localTime = clock.getTime();

                        double clientSkew = clientOutput.readDouble();
                        double clientOffsetError = clientOutput.readDouble();

                        synchronized (Client.this) {
                            long prevClientTime = lastClientTime.get(clientIp);
                            long prevLocalTime = lastLocalTime.get(clientIp);
                            double currentRelativeSkew = relativeSkew.get(clientIp);

                            double newRelativeSkew = RELATIVE_SKEW_TUNE * currentRelativeSkew + (1 - RELATIVE_SKEW_TUNE) * (clientTime - prevClientTime) / (localTime - prevLocalTime);

                            skew = SKEW_TUNE * skew + (1 - SKEW_TUNE) * newRelativeSkew * clientSkew;

                            offsetError = OFFSET_ERROR_TUNE * offsetError + (1 - OFFSET_ERROR_TUNE) * (clientSkew * clientTime + clientOffsetError - skew * localTime - offsetError);

                            relativeSkew.put(clientIp, newRelativeSkew);
                            lastClientTime.put(clientIp, clientTime);
                            lastLocalTime.put(clientIp, localTime);
                        }
                }
            } catch (IOException e) {
                // TODO
                LOG.error("bad");
            }
        }
    }
}
