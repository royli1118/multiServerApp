package activitystreamer;

import activitystreamer.client.ClientControl;
import activitystreamer.util.Settings;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Client Main Class for running the program
 *
 * @author Dr. Aaron Harwood
 * @editor edited by Huanan Li
 * @version 1.1
 */

public class Client {
    private static final Logger log = LogManager.getLogger();


    private static void help(Options options) {
        String header = "An ActivityStream Client for Unimelb COMP90015\n\n";
        String footer = "\ncontact aharwood@unimelb.edu.au for issues.";
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("ActivityStreamer.Client", header, options, footer, true);
        System.exit(-1);
    }

    public static void main(String[] args) {

        log.info("reading command line options");

        Options options = new Options();
        options.addOption("u", true, "username");
        options.addOption("rp", true, "remote port number");
        options.addOption("rh", true, "remote hostname");
        options.addOption("s", true, "secret for username");
        options.addOption("lp", true,"");

        // build a parser
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e1) {
            help(options);
        }

        if (cmd.hasOption("rh")) {
            Settings.setRemoteHostname(cmd.getOptionValue("rh"));
        }

        if (cmd.hasOption("rp")) {
            try {
                int port = Integer.parseInt(cmd.getOptionValue("rp"));
                Settings.setRemotePort(port);
            } catch (NumberFormatException e) {
                log.error("-rp requires a port number, parsed: " + cmd.getOptionValue("rp"));
                help(options);
            }
        }

        if (cmd.hasOption("s")) {
            Settings.setSecret(cmd.getOptionValue("s"));
        }

        if (cmd.hasOption("u")) {
            Settings.setUsername(cmd.getOptionValue("u"));
        }
        if (cmd.hasOption("lp")){
            Settings.setLocalPort(8080);
        }

        log.info("starting client");

        ClientControl.getInstance();
    }
}
