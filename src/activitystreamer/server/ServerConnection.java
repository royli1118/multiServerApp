package activitystreamer.server;

import activitystreamer.util.Connection;
import activitystreamer.util.Settings;

import java.io.IOException;
import java.net.Socket;




/**
 * This class implements the connection from or to a Client.
 *
 * @author Zelei Cui
 *
 */
public class ServerConnection extends Connection {

    public ServerConnection(Socket socket) throws IOException {
        super(socket);
    }

    public void run() {
        log.info("connection running");

        String data;

        try {
            while (!term && (data = inreader.readLine()) != null) {
                term = ServerControl.getInstance().process(this, data);
            }

            log.debug("connection closed to " + Settings.socketAddress(socket));
        } catch (IOException e) {
            log.error("connection " + Settings.socketAddress(socket) + " closed with exception: " + e);

            ServerControl.getInstance().connectionClosed(this);
        }

        ServerControl.getInstance().connectionClosed(this);
        closeStream();
    }
}
