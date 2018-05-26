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
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * This class deals with main logic of servers' behavior. It is responsible for processing clients'
 * request to login, logout, register, broadcast message and so forth. It also handles servers'
 * request to authenticate, redirect, server announce and so forth.
 *
 * @author Zelei Cui and Huanan Li
 */

public class ServerControl extends Control {
    private static final Logger log = LogManager.getLogger();
    private static final int SERVER_CONNECTION_UPPER_LIMIT = 50;
    private static final int CLIENT_CONNECTION_UPPER_LIMIT = 3;
    // a record for how many servers will connect to this server
    private ArrayList<Connection> serverConnectionList = new ArrayList<>();
    // a record for how many clients will connect to this server
    private ArrayList<Connection> clientConnectionList = new ArrayList<>();

    // The entire JSON message have stored
    private HashMap<String, String> allActivityMessage = new HashMap<>();

    // a record for server info which have connect to this server
    private ArrayList<ServerSettings> serverInfoList = new ArrayList<>();

    // a record for client info which have connect to this server
    private HashMap<String, String> userInfoList = new HashMap<>();
    // authenticate id between servers
    private String id = "groupdurian";

    private ServerControl() {
        super();

        // start a listener
        listener = ServerListener.getInstance();

        /*
         * This part means if the server will not remote to any server
         * and this is the host server
         */
        if (Settings.getRemoteHostname() == null) {
            Settings.setSecret(id);
            log.info(id);
            start();
        } else {
            id = Settings.getSecret();
            if (id == null) {
                log.debug("The slave Server do not provide the secret to connect other server");
                System.exit(-1);
            } else {
                connectToServer();
                start();
            }
        }
        // start the server's activity loop
        // it will call doActivity every few seconds

    }

    // since control and its subclasses are singleton, we get the singleton this
    // way
    public static synchronized ServerControl getInstance() {
        if (control == null) {
            control = new ServerControl();
        }
        return (ServerControl) control;
    }

    /**
     * a new incoming connection
     *
     * @param s A Socket to establish a connection
     * @return ServerConnection A ServerConnection object which requires connection
     */
    @Override
    public ServerConnection incomingConnection(Socket s) throws IOException {
        log.debug("incomming connection: " + Settings.socketAddress(s));

        ServerConnection con = new ServerConnection(s);

        return con;
    }

    /**
     * a new outgoing connection
     *
     * @param s A Socket to establish a connection
     * @return ServerConnection A ServerConnection object which requires connection
     */
    @Override
    public ServerConnection outgoingConnection(Socket s) throws IOException {
        ServerConnection con = new ServerConnection(s);
        // Send authentication message
        AuthMsg authJson = new AuthMsg();
        // This step the connect server must have the same secret provided by the host server
        // Otherwise this part will not work
        authJson.setSecret(Settings.getSecret());

        String authJsonStr = authJson.toJsonString();
        con.writeMsg(authJsonStr);

        serverConnectionList.add(con);
        return con;
    }

    /**
     * the connection has been closed
     *
     * @param con the current connection
     */
    @Override
    public void connectionClosed(Connection con) {
        super.connectionClosed(con);

        if (!term && !serverConnectionList.remove(con)) {
            clientConnectionList.remove(con);
        }
    }

    /**
     * Initiate a connection to the other Server
     *
     * @param port port number
     * @param host host name
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
     * process incoming Message, either from client or server, from connection con return true if the connection
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

            case JsonMessage.CLIENT_AUTHENTICATE:
                return processClientAuthMsg(con, receivedJsonObj);

            case JsonMessage.AUTHENTICATION_FAIL:
                return processAuthFailedMsg(con, receivedJsonObj);

            case JsonMessage.REQUEST_ALL:
                return processRequestAllMsg(con, receivedJsonObj);

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

            default:
                return processInvalidCommand(con, receivedJsonObj);
        }
    }


    /**
     * Called once every few seconds to synchronize servers' information
     *
     * @return boolean
     */
    @Override
    public boolean doActivity() {
        // Broadcast server announce
        ServerAnnounceMsg serverAnnounceMsg = new ServerAnnounceMsg();
        serverAnnounceMsg.setHostname(Settings.getLocalHostname());
        serverAnnounceMsg.setId(id);
        serverAnnounceMsg.setLoad(clientConnectionList.size());
        serverAnnounceMsg.setPort(Settings.getLocalPort());
        // Activity for synchronizing the UserList and All activity Message.
        // Added for Project 2
        serverAnnounceMsg.setUserList(userInfoList);
        serverAnnounceMsg.setAllJSONMessage(allActivityMessage);

        String serverAnnounceJsonStr = serverAnnounceMsg.toJsonString();

        // Broad server announce to adjacent servers
        broadcastToAllOtherServers(serverAnnounceJsonStr);

        log.info("Server announcement sent");

        return false;
    }

