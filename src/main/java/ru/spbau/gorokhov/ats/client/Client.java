package ru.spbau.gorokhov.ats.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.spbau.gorokhov.ats.client.utils.Clock;
import ru.spbau.gorokhov.ats.model.ClientAddress;
import ru.spbau.gorokhov.ats.model.Request;
import ru.spbau.gorokhov.ats.model.SyncInfo;
import ru.spbau.gorokhov.ats.model.TimeInfo;
import ru.spbau.gorokhov.ats.utils.Serializer;
import ru.spbau.gorokhov.ats.utils.Sleepyhead;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class Client {
    private static int id = 1;
    private Logger LOG = LoggerFactory.getLogger(String.format("Client-%d", id++));

    private static final Random RANDOM = new Random(System.currentTimeMillis());

    private static final int DEFAULT_SERVER_PORT = 8080;

    private final String serverHostname;
    private final int serverPort;
    private int localPort;

    private final Clock clock;

    private static final long UPDATE_DELAY = 4000;
    private static final long SEND_DATA_DELAY = 1000;

    private static final long PACKET_DELIVERY_DELAY = 1;

    private static final long STAGE_TIME = 50000;
    private long startTime;

    private static final double RELATIVE_SKEW_TUNE = 0.6;
    private static final double SKEW_TUNE = 0.6;
    private static final double OFFSET_ERROR_TUNE = 0.6;

    private final Map<ClientAddress, Double> relativeSkew = new TreeMap<>();
    private final Map<ClientAddress, Long> lastClientTime = new TreeMap<>();
    private final Map<ClientAddress, Long> lastLocalTime = new TreeMap<>();

    private double skew = 1;
    private double offsetError = 0;

    private final List<ClientAddress> neighbours = new ArrayList<>();

    private boolean running = false;

    public Client(String serverHostname, int serverPort) {
        this.serverHostname = serverHostname;
        this.serverPort = serverPort;

        clock = new Clock();
    }

    public Client(String serverHostname) {
        this(serverHostname, DEFAULT_SERVER_PORT);
    }

    public void connect() throws IOException {
        running = true;

        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                localPort = socket.getLocalPort();

                LOG.info("Client started. Listening to {} port.", localPort);

                try {
                    register();

                    LOG.info("Connected to the server.");
                } catch (IOException e) {
                    LOG.error("Failed to connect to the server.", e);
                }

                sendTime();

                updateNeighbours();

                byte[] data = new byte[128];

                startTime = Clock.getRealTime();

                while (running) {
                    DatagramPacket packet = new DatagramPacket(data, data.length);

                    try {
                        socket.receive(packet);
                    } catch (IOException e) {
                        LOG.error("Failed to receive packet.", e);
                        continue;
                    }

                    long localTime = clock.getTime() - PACKET_DELIVERY_DELAY;

                    SyncInfo syncInfo;

                    try {
                        syncInfo = Serializer.deserialize(packet.getData(), SyncInfo.class);
                    } catch (IOException | ClassNotFoundException e) {
                        LOG.error("Failed to deserialize sync info.", e);
                        continue;
                    }

                    ClientAddress clientAddress = new ClientAddress(packet.getAddress().getHostAddress(), syncInfo.getPort());

                    LOG.info("Got sync info from {}: {}", clientAddress, syncInfo);

                    process(clientAddress, syncInfo.getTime(), syncInfo.getSkew(), syncInfo.getOffset(), localTime);
                }
            } catch (SocketException e) {
                LOG.error("Failed to start client.", e);
            }
        }).start();

        new Thread(() -> {
            Sleepyhead.sleep(3000);

            while (running) {
                Sleepyhead.sleep(SEND_DATA_DELAY);

                sendData();
            }
        }).start();

        new Thread(() -> {
            while (running) {
                Sleepyhead.sleep(UPDATE_DELAY);

                updateNeighbours();

                Sleepyhead.sleep(UPDATE_DELAY);

                sendTime();

                showDebugInfo();
            }
        }).start();
    }

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");

    private void showDebugInfo() {
        System.out.println("\n"+
                String.format("port=%d", localPort) + "\n" +
                String.format("Work time=%d", getWorkTime()) + "\n" +
                String.format("time=%s, timeV=%s", DATE_FORMAT.format(new Date(clock.getTime())), DATE_FORMAT.format(new Date(getTime()))) + "\n" +
                "alpha=" + clock.getSkew() + " beta=" + clock.getOffset() + " alpha^=" + skew + " o^=" + offsetError + "\n" +
                relativeSkew + "\n");
    }

    private synchronized void process(ClientAddress clientAddress, long clientTime, double clientSkew, double clientOffsetError, long localTime) {
        if (!relativeSkew.containsKey(clientAddress)) {
            relativeSkew.put(clientAddress, 1D);
        }

        long workTime = Clock.getRealTime() - startTime;

        double currentRelativeSkew = relativeSkew.get(clientAddress);

        if (workTime < STAGE_TIME && lastLocalTime.containsKey(clientAddress)) {
            long prevClientTime = lastClientTime.get(clientAddress);
            long prevLocalTime = lastLocalTime.get(clientAddress);

            double newRelativeSkew = RELATIVE_SKEW_TUNE * currentRelativeSkew + (1 - RELATIVE_SKEW_TUNE) * (clientTime - prevClientTime) / (localTime - prevLocalTime);
            relativeSkew.put(clientAddress, newRelativeSkew);
        } else if (workTime > STAGE_TIME && workTime < 2 * STAGE_TIME) {
            skew = SKEW_TUNE * skew + (1 - SKEW_TUNE) * currentRelativeSkew * clientSkew;
        } else if (workTime > 2 * STAGE_TIME){
            offsetError = offsetError + (1 - OFFSET_ERROR_TUNE) * (clientSkew * clientTime + clientOffsetError - skew * localTime - offsetError);
        }

        lastClientTime.put(clientAddress, clientTime);
        lastLocalTime.put(clientAddress, localTime);
    }

    private long getWorkTime() {
        return Clock.getRealTime() - startTime;
    }

    private void register() throws IOException {
        try (Socket socket = new Socket(serverHostname, serverPort);
             DataOutputStream serverInput = new DataOutputStream(socket.getOutputStream())) {

            serverInput.writeInt(localPort);

            serverInput.writeInt(Request.REGISTER);

        } catch (IOException rethrown) {
            throw rethrown;
        }
    }

    private void updateNeighbours() {
        try (Socket socket = new Socket(serverHostname, serverPort);
             DataOutputStream serverInput = new DataOutputStream(socket.getOutputStream());
             DataInputStream serverOutput = new DataInputStream(socket.getInputStream())) {

            serverInput.writeInt(localPort);

            serverInput.writeInt(Request.UPDATE_NEIGHBOURS);

            List<ClientAddress> newNeighbours = new ArrayList<>();

            int count = serverOutput.readInt();

            while (count --> 0) {
                String ip = serverOutput.readUTF();
                int port = serverOutput.readInt();
                newNeighbours.add(new ClientAddress(ip, port));
            }

            synchronized (neighbours) {
                for (ClientAddress neighbour : newNeighbours) {
                    if (!neighbours.contains(neighbour)) {
                        neighbours.add(neighbour);
                    }
                }
            }

            // TODO: handle removed neighbours
            LOG.info("Got new neighbours: {}", newNeighbours);
        } catch (IOException e) {
            LOG.error("Failed to update neighbours.", e);
        }
    }

    private void sendTime() {
        try (Socket socket = new Socket(serverHostname, serverPort);
             DataOutputStream serverInput = new DataOutputStream(socket.getOutputStream())) {

            serverInput.writeInt(localPort);

            serverInput.writeInt(Request.SEND_TIME);

            TimeInfo timeInfo = new TimeInfo(skew * clock.getSkew(), skew * clock.getOffset() + offsetError);

            serverInput.writeDouble(timeInfo.getSkew());
            serverInput.writeDouble(timeInfo.getOffset());

            LOG.info("Time info was sent: {}", timeInfo);
        } catch (IOException e) {
            LOG.error("Failed to send time info.", e);
        }
    }

    private ClientAddress chooseNeighbour() {
        synchronized (neighbours) {
            int count = neighbours.size();

            if (count == 0) {
                return null;
            }

            return neighbours.get(RANDOM.nextInt(count));
        }
    }

    private void sendData() {
        if (neighbours.size() == 0) {
            return;
        }

        ClientAddress neighbour = chooseNeighbour();

        try (DatagramSocket socket = new DatagramSocket()) {
            SyncInfo syncInfo;

            synchronized (this) {
                syncInfo = new SyncInfo(localPort, clock.getTime(), skew, offsetError);
            }

            byte[] data = Serializer.serialize(syncInfo);
            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(neighbour.getIp()), neighbour.getPort());
            socket.send(packet);

            LOG.info("Sync info was sent to {}: {}", neighbour, syncInfo);
        } catch (IOException e) {
            LOG.error("Failed to send sync info to {}.", neighbour, e);
        }
    }

    public void disconnect() {
        running = false;
    }

    public long getTime() {
        return (long) (skew * clock.getTime() + offsetError);
    }
}
