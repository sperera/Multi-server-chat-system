package unimelb.ds.project1;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

/**
 * This class contains the main program, which starts the
 * {@link ChatServerApplication}.
 * 
 * @author Sewwandi Perera
 *
 */
public class ChatServerMain {
	public static void main(String[] args) {
		// Parse command line arguments
		Config config = new Config();
		CmdLineParser parser = new CmdLineParser(config);
		try {
			parser.parseArgument(args);
			ChatServerApplication application = new ChatServerApplication();
			application.start(config);
		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			parser.printUsage(System.err);
		} catch (RuntimeException e) {
			System.err.println(e.getMessage());
		}
	}
}
