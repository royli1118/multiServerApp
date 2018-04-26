package activitystreamer.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;


@SuppressWarnings("serial")
public class TextFrame extends JFrame implements ActionListener {

    private static final Logger log = LogManager.getLogger();
    private JTextArea inputText;
    private JTextArea outputText;
    private JButton sendButton;
    private JButton disconnectButton;
    private ClientControl clientThread;

    public TextFrame() {
        setTitle("ActivityStreamer Text I/O");
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayout(1, 2));
        JPanel inputPanel = new JPanel();
        JPanel outputPanel = new JPanel();
        inputPanel.setLayout(new BorderLayout());
        outputPanel.setLayout(new BorderLayout());
        Border lineBorder = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.lightGray),
                "Activity message");
        inputPanel.setBorder(lineBorder);
        lineBorder = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.lightGray),
                "JSON output, received from server");
        outputPanel.setBorder(lineBorder);
        outputPanel.setName("Text output");

        inputText = new JTextArea();
        JScrollPane scrollPane = new JScrollPane(inputText);
        inputPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonGroup = new JPanel();
        sendButton = new JButton("Send");
        disconnectButton = new JButton("Disconnect");
        buttonGroup.add(sendButton);
        buttonGroup.add(disconnectButton);
        inputPanel.add(buttonGroup, BorderLayout.SOUTH);
        sendButton.addActionListener(this);
        disconnectButton.addActionListener(this);

        outputText = new JTextArea();
        scrollPane = new JScrollPane(outputText);
        outputPanel.add(scrollPane, BorderLayout.CENTER);

        mainPanel.add(inputPanel);
        mainPanel.add(outputPanel);
        add(mainPanel);

        setLocationRelativeTo(null);
        setSize(1280, 768);
        setVisible(true);

        clientThread = ClientControl.getInstance();

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                ClientControl.getInstance().disconnect();

                e.getWindow().dispose();
            }
        });
    }

    public void sendOutPutText(final JsonObject obj) {
        String newText = new Gson().toJson(obj);
        String oldText = outputText.getText();

        outputText.setText(oldText + "\n\n" + newText);
        outputText.revalidate();
        outputText.repaint();
    }

    public void showErrorMsg(String error) {
        JOptionPane.showMessageDialog(null, error, "Error", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == sendButton) {
            String msg = inputText.getText().trim().replaceAll("\r", "").replaceAll("\n", "").replaceAll("\t", "");
            if (!msg.isEmpty()) {
                if (clientThread.connect()) {
                    clientThread.sendTOServerJsonObject(msg);
                }

            } else {
                showErrorMsg("Message cannot be empty");
                return;
            }


        } else if (e.getSource() == disconnectButton) {
            ClientControl.getInstance().disconnect();
        }
    }
}
