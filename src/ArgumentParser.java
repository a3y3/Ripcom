/**
 * @author Soham Dongargaonkar [sd4324] on 15/4/19
 */
class ArgumentParser {
    private static final String ROVER_S = "-r";
    private static final String SOURCE_PORT_S = "-s";
    private static final String MULTICAST_PORT_S = "-m";
    private static final String MULTICAST_IP_S = "-i";
    private static final String DESTINATION_IP_S = "-d";
    private static final String UDP_PORT_S = "-u";
    private static final String FILE_NAME_S = "-f";
    private static final String VERBOSE_S = "-v";
    private static final String HELP_S = "-h";

    private static final String ROVER_L = "--rover-id";
    private static final String SOURCE_PORT_L = "--source-port";
    private static final String MULTICAST_PORT_L = "--multicast-port";
    private static final String MULTICAST_IP_L = "--multicast-ip";
    private static final String DESTINATION_IP_L = "--destination-ip";
    private static final String UDP_PORT_L = "--udp-port";
    private static final String FILE_NAME_L = "--file-name";
    private static final String VERBOSE_L = "--verbose";
    private static final String HELP_L = "--help";

    /**
     * Scans args and sets flags appropriately.
     *
     * @param args STDIN arguments.
     */
    void parseArguments(String[] args, Rover rover) throws ArgumentException {
        boolean missingArgument = false;
        boolean missingMulticastIP = true;
        boolean missingRoverID = true;
        boolean missingUDPPort = true;
        boolean missingFileName = true;
        boolean missingDestinationIP = true;
        try {
            for (int i = 0; i < args.length; i++) {
                String argument = args[i];
                if (argument.equals(HELP_S) || argument.equals(HELP_L)) {
                    displayHelp();
                }
                if (argument.equals(VERBOSE_S) || argument.equals(VERBOSE_L)) {
                    rover.verboseOutputs = true;
                }
                if (argument.equals(MULTICAST_PORT_S) || argument.equals(MULTICAST_PORT_L)) {
                    rover.multicastPort = Integer.parseInt(args[i + 1]);
                }
                if (argument.equals(SOURCE_PORT_S) || argument.equals(SOURCE_PORT_L)) {
                    rover.ripPort = Integer.parseInt(args[i + 1]);
                }
                if (argument.equals(MULTICAST_IP_S) || argument.equals(MULTICAST_IP_L)) {
                    rover.multicastIp = args[i + 1];
                    missingMulticastIP = false;
                }
                if (argument.equals(ROVER_S) || argument.equals(ROVER_L)) {
                    rover.roverID = Integer.parseInt(args[i + 1]);
                    missingRoverID = false;
                }
                if (argument.equals(DESTINATION_IP_S) || argument.equals(DESTINATION_IP_L)) {
                    rover.destinationIP = args[i + 1];
                    missingDestinationIP = false;
                }
                if (argument.equals(UDP_PORT_S) || argument.equals(UDP_PORT_L)) {
                    rover.udpPort = Integer.parseInt(args[i + 1]);
                    missingUDPPort = false;
                }
                if (argument.equals(FILE_NAME_S) || argument.equals(FILE_NAME_L)) {
                    rover.fileName = args[i + 1];
                    missingFileName = false;
                }
            }
        } catch (Exception e) {
            throw new ArgumentException("Your arguments are incorrect. Please see " + HELP_L +
                    " for help and usage.");
        }
        if (missingRoverID) {
            System.err.println("Error: Missing Rover ID. Exiting...");
            displayHelp();
        }
        if (rover.multicastPort == 0) {
            System.out.println("Warning: Assuming Multicast port " + 20001);
            rover.multicastPort = 20001;
            missingArgument = true;
        }
        if (rover.ripPort == 0) {
            System.out.println("Warning: Port not specified, using port " + 32768);
            rover.ripPort = 32768;
            missingArgument = true;
        }
        if (missingMulticastIP) {
            System.out.println("Warning: Multicast IP not specified, using " +
                    "default IP 233.33.33.33");
            rover.multicastIp = "233.33.33.33";
            missingArgument = true;
        }
        if (missingUDPPort) {
            System.out.println("Warning: UDP Port not specified, using port 6767");
            rover.udpPort = 6767;
            missingArgument = true;
        }
        if ((missingFileName && !missingDestinationIP) || (missingDestinationIP && !missingFileName)) {
            System.err.println("Error: It appears that you have missed either the " +
                    "file name or the destination IP address. These two fields are " +
                    "optional, however, must be provided with each other if they are " +
                    "provided at all. Exiting ...");
            displayHelp();
        }
        if (missingArgument) {
            System.out.println("See " + HELP_L + " for options");
        }
    }

    /**
     * Displays available options to start the Rover. Note that calling this
     * function will exit the program, so use with caution.
     */
    private void displayHelp() {
        System.out.println();
//        String usage = "Usage: java Rover [" + ROVER_S + " | " + ROVER_L + "] VALUE | " +
//                "[" + SOURCE_PORT_S + " | " + SOURCE_PORT_L + "] VALUE | " +
//                "[" + MULTICAST_PORT_S + " | " + MULTICAST_PORT_L + "] VALUE | " +
//                "[" + MULTICAST_IP_S + " | " + MULTICAST_IP_L + "] VALUE | " +
//                "[" + VERBOSE_S + " | " + VERBOSE_L + "]";
        String usage = "Usage: java Rover [" + ROVER_S + " | " + ROVER_L + "] VALUE | " +
                "[OPTIONAL_FLAG_1 VALUE_1] [OPTIONAL_FLAG_2 VALUE_2] [...]";
        System.out.println(usage);
        System.out.println();


        System.out.println("List of flags");

        System.out.println(ROVER_S + ": Rover ID: This value should serve as a 1 byte" +
                " identifier to each Rover. This is the only field that is " +
                "not optional.");
        System.out.println();

        System.out.println("Optional flags:");
        System.out.println("[" + SOURCE_PORT_S + " | " + SOURCE_PORT_L + "]: Source " +
                "Port:" +
                " " +
                "In  " +
                "RIP, this field is 520. You will need to run the program as root to " +
                "set this value as 520.");
        System.out.println();

        System.out.println("[" + MULTICAST_PORT_S + " | " + MULTICAST_PORT_L + "]: " +
                "multicast " +
                "port. The port where the multicast messages are sent to.");
        System.out.println();

        System.out.println("[" + MULTICAST_IP_S + " | " + MULTICAST_IP_L + "]: multicast" +
                " " +
                "IP" +
                ". " +
                "The IP where messages are sent to. Defaulted to 233.33.33.33 if not " +
                "specified.");
        System.out.println();

        System.out.println("[" + UDP_PORT_S + " | " + UDP_PORT_L + "]: the port a Rover " +
                "will " +
                "listen on for Ripcom packets.");
        System.out.println();

        System.out.println("[" + FILE_NAME_S + " | " + FILE_NAME_L + "]: the file that " +
                "is" +
                " " +
                "to be" +
                " transmitted. If provided, it MUST exist along with the " +
                "-d flag.");
        System.out.println();

        System.out.println("[" + DESTINATION_IP_S + " | " + DESTINATION_IP_L + "]: the " +
                "IP" +
                " " +
                "address of the destination Rover. Must be of the form \"10.0" +
                ".<rover_id>.0\"");
        System.out.println();

        System.out.println("[" + VERBOSE_S + " | " + VERBOSE_L + "]: verbose mode. In " +
                "this " +
                "mode, every received packet is displayed, and the current routing " +
                "table is displayed at every available opportunity.");
        System.out.println();

        System.exit(1);
    }
}
