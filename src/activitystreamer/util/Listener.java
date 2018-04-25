package activitystreamer.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;


public class Listener extends Thread {
    protected static final Logger log = LogManager.getLogger();
    protected static Listener listener;

    protected ServerSocket serverSocket = null;
    protected boolean term = false;
    protected int portnum;

    protected Listener() throws IOException {
        portnum = Settings.getLocalPort();    // keep our own copy in case it
        // changes later
        serverSocket = new ServerSocket(portnum);

        start();
    }

    @Override
    public void run() {

    }

    public void setTerm(boolean term) {
        this.term = term;

        if (term) {
            try {
                serverSocket.close();
            } catch (IOException io) {
                log.error("Server socket closed error");
            }
        }
    }
}
