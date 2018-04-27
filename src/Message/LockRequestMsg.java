package Message;

public class LockRequestMsg extends JsonMessage{
    private String username = "";
    private String secret = "";
    private String originalServer = "";

    public LockRequestMsg() {
        setCommand(JsonMessage.LOCK_REQUEST);
    }

    public void setUsername(String n) {
        username = n;
    }

    public void setSecret(String s) {
        secret = s;
    }

    public void setOriginalServer(String o){originalServer = o;}
}
