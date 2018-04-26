package activitystreamer.client;

import activitystreamer.util.Settings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;

/**
 * The login frame for Client User implemented by JAVA GUI
 *
 * Added by Huanan Li
 */
public class LoginFrame implements ActionListener {
    private JFrame frame;
    private JButton loginButton;
    private JButton registerButton;
    private JTextField userText;
    private JTextField hostnameText;
    private JTextField hostportText;
    private JPasswordField passwordText;
    private JButton anonymousButton;
    private ClientControl clientThread;
    public LoginFrame() {
        frame = new JFrame("A User login Frame");
        frame.setSize(500, 200);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        JPanel panel = new JPanel();
        frame.add(panel);
        panel.setLayout(null);


        JLabel remoteHostLabel = new JLabel("Remote Host");
        remoteHostLabel.setBounds(10, 10, 100, 25);
        panel.add(remoteHostLabel);

        hostnameText = new JTextField(20);
        hostnameText.setText(Settings.getRemoteHostname());
        hostnameText.setBounds(100, 10, 160, 25);
        panel.add(hostnameText);

        JLabel portLabel = new JLabel("Port Number");
        portLabel.setBounds(10, 40, 80, 25);
        panel.add(portLabel);

        hostportText = new JTextField(20);
        hostportText.setText("" + Settings.getRemotePort());
        hostportText.setBounds(100, 40, 160, 25);
        panel.add(hostportText);

        JLabel userLabel = new JLabel("UserName");
        userLabel.setBounds(10, 70, 80, 25);
        panel.add(userLabel);

        userText = new JTextField(20);
        userText.setText(Settings.getUsername());
        userText.setBounds(100, 70, 160, 25);
        panel.add(userText);

        JLabel passwordLabel = new JLabel("Secret");
        passwordLabel.setBounds(10, 100, 80, 25);
        panel.add(passwordLabel);

        passwordText = new JPasswordField(20);
        passwordText.setText(Settings.getSecret());
        passwordText.setBounds(100, 100, 160, 25);
        panel.add(passwordText);

        loginButton = new JButton("Login");
        loginButton.setBounds(10, 130, 110, 25);
        panel.add(loginButton);
        loginButton.addActionListener(this);

        registerButton = new JButton("Register");
        registerButton.setBounds(140, 130, 110, 25);
        panel.add(registerButton);
        registerButton.addActionListener(this);

        anonymousButton = new JButton("Login by anonymous");
        anonymousButton.setBounds(270, 130, 160, 25);
        panel.add(anonymousButton);
        anonymousButton.addActionListener(this);
        frame.setVisible(true);

        clientThread = ClientControl.getInstance();
    }

    public void showInfoBox(String error) {
        JOptionPane.showMessageDialog(null, error, "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    public void hide() {
        frame.setVisible(false);
    }

    public void close() {
        frame.dispose();
    }

    /**
     * The collection of Action performed
     *
     */
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == loginButton) {
            if (passwordText.getPassword().length == 0 ||
                    userText.getText().isEmpty() ||
                    hostportText.getText().isEmpty() ||
                    hostnameText.getText().isEmpty()) {
                showInfoBox("All fields must not be empty");

                return;
            }

            Settings.setSecret(new String(passwordText.getPassword()));
            Settings.setUsername(userText.getText());
            Settings.setRemotePort(Integer.parseInt(hostportText.getText()));
            Settings.setRemoteHostname(hostnameText.getText());

            if (clientThread.connect()) {
                clientThread.sendLoginMsg();
            } else {
                showInfoBox("Client connect failed");
            }
        } else if (e.getSource() == registerButton) {
            if (passwordText.getPassword().length == 0 ||
                    userText.getText().isEmpty() ||
                    hostportText.getText().isEmpty() ||
                    hostnameText.getText().isEmpty()) {
                showInfoBox("All fields must not be empty");

                return;
            }

            Settings.setSecret(new String(passwordText.getPassword()));
            Settings.setUsername(userText.getText());
            Settings.setRemotePort(Integer.parseInt(hostportText.getText()));
            Settings.setRemoteHostname(hostnameText.getText());

            if (clientThread.connect()) {
                clientThread.sendRegisterMsg();
            } else {
                showInfoBox("Client connect failed");
            }
        } else if (e.getSource() == anonymousButton) {
            if (!hostnameText.getText().equals(null) && !hostportText.getText().equals(null)) {
                Settings.setRemotePort(Integer.parseInt(hostportText.getText()));
                Settings.setRemoteHostname(hostnameText.getText());
            } else {
                Settings.setRemotePort(3780);
                Settings.setRemoteHostname("127.0.0.1");
            }
            if (clientThread.connect()) {
                clientThread.sendAnonymusLoginMsg();
            } else {
                showInfoBox("Client connect failed");
            }
        }
    }
}
