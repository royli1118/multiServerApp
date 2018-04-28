package activitystreamer.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

/**
 * This class is a utility class which implement the connections from servers or clients
 *
 * @author Huanan Li
 *
 */
public class Control extends Thread {
    protected static final Logger log = LogManager.getLogger();
    protected static Control control;
    protected static Listener listener;
    protected static boolean term = false;
    private static ArrayList<Connection> connections;

    protected Control() {
        // initialize the connections array
        connections = new ArrayList<Connection>();
    }

    public boolean initiateConnection(int port, String host, boolean toLoadBalancer) {
        // make a connection to another server if remote hostname is supplied
        if (host != null) {
            try {
                outgoingConnection(new Socket(host, port));

                return true;
            } catch (UnknownHostException e) {
                log.info("Server establish ssl connection failed. Unknown Host: " + e.getMessage());

                System.exit(-1);
            } catch (IOException e) {
                log.error("Server failed to make plain connection to " + host + ":"
                        + port + " :" + e);

                return false;
            }
        }

        return false;
    }

    /**
     * Processing incoming messages from the Server.
     * Return true if the connection should close.
     * @param con
     * @param msg
     * @return boolean
     */
    public synchronized boolean process(Connection con, String msg) {
        return true;
    }

    /*
     * The connection has been closed by the other party.
     */
    public synchronized void connectionClosed(Connection con) {
        if (!term) {
            connections.remove(con);
        }
    }

    /*
     * A new incoming connection has been established, and a reference is
     * returned to it
     */
    public synchronized Connection incomingConnection(Socket s) throws IOException {
        log.debug("incomming connection: " + Settings.socketAddress(s));

        Connection c = new Connection(s);
        connections.add(c);

        return c;
    }

    /*
     * A new outgoing connection has been established, and a reference is
     * returned to it
     */
    public synchronized Connection outgoingConnection(Socket s) throws IOException {
        log.debug("outgoing connection: " + Settings.socketAddress(s));
        Connection c = new Connection(s);
        connections.add(c);

        return c;
    }

    @Override
    public void run() {
        log.info("using activity interval of " + Settings.getActivityInterval() + " milliseconds");

        while (!term) {
            // do something with 5 second intervals in between
            try {
                Thread.sleep(Settings.getActivityInterval());
            } catch (InterruptedException e) {
                log.info("received an interrupt, system is shutting down");

                break;
            }
            if (!term) {
                log.debug("doing activity");

                term = doActivity();
            }
        }

        log.info("closing " + connections.size() + " connections");

        // clean up
        for (Connection connection : connections) {
            connection.closeCon();
        }

        listener.setTerm(true);
    }

    public boolean doActivity() {
        return false;
    }

    public final void setTerm(boolean t) {
        term = t;
    }
}