    /**
     * Other message processing methods
     *
     * @param con             the current connection
     * @param receivedJsonObj the Json object to be processed
     * @return true if the connection should be closed, false otherwise.
     */
    private boolean processLogoutMsg(Connection con, JsonObject receivedJsonObj) {
        log.info("user logout");

        clientConnectionList.remove(con);

        return true;
    }


    /**
     * Process the Authentication failed message, connected by server
     *
     * @param con             the current connection
     * @param receivedJsonObj the Json object to be processed
     * @return true if the connection should be closed, false otherwise.
     */
    private boolean processAuthFailedMsg(Connection con, JsonObject receivedJsonObj) {
        log.info("Authentication failed");

        serverConnectionList.remove(con);

        return true;
    }

    /**
     * The Client request all activity message when request, server will send back them
     * @param con
     * @param receivedJsonObj
     * @return
     */
    private boolean processRequestAllMsg(Connection con, JsonObject receivedJsonObj) {
        log.info("Request message from connected Client");
        RequestAllActivityMsg requestAll = new RequestAllActivityMsg();
        requestAll.setAllActivityMessage(allActivityMessage);
        String backRequestMessage = requestAll.toJsonString();
        con.writeMsg(backRequestMessage);
        return true;
    }


    // process invalid message
    private boolean processInvalidMsg(JsonObject receivedJsonObj) {
        String errorInfo = receivedJsonObj.get("info").getAsString();

        log.info(errorInfo);

        return errorInfo.equals(JsonMessage.UNAUTHENTICATED_SERVER) ||
                errorInfo.equals(JsonMessage.REPEATED_AUTHENTICATION);

    }

    // Process Login message
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

