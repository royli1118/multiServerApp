package Message;

import java.util.HashMap;

public class RequestAllActivityMsg extends JsonMessage{

    private String username = "";
    private String secret = "";

    private HashMap<String,String> allActivityMessage;

    public RequestAllActivityMsg() {
        setCommand(JsonMessage.REQUEST_ALL);
        allActivityMessage = new HashMap<>();
    }

    public void setUsername(String n) {
        username = n;
    }

    public void setSecret(String s) {
        secret = s;
    }

    public void setAllActivityMessage(HashMap<String, String> allActivityMessage) {
        this.allActivityMessage = allActivityMessage;
    }
}
