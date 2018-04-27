package activitystreamer.server;

import Message.*;
import activitystreamer.util.Connection;
import activitystreamer.util.Control;
import activitystreamer.util.Settings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class deals with main logic of servers' behavior. It is responsible for processing clients'
 * request to login, logout, register, broadcast message and so forth. It also handles servers'
 * request to authenticate, redirect, server announce and so forth.
 *
 * @author Zelei Cui
 */

public class ServerControl extends Control {
    private static final Logger log = LogManager.getLogger();
    private static final int SERVER_CONNECTION_UPPER_LIMIT = 5;
    private static final int CLIENT_CONNECTION_UPPER_LIMIT = 4;
    private ArrayList<Connection> serverConnectionList = new ArrayList<>();
    private ArrayList<Connection> clientConnectionList = new ArrayList<>();
    private ArrayList<ServerSettings> serverInfoList = new ArrayList<>();
    private HashMap<String, String> clientInfoList = new HashMap<>();
    // authenticate id between servers
    private String id;

    private ServerControl() {
        super();

        // start a listener
        listener = ServerListener.getInstance();
        id = Settings.nextSecret();

        /*
         * Do some further initialization here if necessary
         */
        //connectToServer();

        // start the server's activity loop
        // it will call doActivity every few seconds
        start();
    }

    // since control and its subclasses are singleton, we get the singleton this
    // way
    public static synchronized ServerControl getInstance() {
        if (control == null) {
            control = new ServerControl();
        }
        return (ServerControl) control;
    }

    /*
     * a new incoming connection
     * @param A Socket to establish a connection
     * @return A ServerConnection object which requires connection
     */
    @Override
    public ServerConnection incomingConnection(Socket s) throws IOException {
        log.debug("incomming connection: " + Settings.socketAddress(s));

        ServerConnection con = new ServerConnection(s);

        return con;
    }

    /*
     * a new outgoing connection
     * @param A Socket to establish a connection
     * @return A ServerConnection object which requires connection
     */
    @Override
    public ServerConnection outgoingConnection(Socket s) throws IOException {
        ServerConnection con = new ServerConnection(s);

        // Send authentication message
        AuthMsg authJson = new AuthMsg();
        authJson.setSecret(Settings.getSecret());

        String authJsonStr = authJson.toJsonString();
        con.writeMsg(authJsonStr);

        serverConnectionList.add(con);
        return con;
    }

    /*
     * the connection has been closed
     * @param the connection to be closed
     */
    @Override
    public void connectionClosed(Connection con) {
        super.connectionClosed(con);

        if (!term && !serverConnectionList.remove(con)) {
            clientConnectionList.remove(con);
        }
    }

