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
import java.util.ArrayList;
import java.util.HashMap;


public class ServerControl extends Control {
    private static final Logger log = LogManager.getLogger();
    private static final int SERVER_CONNECTION_UPPER_LIMIT = 5;
    private static final int CLIENT_CONNECTION_UPPER_LIMIT = 4;
    private ArrayList<Connection> serverConnectionList = new ArrayList<>();
    private ArrayList<Connection> clientConnectionList = new ArrayList<>();
    private ArrayList<ServerSettings> serverInfoList = new ArrayList<>();
    private HashMap<String, String> clientInfoList = new HashMap<>();

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
     */
    @Override
    public ServerConnection incomingConnection(Socket s) throws IOException {
        log.debug("incomming connection: " + Settings.socketAddress(s));

        ServerConnection con = new ServerConnection(s);

        return con;
    }

    /*
     * a new outgoing connection
     */
//	public void outgoingConnection(Socket s, boolean toLoadBalancer) throws IOException
//	{
//		ServerConnection con = new ServerConnection(s);
//
//		// Send authentication message
//		AuthMsg authJson = new AuthMsg();
//		authJson.setSecret(Settings.getSecret());
//
//		String authJsonStr = authJson.toJsonString();
//		con.writeMsg(authJsonStr);
//
//		if (toLoadBalancer)
//		{
//			// Send server announce to load balancer immediately
//			doActivity();
//		}
//		else
//		{
//			serverConnectionList.add(con);
//		}
//	}

    /*
     * the connection has been closed
     */
    @Override
    public void connectionClosed(Connection con) {
        super.connectionClosed(con);

        if (!term && !serverConnectionList.remove(con)) {
            clientConnectionList.remove(con);
        }
    }


    //	public boolean initiateConnection(int port, String host)
//	{
//		// make a connection to another server if remote hostname is supplied
//		if (host != null)
//		{
//			try
//			{
//				outgoingConnection(new Socket(host, port));
//
//				return true;
//			}
//			catch (UnknownHostException e)
//			{
//				log.info("Server establish connection failed. Unknown Host: " + e.getMessage());
//
//				System.exit(-1);
//			}
//			catch (IOException e)
//			{
//				log.error("Server failed to make plain connection to " + host + ":"
//						+ port + " :" + e);
//
//				return false;
//			}
//		}
//
//		return false;
//	}


    /*
     * process incoming msg, from connection con return true if the connection
     * should be closed, false otherwise
     */
    @Override
    public synchronized boolean process(Connection con, String msg) {
        log.debug("Server Receieved: " + msg);

        JsonObject receivedJsonObj;

        try {
            receivedJsonObj = new Gson().fromJson(msg, JsonObject.class);
        } catch (JsonSyntaxException e) {
            log.debug("Server receiving msg failed. Not json format: " + e.getMessage());

            return true;
        }

        if (!containCommandField(con, receivedJsonObj)) {
            return true;
        }

        String msgType = receivedJsonObj.get("command").getAsString();

        switch (msgType) {
            case JsonMessage.LOGIN:
                return processLoginMsg(con, receivedJsonObj);

            case JsonMessage.CLIENT_AUTHENTICATE:
                return processClientAuthMsg(con, receivedJsonObj);

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

            default:
                return processInvalidCommand(con, receivedJsonObj);
        }
    }

    /*
     * Called once every few seconds Return true if server should shut down,
     * false otherwise
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
     * Other methods as needed
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

            log.info("Redirected");

//			RedirectMsg redirectMsg = new RedirectMsg();
//			redirectMsg.setHost(serverInfo.getRemoteHostname());
//			redirectMsg.setPort(serverInfo.getRemotePort());
//			redirectMsg.setId(serverInfo.getId());
//
//			String redirectMsgJsonStr = redirectMsg.toJsonString();
//			con.writeMsg(redirectMsgJsonStr);
        }
        // All servers are too busy
        else {
            log.info("server is too busy");

            LoginFailedMsg loginFailedMsg = new LoginFailedMsg();
            loginFailedMsg.setInfo("server is too busy");

            String registFailedJsonStr = loginFailedMsg.toJsonString();
            con.writeMsg(registFailedJsonStr);
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
            log.info("Register_Success");

            // Send register success message
            RegistSuccMsg registerSuccMsg = new RegistSuccMsg();
            registerSuccMsg.setInfo("register success for " + username);

            String registSuccJsonStr = registerSuccMsg.toJsonString();
            con.writeMsg(registSuccJsonStr);

            // Add client info
            clientInfoList.put(username, secret);

            return true;
        }
    }


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
        String clientSentId = receivedJsonObj.get("id").getAsString();

        if (!clientSentId.equals(id)) {
            // Send login failed info
            log.info("server is too busy");

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

        broadcastToAllClients(activityJsonStr);
        broadcastToAllOtherServers(activityJsonStr);

        return false;
    }

    private boolean processClientAuthMsg(Connection con, JsonObject receivedJsonObj) {
        // Validate login message format
        if (!isUserInfoMsgValid(con, receivedJsonObj)) {
            return true;
        }

        String username = receivedJsonObj.get("username").getAsString();
        String clientSentId = receivedJsonObj.get("id").getAsString();

        if (!clientSentId.equals(id)) { // 需要删掉
            // Send login failed info
            log.info("server is too busy");

            InvalidMsg invalidMsg = new InvalidMsg();
            invalidMsg.setInfo("Client auth faile");

            con.writeMsg(invalidMsg.toJsonString());

            return true;
        }

        log.info("Connected with broadcaster in as user " + username);

        LoginSuccMsg loginSuccMsg = new LoginSuccMsg();
        loginSuccMsg.setInfo("Connected with broadcaster successful");

        String loginSuccJsonStr = loginSuccMsg.toJsonString();
        con.writeMsg(loginSuccJsonStr);

        clientConnectionList.add(con);

        return false;
    }

    private boolean processAuthMsg(Connection con, JsonObject receivedJsonObj) {
        // This server has too many children
        if (serverConnectionList.size() >= SERVER_CONNECTION_UPPER_LIMIT) {
            log.info("Auth faield: too many servers connecting to this server");

            AuthFailMsg authFailedMsg = new AuthFailMsg();
            authFailedMsg.setInfo("Auth faield: too many servers connecting to this server");

            String authFailedJsonStr = authFailedMsg.toJsonString();
            con.writeMsg(authFailedJsonStr);

            return true;
        }
        // Json message format incorrect
        else if (!receivedJsonObj.has("secret")) {
            log.info("Auth faield: the supplied secret is incorrect");

            AuthFailMsg authFailedMsg = new AuthFailMsg();
            authFailedMsg.setInfo("the supplied secret is incorrect");

            String authFailedJsonStr = authFailedMsg.toJsonString();
            con.writeMsg(authFailedJsonStr);

            return true;
        }

        String secret = receivedJsonObj.get("secret").getAsString();

        if (!secret.equals(Settings.getSecret())) {
            log.info("Auth faield");

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

//	public boolean connectToServer()
//	{
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

    private ServerSettings loadBalance() {
        int minLoad = CLIENT_CONNECTION_UPPER_LIMIT;
        ServerSettings clusterWithLowestLoad = null;

        for (ServerSettings cluster : serverInfoList) {
            if (cluster.getServerLoad() < minLoad) {
                minLoad = cluster.getServerLoad();
                clusterWithLowestLoad = cluster;
            }
        }

        return clusterWithLowestLoad;
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
