package activitystreamer.server;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Settings for Server Command Line options from Skeleton Code
 *
 * @author Dr. Aaron Harwood
 */
public class ServerSettings {
    private String id = null;
    private String remoteHostname = null;
    private int remotePort = 3780;
    private int serverLoad = 0;

    private HashMap<String,String> userList = new HashMap<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRemoteHostname() {
        return remoteHostname;
    }

    public void setRemoteHostname(String remoteHostname) {
        this.remoteHostname = remoteHostname;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }

    public int getServerLoad() {
        return serverLoad;
    }

    public void setServerLoad(int serverLoad) {
        this.serverLoad = serverLoad;
    }

    public HashMap<String, String> getUserList() {
        return userList;
    }

    public void setUserList(HashMap<String, String> userList) {
        this.userList = userList;
    }
}
