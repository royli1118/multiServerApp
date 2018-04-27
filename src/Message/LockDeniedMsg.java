package Message;

public class LockDeniedMsg extends JsonMessage{

    private String username = "";
    private String secret = "";
    private String originalServer = "";

    public LockDeniedMsg() {
        setCommand(JsonMessage.LOCK_DENIED);
    }

    public void setUsername(String n) {
        username = n;
    }

    public void setSecret(String s) {
        secret = s;
    }

    public void setOriginalServer(String o){originalServer = o;}
}
