package Message;

public class LockAllowedMsg extends JsonMessage{

    private String username = "";
    private String secret = "";
    private String originalServer = "";

    public LockAllowedMsg() {
        setCommand(JsonMessage.LOCK_ALLOWED);
    }

    public void setUsername(String n) {
        username = n;
    }

    public void setSecret(String s) {
        secret = s;
    }

    public void setOriginalServer(String o){originalServer = o;}
}