            clientConnectionList.add(con);
        }
        // This server is too busy
        else {
            log.info("This server is too busy");
            log.info("Redirected");

            LoginFailedMsg loginFailedMsg = new LoginFailedMsg();
            loginFailedMsg.setInfo("server is too busy");

            //iterate serverinfo and find the lowest connection load
            RedirectMsg redirectMsg = new RedirectMsg();
            // Find the server with the lowest load
            ServerSettings server = minLoadServer();
            redirectMsg.setHost(server.getRemoteHostname());
            redirectMsg.setPort(server.getRemotePort());
            redirectMsg.setId(server.getId());

            String redirectMsgJsonStr = redirectMsg.toJsonString();
            con.writeMsg(redirectMsgJsonStr);

            return false;
        }
        return true;
    }

    // Process Register message
    private boolean processRegisterMsg(Connection con, JsonObject receivedJsonObj) {
        // Validate register message format
        if (!isUserInfoMsgValid(con, receivedJsonObj)) {
            return true;
        }

        String secret = receivedJsonObj.get("secret").getAsString();
        String username = receivedJsonObj.get("username").getAsString();

        // Check whether username already exists, and username cannot be 'anonymous'
        if (userInfoList.containsKey(username) || username.equals(JsonMessage.ANONYMOUS_USERNAME)) {
            log.info("Register failed. Username already exists!");

            RegisterFailedMsg registerFailedMsg = new RegisterFailedMsg();
            registerFailedMsg.setInfo(username + " is already registered in the system");

            String registFailedJsonStr = registerFailedMsg.toJsonString();
            con.writeMsg(registFailedJsonStr);

            return true;
        }
        // Register success
        else {
            // If only in one server
            log.info("Register_Success");

            // Send register success message
            RegistSuccMsg registerSuccMsg = new RegistSuccMsg();
            registerSuccMsg.setInfo("register success for " + username);

            String registSuccJsonStr = registerSuccMsg.toJsonString();
            con.writeMsg(registSuccJsonStr);

            // Add client info
            userInfoList.put(username, secret);
        }
        return false;
    }


    /**
     * Process Server Announce Message
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
        String rp = receivedJsonObj.get("hostname").getAsString();
        int port = receivedJsonObj.get("port").getAsInt();
        ServerSettings serverInfo = findServer(id,rp,port);

        // This is a new server
        if (serverInfo == null) {
            serverInfo = new ServerSettings();
            serverInfo.setId(id);
            serverInfo.setServerLoad(receivedJsonObj.get("load").getAsInt());
            serverInfo.setRemoteHostname(receivedJsonObj.get("hostname").getAsString());
            serverInfo.setRemotePort(receivedJsonObj.get("port").getAsInt());
            synchronizePool(receivedJsonObj);
            serverInfoList.add(serverInfo);
        }
        // This is a known server, update server load info
        else {
            synchronizePool(receivedJsonObj);
            serverInfo.setServerLoad(receivedJsonObj.get("load").getAsInt());
        }
        return false;
    }

    /**
     * Process activity broadcast from other servers
     *
     * @param con
     * @param receivedJsonObj
     * @return boolean
     */
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

    /**
     * Process activity message from client
     *
     * @param con
     * @param receivedJsonObj
     * @return boolean
     */
    private boolean processActivityMsg(Connection con, JsonObject receivedJsonObj) {
        log.info("Activity message received from port: " + con.getSocket().getPort());

        // Validate activity message
        if (!isActivityMsgValid(con, receivedJsonObj)) {
            return true;
        }

        // Check username and secret
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
        JsonObject actJsonObj = receivedJsonObj.get("activity").getAsJsonObject();
        String content = actJsonObj.get("object").getAsString();

        ActBroadMsg actBroadMsg = new ActBroadMsg();
        actBroadMsg.setActor(username);
        actBroadMsg.setObject(content);

        String activityJsonStr = actBroadMsg.toJsonString();

        // Store the Activity message to the hashMap, save its username and activityMessage
        Date date = new Date();

        SimpleDateFormat dateFormatter = new SimpleDateFormat("hh:mm:ss");
        log.info("Activity Message Send at(Time): " + dateFormatter.format(date));
        allActivityMessage.put(username + "," + dateFormatter.format(date), activityJsonStr);

        broadcastToAllClients(activityJsonStr);
        broadcastToAllOtherServers(activityJsonStr);

        return false;
    }

    // Process authenticate message
    private boolean processAuthMsg(Connection con, JsonObject receivedJsonObj) {
        // This server has too many children
        if (serverConnectionList.size() >= SERVER_CONNECTION_UPPER_LIMIT) {
            log.info("Auth failure: too many servers connecting to this server");

            AuthFailMsg authFailedMsg = new AuthFailMsg();
            authFailedMsg.setInfo("Auth failure: too many servers connecting to this server");

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

    /**
     * Process the Client authenticate and give login success
     *
     * @param con
     * @param receivedJsonObj
     * @return
     */

    private boolean processClientAuthMsg(Connection con, JsonObject receivedJsonObj) {
        // Validate login message format
        if (!isUserInfoMsgValid(con, receivedJsonObj)) {
            return true;
        }

        String username = receivedJsonObj.get("username").getAsString();
        // Check if the Server's Userlist have the user
        if (!userInfoList.containsKey(username)) {

            LoginFailedMsg loginFailedMsg = new LoginFailedMsg();
            loginFailedMsg.setInfo("The Server do not have this user");
            String loginFailedJsonStr = loginFailedMsg.toJsonString();
            con.writeMsg(loginFailedJsonStr);

            return true;

        } else {
            log.info("Connected with Server in as user " + username);

            LoginSuccMsg loginSuccMsg = new LoginSuccMsg();
            loginSuccMsg.setInfo("Connected with Server successful");

            String loginSuccJsonStr = loginSuccMsg.toJsonString();
            con.writeMsg(loginSuccJsonStr);

            clientConnectionList.add(con);
            return false;
        }
    }

    /**
     * Broadcast to all servers
     *
     * @param jsonStr
     */
    private void broadcastToAllOtherServers(String jsonStr) {
        for (Connection con : serverConnectionList) {
            con.writeMsg(jsonStr);
        }
    }

    /**
     * Forward to the other servers
     *
     * @param current
     * @param jsonStr
     */
    private String forwardToOtherServers(Connection current, String jsonStr) {
        String result = "";
        for (Connection con : serverConnectionList) {
            if (current.getSocket().getPort() != con.getSocket().getPort()) {
                con.writeMsg(jsonStr);
            }
        }
        return result;
    }

    /**
     * Broadcast to all clients which is adjacent to the server
     *
     * @param jsonStr
     */
    private void broadcastToAllClients(String jsonStr) {
        for (Connection con : clientConnectionList) {
            con.writeMsg(jsonStr);
        }
    }


    /**
     * Initialize the broadcasting connection
     *
     * @return boolean
     */
    public boolean connectToServer() {
        return initiateConnection(Settings.getRemotePort(), Settings.getRemoteHostname());
    }

    /**
     * If the object contains command field
     *
     * @param con
     * @param receivedJsonObj
     * @return boolean
     */
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
     * @return boolean
     */
    private boolean processInvalidString(Connection con, String invalidJsonObj) {
        InvalidMsg invalidMsg = new InvalidMsg();
        invalidMsg.setInfo("Invalid Message: " + invalidJsonObj);
        con.writeMsg(invalidMsg.toJsonString());
        return true;
    }


    /**
     * Check if the server is authenticated
     *
     * @param con
     * @return boolean
     */
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

    private ServerSettings findServer(String id,String rp,int port) {
        for (ServerSettings serverInfo : serverInfoList) {
            if (serverInfo.getId().equals(id)&&serverInfo.getRemoteHostname().equals(rp)&&serverInfo.getRemotePort()==port) {
                return serverInfo;
            }
        }

        return null;
    }

    private boolean hasClientInfo(String username, String secret) {
        return userInfoList.containsKey(username) && userInfoList.get(username).equals(secret);
    }

    /**
     * Find the minLoad Server
     *
     * @return ServerSettings the minimum load server
     */
    private ServerSettings minLoadServer() {
        int minLoad = serverInfoList.get(0).getServerLoad();
        int index = 0;
        for (int i = 1; i < serverInfoList.size(); i++) {
            int load = serverInfoList.get(i).getServerLoad();
            if (load < minLoad) {
                minLoad = load;
                index = i;
            }
        }
        return serverInfoList.get(index);
    }


    private void synchronizePool(JsonObject receivedJsonObj) {

        Gson gson = new Gson();

        // Synchronize the User list. modified for Project 2
        String userResult = receivedJsonObj.get("userList").toString();
        if (!userResult.equals("")) {
            HashMap<String, String> x = gson.fromJson(userResult, HashMap.class);
            Iterator<Map.Entry<String, String>> iterator = x.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, String> pair = (Map.Entry<String, String>) iterator.next();
                if (!userInfoList.containsKey(pair.getKey())) {
                    userInfoList.put(pair.getKey().toString(), pair.getValue().toString());
                }
            }
        }

        // Synchronize the activity message. modified for Project 2
        String activityMessageResult = receivedJsonObj.get("allJSONMessage").toString();
        if (!activityMessageResult.equals("")) {
            HashMap<String, String> x = gson.fromJson(activityMessageResult, HashMap.class);
            Iterator<Map.Entry<String, String>> iterator = x.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, String> pair = (Map.Entry<String, String>) iterator.next();
                if (!allActivityMessage.containsKey(pair.getKey())) {
                    allActivityMessage.put(pair.getKey().toString(), pair.getValue().toString());
                }
            }
        }
    }



}
