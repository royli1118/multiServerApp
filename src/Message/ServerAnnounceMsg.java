package Message;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;

public class ServerAnnounceMsg extends JsonMessage {
    private String id = "";
    private String hostname = "";
    private int load = 0;
    private int port = 0;
    private HashMap<String,String> userList;
    private HashMap<String,String> allJSONMessage;

    public ServerAnnounceMsg() {
        setCommand(JsonMessage.SERVER_ANNOUNCE);
        userList = new HashMap<>();
        allJSONMessage = new HashMap<>();
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setHostname(String host) {
        this.hostname = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setLoad(int load) {
        this.load = load;
    }

    public void setUserList(HashMap<String, String> userList) {
        this.userList = userList;
    }

    public void setAllJSONMessage(HashMap<String, String> allJSONMessage) {
        this.allJSONMessage = allJSONMessage;
    }
}
