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
    private static final String VERBOSE_S = "-v";
    private static final String HELP_S = "-h";

    private static final String ROVER_L = "--rover-id";
    private static final String SOURCE_PORT_L = "--source-port";
    private static final String MULTICAST_PORT_L = "--multicast-port";
    private static final String MULTICAST_IP_L = "--multicast-ip";
    private static final String DESTINATION_IP_L = "--destination-ip";
    private static final String UDP_PORT_L = "--udp-port";
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
                    rover.MULTICAST_PORT = Integer.parseInt(args[i + 1]);
                }
                if (argument.equals(SOURCE_PORT_S) || argument.equals(SOURCE_PORT_L)) {
                    rover.RIP_PORT = Integer.parseInt(args[i + 1]);
                }
                if (argument.equals(MULTICAST_IP_S) || argument.equals(MULTICAST_IP_L)) {
                    rover.MULTICAST_IP = args[i + 1];
                    missingMulticastIP = false;
                }
                if (argument.equals(ROVER_S) || argument.equals(ROVER_L)) {
                    rover.roverID = Integer.parseInt(args[i + 1]);
                    missingRoverID = false;
                }
                if (argument.equals(DESTINATION_IP_S) || argument.equals(DESTINATION_IP_L)) {
                    rover.destinationIP = args[i + 1];
                }
                if (argument.equals(UDP_PORT_S) || argument.equals(UDP_PORT_L)) {
                    rover.UDP_PORT = Integer.parseInt(args[i + 1]);
                    missingUDPPort = false;
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
        if (rover.MULTICAST_PORT == 0) {
            System.out.println("Warning: Assuming Multicast port " + 20001);
            rover.MULTICAST_PORT = 20001;
            missingArgument = true;
        }
        if (rover.RIP_PORT == 0) {
            System.out.println("Warning: Port not specified, using port " + 32768);
            rover.RIP_PORT = 32768;
            missingArgument = true;
        }
        if (missingMulticastIP) {
            System.out.println("Warning: Multicast IP not specified, using " +
                    "default IP 233.33.33.33");
            rover.MULTICAST_IP = "233.33.33.33";
            missingArgument = true;
        }
        if (missingUDPPort){
            System.out.println("Warning: UDP Port not specified, using port 6767");
            rover.UDP_PORT = 6767;
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
        String usage = "Usage: java Rover [" + ROVER_S + " | " + ROVER_L + "] VALUE | " +
                "[" + SOURCE_PORT_S + " | " + SOURCE_PORT_L + "] VALUE | " +
                "[" + MULTICAST_PORT_S + " | " + MULTICAST_PORT_L + "] VALUE | " +
                "[" + MULTICAST_IP_S + " | " + MULTICAST_IP_L + "] VALUE | " +
                "[" + VERBOSE_S + " | " + VERBOSE_L + "]";
        System.out.println(usage);
        System.out.println();


        System.out.println("List of flags");

        System.out.println(ROVER_S + ": Rover ID: This value should serve as an 8 bit" +
                " identifier to each Rover. This is the only field that is " +
                "not optional.");
        System.out.println();

        System.out.println("Optional flags:");
        System.out.println(SOURCE_PORT_S + ": Source Port: In  RIP, this field is 520. " +
                "You will need to run the program as root to set this value as 520.");
        System.out.println();

        System.out.println(MULTICAST_PORT_S + ": multicast port. The port where " +
                "the multicast messages are sent to.");
        System.out.println();

        System.out.println(MULTICAST_IP_S + ": multicast IP. The IP where messages " +
                "are sent to. Defaulted to 233.33.33.33 if not specified.");
        System.out.println();

        System.out.println(VERBOSE_S + ": verbose mode. In this mode, every received " +
                "packet is displayed, and the current routing table is " +
                "displayed at every available opportunity.");
        System.out.println();

        System.exit(1);
    }
}