    /*
     * the connection has been closed
     * @param the connection to be closed
     * @return true if connection succeeds, false otherwise
     */
    public boolean initiateConnection(int port, String host) {
        // make a connection to another server if remote hostname is supplied
        if (host != null) {
            try {
                outgoingConnection(new Socket(host, port));
                return true;
            } catch (UnknownHostException e) {
                log.info("Server establish connection failed. Unknown Host: " + e.getMessage());
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
     * process incoming msg, from connection con return true if the connection
     * should be closed, false otherwise
     *
     * @param con message to be processed
     * @param msg message comes from connection con
     * @return true to close the connection, false otherwise
     */
    @Override
    public synchronized boolean process(Connection con, String msg) {
        log.debug("Server Receieved: " + msg);

        JsonObject receivedJsonObj;

        try {
            receivedJsonObj = new Gson().fromJson(msg, JsonObject.class);
        } catch (JsonSyntaxException e) {
            log.debug("Server receiving msg failed. Not json format: " + e.getMessage());
            //return true;
            return processInvalidString(con, "Server receiving msg failed. Not json format");
        }

        if (!containCommandField(con, receivedJsonObj)) {
            return true;
        }

        String msgType = receivedJsonObj.get("command").getAsString();

        switch (msgType) {
            case JsonMessage.LOGIN:
                return processLoginMsg(con, receivedJsonObj);

            case JsonMessage.AUTHENTICATE:
                return processAuthMsg(con, receivedJsonObj);

            case JsonMessage.AUTHENTICATION_FAIL:
                return processAuthFailedMsg(con, receivedJsonObj);

            case JsonMessage.LOGOUT:
                return processLogoutMsg(con, receivedJsonObj);

            case JsonMessage.INVALID_MESSAGE:
                return processInvalidMsg(receivedJsonObj);

            case JsonMessage.ACTIVITY_MESSAGE:
                return processActivityMsg(con, receivedJsonObj);

            case JsonMessage.ACTIVITY_BROADCAST:
                return processActivityBroadcastMsg(con, receivedJsonObj);

            case JsonMessage.REGISTER:
                return processRegisterMsg(con, receivedJsonObj);

            case JsonMessage.SERVER_ANNOUNCE:
                return processServerAnnounceMsg(con, receivedJsonObj);
            case JsonMessage.LOCK_REQUEST:
                return processServerLockRequestMsg(con, receivedJsonObj);
            case JsonMessage.LOCK_ALLOWED:
                return processServerLockAllowedMsg(con, receivedJsonObj);
            case JsonMessage.LOCK_DENIED:
                return processServerLockDeniedMsg(con, receivedJsonObj);

            default:
                return processInvalidCommand(con, receivedJsonObj);
        }
    }


    /*
     * Called once every few seconds to synchronize servers' information
     * @return true if server should shut down, false otherwise
     *
     */
    @Override
    public boolean doActivity() {
        // Broadcast server announce
        ServerAnnounceMsg serverAnnounceMsg = new ServerAnnounceMsg();
        serverAnnounceMsg.setHostname(Settings.getLocalHostname());
        serverAnnounceMsg.setId(id);
        serverAnnounceMsg.setLoad(clientConnectionList.size());
        serverAnnounceMsg.setPort(Settings.getLocalPort());

        String serverAnnounceJsonStr = serverAnnounceMsg.toJsonString();

        // Broad server announce to adjacent servers
        broadcastToAllOtherServers(serverAnnounceJsonStr);

        log.info("Server announcement sent");

        return false;
    }

    /*
     * Other message processing methods, return true if the connection should be closed,
     * false otherwise.
     */
    private boolean processLogoutMsg(Connection con, JsonObject receivedJsonObj) {
        log.info("user logout");

        clientConnectionList.remove(con);

        return true;
    }

    private boolean processAuthFailedMsg(Connection con, JsonObject receivedJsonObj) {
        log.info("Authentication failed");

        serverConnectionList.remove(con);

        return true;
    }

    // process invalid message?????
    // process invalid message?????
    // process invalid message?????
    // process invalid message?????
    // process invalid message?????
    private boolean processInvalidMsg(JsonObject receivedJsonObj) {
        String errorInfo = receivedJsonObj.get("info").getAsString();

        log.info(errorInfo);

        if (errorInfo.equals(JsonMessage.UNAUTHENTICATED_SERVER) ||
                errorInfo.equals(JsonMessage.REPEATED_AUTHENTICATION)) {
            return true;
        }

        return false;
    }

    private boolean processLoginMsg(Connection con, JsonObject receivedJsonObj) {
        // Validate login message format
        if (!isUserInfoMsgValid(con, receivedJsonObj)) {
            return true;
        }

        String secret = receivedJsonObj.get("secret").getAsString();
        String username = receivedJsonObj.get("username").getAsString();

        // Secret or username is not correct, login failed
        if (!username.equals(JsonMessage.ANONYMOUS_USERNAME) && !hasClientInfo(username, secret)) {
            log.info("attempt to login with a wrong secret");

            LoginFailedMsg loginFailedMsg = new LoginFailedMsg();
            loginFailedMsg.setInfo("user attempt to login with a wrong secret");

            String registFailedJsonStr = loginFailedMsg.toJsonString();
            con.writeMsg(registFailedJsonStr);

            return true;
        }

        if (clientConnectionList.size() < CLIENT_CONNECTION_UPPER_LIMIT) {
            log.info("logged in as user " + username);

            LoginSuccMsg loginSuccMsg = new LoginSuccMsg();
            loginSuccMsg.setInfo("Login successful");

            String loginSuccJsonStr = loginSuccMsg.toJsonString();
            con.writeMsg(loginSuccJsonStr);
        }
        // All servers are too busy
        else {
            log.info("server is too busy");
            log.info("Redirected");

            LoginFailedMsg loginFailedMsg = new LoginFailedMsg();
            loginFailedMsg.setInfo("server is too busy");

            //iterate serverinfo and find the lowest connection number
            RedirectMsg redirectMsg = new RedirectMsg();
            redirectMsg.setHost(serverInfoList.get(0).getRemoteHostname());
            redirectMsg.setPort(serverInfoList.get(0).getRemotePort());
            redirectMsg.setId(serverInfoList.get(0).getId());

            String redirectMsgJsonStr = redirectMsg.toJsonString();
            con.writeMsg(redirectMsgJsonStr);

            return false;
        }
        return true;
    }

    private boolean processRegisterMsg(Connection con, JsonObject receivedJsonObj) {
        // Validate register message format
        if (!isUserInfoMsgValid(con, receivedJsonObj)) {
            return true;
        }

        String secret = receivedJsonObj.get("secret").getAsString();
        String username = receivedJsonObj.get("username").getAsString();

        // Check whether username already exists, and username cannot be 'anonymous'
        if (clientInfoList.containsKey(username) || username.equals(JsonMessage.ANONYMOUS_USERNAME)) {
            log.info("Register failed. Username already exists!");

            RegisterFailedMsg registerFailedMsg = new RegisterFailedMsg();
            registerFailedMsg.setInfo(username + " is already registered in the system");

            String registFailedJsonStr = registerFailedMsg.toJsonString();
            con.writeMsg(registFailedJsonStr);

            return true;
        }
        // Register success
        else {
            // First we need to have a check there is only one server or multiple servers, if multiservers exists
            // the server will broadcast the lock_request function
            if (serverConnectionList.size() > 0) {
                // If the User from client have register success, this server will forward a message to other servers
                LockRequestMsg lockrequestMsg = new LockRequestMsg();
                lockrequestMsg.setUsername(username);
                lockrequestMsg.setSecret(secret);
                // In this case, I insert a new variable into the lockrequest, which is original server.
                lockrequestMsg.setOriginalServer(con.getSocket().getInetAddress().getHostAddress() + ":" + con.getSocket().getPort());
                String lockrequestJsonStr = lockrequestMsg.toJsonString();
                forwardToOtherServers(con, lockrequestJsonStr);
            } else {
                // If only in one server
                log.info("Register_Success");

                // Send register success message
                RegistSuccMsg registerSuccMsg = new RegistSuccMsg();
                registerSuccMsg.setInfo("register success for " + username);

                String registSuccJsonStr = registerSuccMsg.toJsonString();
                con.writeMsg(registSuccJsonStr);

                // Add client info
                clientInfoList.put(username, secret);
            }


            return true;
        }
    }

    /**
     * Process server lock request Message
     *
     * @param con
     * @param receivedJsonObj
     * @return
     */
    private boolean processServerLockRequestMsg(Connection con, JsonObject receivedJsonObj) {
        log.info("Lock Request received");

        String secret = receivedJsonObj.get("secret").getAsString();
        String username = receivedJsonObj.get("username").getAsString();
        String originalServer = receivedJsonObj.get("originalServer").getAsString();
        // If the username not contain in the list

        if (!clientInfoList.containsKey(username)) {
            LockDeniedMsg lockDeniedMsg = new LockDeniedMsg();
            lockDeniedMsg.setSecret(secret);
            lockDeniedMsg.setUsername(username);
            lockDeniedMsg.setOriginalServer(originalServer);
            String lockdeniedJsonStr = lockDeniedMsg.toJsonString();
            forwardBackToOriginalServer(con, lockdeniedJsonStr, originalServer);

        } else {
            LockAllowedMsg lockAllowedMsg = new LockAllowedMsg();
            lockAllowedMsg.setSecret(secret);
            lockAllowedMsg.setUsername(username);

            String lockallowJsonStr = lockAllowedMsg.toJsonString();
            forwardBackToOriginalServer(con, lockallowJsonStr, originalServer);
        }

        return false;
    }

    /**
     * Push the lock message back to Original Server
     *
     * @param con
     * @param lockedMsgJsonStr
     * @param originalServer
     * @return
     */
    private boolean forwardBackToOriginalServer(Connection con, String lockedMsgJsonStr, String originalServer) {
        String host = originalServer.substring(0, originalServer.indexOf(':'));
        int port = Integer.parseInt(originalServer.substring(originalServer.indexOf(':') + 1));
        try {
            Socket s = new Socket(host, port);
            Connection conoriginal = new Connection(s);
            conoriginal.writeMsg(lockedMsgJsonStr);
        } catch (IOException e) {
            e.printStackTrace();
            log.info("Cannot connect to original Server");
        }

        return true;
    }

    /**
     * Process Server will received the locked Allowed process
     *
     * @param con
     * @param receivedJsonObj
     * @return
     */
    private boolean processServerLockAllowedMsg(Connection con, JsonObject receivedJsonObj) {

        String secret = receivedJsonObj.get("secret").getAsString();
        String username = receivedJsonObj.get("username").getAsString();

        log.info("Register_Success");

        // Send register success message
        RegistSuccMsg registerSuccMsg = new RegistSuccMsg();
        registerSuccMsg.setInfo("register success for " + username);

        String registSuccJsonStr = registerSuccMsg.toJsonString();
        con.writeMsg(registSuccJsonStr);

        // Add client info
        clientInfoList.put(username, secret);

        return false;
    }


    /**
     * Process Server will received the locked Allowed process
     *
     * @param con
     * @param receivedJsonObj
     * @return boolean
     */
    private boolean processServerLockDeniedMsg(Connection con, JsonObject receivedJsonObj) {

        String username = receivedJsonObj.get("username").getAsString();

        RegisterFailedMsg registerFailedMsg = new RegisterFailedMsg();
        registerFailedMsg.setInfo(username + " is already registered in other distributed Servers");

        String registFailedJsonStr = registerFailedMsg.toJsonString();
        con.writeMsg(registFailedJsonStr);

        return true;
    }

    /**
     * Processing Server Announce Message
     *
     * @param con
     * @param receivedJsonObj
     * @return boolean
     */
    private boolean processServerAnnounceMsg(Connection con, JsonObject receivedJsonObj) {
        log.info("Server announce received");

        if (!serverConnectionList.contains(con)) {
            // Send invalid message
            InvalidMsg invalidMsg = new InvalidMsg();
            invalidMsg.setInfo(JsonMessage.UNAUTHENTICATED_SERVER);
            con.writeMsg(invalidMsg.toJsonString());

            return true;
        }

        String id = receivedJsonObj.get("id").getAsString();
        ServerSettings serverInfo = findServer(id);

        // This is a new server
        if (serverInfo == null) {
            serverInfo = new ServerSettings();
            serverInfo.setId(id);
            serverInfo.setServerLoad(receivedJsonObj.get("load").getAsInt());
            serverInfo.setRemoteHostname(receivedJsonObj.get("hostname").getAsString());
            serverInfo.setRemotePort(receivedJsonObj.get("port").getAsInt());
            serverInfoList.add(serverInfo);
        }
        // This is a known server, update server load info
        else {
            serverInfo.setServerLoad(receivedJsonObj.get("load").getAsInt());
        }
        return false;
    }

    private boolean processActivityBroadcastMsg(Connection con, JsonObject receivedJsonObj) {
        log.debug("Activity broadcast message received from port: " + con.getSocket().getPort());

        // Validate activity message
        if (!isActivityMsgValid(con, receivedJsonObj)) {
            return true;
        }

        String jsonStr = new Gson().toJson(receivedJsonObj);

        broadcastToAllClients(jsonStr);
        forwardToOtherServers(con, jsonStr);

        return false;
    }

    private boolean processActivityMsg(Connection con, JsonObject receivedJsonObj) {
        log.info("Activity message received from port: " + con.getSocket().getPort());

        // Validate activity message
        if (!isActivityMsgValid(con, receivedJsonObj)) {
            return true;
        }

        // Check username and secret!!
        String username = receivedJsonObj.get("username").getAsString();
        String secret = receivedJsonObj.get("secret").getAsString();

        if (!username.equals(JsonMessage.ANONYMOUS_USERNAME) && !hasClientInfo(username, secret)) {
            // Send login failed info
            log.info("Client auth failed");

            InvalidMsg invalidMsg = new InvalidMsg();
            invalidMsg.setInfo("Client auth failed");

            con.writeMsg(invalidMsg.toJsonString());

            return true;
        }

        log.debug("Broadcast activity message received from client");

        // Convert it to activity broadcast message
        JsonObject jsonObj = receivedJsonObj.get("activity").getAsJsonObject();
        String content = jsonObj.getAsString();

        ActBroadMsg actBroadMsg = new ActBroadMsg();
        actBroadMsg.setActor(username);
        actBroadMsg.setObject(content);

        String activityJsonStr = actBroadMsg.toJsonString();

        broadcastToAllClients(activityJsonStr);
        broadcastToAllOtherServers(activityJsonStr);

        return false;
    }

    private boolean processAuthMsg(Connection con, JsonObject receivedJsonObj) {
        // This server has too many children
        if (serverConnectionList.size() >= SERVER_CONNECTION_UPPER_LIMIT) {
            log.info("Auth failed: too many servers connecting to this server");

            AuthFailMsg authFailedMsg = new AuthFailMsg();
            authFailedMsg.setInfo("Auth failed: too many servers connecting to this server");

            String authFailedJsonStr = authFailedMsg.toJsonString();
            con.writeMsg(authFailedJsonStr);

            return true;
        }
        // Json message format incorrect
        else if (!receivedJsonObj.has("secret")) {
            log.info("Auth failed: the supplied secret is incorrect");

            AuthFailMsg authFailedMsg = new AuthFailMsg();
            authFailedMsg.setInfo("the supplied secret is incorrect");

            String authFailedJsonStr = authFailedMsg.toJsonString();
            con.writeMsg(authFailedJsonStr);

            return true;
        }

        String secret = receivedJsonObj.get("secret").getAsString();

        if (!secret.equals(Settings.getSecret())) {
            log.info("Auth failed");

            AuthFailMsg authFailedMsg = new AuthFailMsg();
            authFailedMsg.setInfo("the supplied secret is incorrect: " + secret);

            String authFailedJsonStr = authFailedMsg.toJsonString();
            con.writeMsg(authFailedJsonStr);

            return true;
        }
        // Server already authenticated, send invalid message
        else if (isServerAuthenticated(con)) {
            return true;
        }
        // Connect with server
        else {
            log.info("Auth succeeded");

            serverConnectionList.add(con);

            return false;
        }
    }

    private void broadcastToAllOtherServers(String jsonStr) {
        for (Connection con : serverConnectionList) {
            con.writeMsg(jsonStr);
        }
    }

    private void forwardToOtherServers(Connection current, String jsonStr) {
        for (Connection con : serverConnectionList) {
            if (current.getSocket().getPort() != con.getSocket().getPort()) {
                con.writeMsg(jsonStr);
            }
        }
    }

    private void broadcastToAllClients(String jsonStr) {
        for (Connection con : clientConnectionList) {
            con.writeMsg(jsonStr);
        }
    }

//	public boolean connectToServer() {
//		return initiateConnection(Settings.getLoadBalancerPort(), Settings.getLoadBalancerHostname());
//	}

    private boolean containCommandField(Connection con, JsonObject receivedJsonObj) {
        if (!receivedJsonObj.has("command")) {
            InvalidMsg invalidMsg = new InvalidMsg();
            invalidMsg.setInfo("Message must contain field command");
            con.writeMsg(invalidMsg.toJsonString());

            return false;
        }
        return true;
    }

    private boolean isUserInfoMsgValid(Connection con, JsonObject receivedJsonObj) {
        InvalidMsg invalidMsg = new InvalidMsg();

        if (!receivedJsonObj.has("username")) {
            invalidMsg.setInfo("Message must contain field username");
            con.writeMsg(invalidMsg.toJsonString());

            return false;
        } else if (!receivedJsonObj.has("secret")) {
            invalidMsg.setInfo("Message must contain field secret");
            con.writeMsg(invalidMsg.toJsonString());

            return false;
        } else {
            return true;
        }
    }

    private boolean isActivityMsgValid(Connection con, JsonObject receivedJsonObj) {
        if (!receivedJsonObj.has("activity")) {
            InvalidMsg invalidMsg = new InvalidMsg();
            invalidMsg.setInfo("Message must contain field activity");
            con.writeMsg(invalidMsg.toJsonString());

            return false;
        }

        return true;
    }

    private boolean processInvalidCommand(Connection con, JsonObject receivedJsonObj) {
        String command = receivedJsonObj.get("command").getAsString();

        InvalidMsg invalidMsg = new InvalidMsg();
        invalidMsg.setInfo("Invalid command: " + command);
        con.writeMsg(invalidMsg.toJsonString());

        return true;
    }

    /**
     * A method that the server send back the invalid command which is not the JsonObject
     *
     * @param con
     * @param invalidJsonObj
     * @return
     */
    private boolean processInvalidString(Connection con, String invalidJsonObj) {
        InvalidMsg invalidMsg = new InvalidMsg();
        invalidMsg.setInfo("Invalid Message: " + invalidJsonObj);
        con.writeMsg(invalidMsg.toJsonString());
        return true;
    }


    private boolean isServerAuthenticated(Connection con) {
        for (Connection connection : serverConnectionList) {
            if (con.getSocket().getPort() == connection.getSocket().getPort()) {
                InvalidMsg invalidMsg = new InvalidMsg();
                invalidMsg.setInfo(JsonMessage.REPEATED_AUTHENTICATION);
                con.writeMsg(invalidMsg.toJsonString());

                return true;
            }
        }

        return false;
    }

    private ServerSettings findServer(String id) {
        for (ServerSettings serverInfo : serverInfoList) {
            if (serverInfo.getId().equals(id)) {
                return serverInfo;
            }
        }

        return null;
    }

    private boolean hasClientInfo(String username, String secret) {
        return clientInfoList.containsKey(username) && clientInfoList.get(username).equals(secret);
    }
}
