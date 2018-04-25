package activitystreamer.server;

import activitystreamer.util.Listener;

import java.io.IOException;
import java.net.Socket;


public class ServerListener extends Listener {

    private ServerListener() throws IOException {
        super();
    }

    protected static synchronized ServerListener getInstance() {
        if (listener == null) {
            try {
                listener = new ServerListener();
            } catch (IOException e) {
                log.debug("ServerListener init failed: " + e.getMessage());
            }
        }

        return (ServerListener) listener;
    }

    @Override
    public void run() {
        log.info("listening for new connections on " + portnum);

        while (!term) {
            try {
                Socket clientSocket = serverSocket.accept();
                ServerControl.getInstance().incomingConnection(clientSocket);
            } catch (IOException e) {
                log.info("received exception, shutting down");

                term = true;
            }
        }
    }
}
