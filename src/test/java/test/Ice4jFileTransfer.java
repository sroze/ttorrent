package test;

import jargs.gnu.CmdLineParser;

import java.io.PrintStream;

import org.apache.log4j.BasicConfigurator;
import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.harvest.StunCandidateHarvester;
import org.ice4j.ice.harvest.UPNPHarvester;

import com.turn.ttorrent.common.ice4j.Ice4jAgent;

/**
 * Define an ice4j configuration, a tracker and a client.
 * 
 * @author samuel
 */
public abstract class Ice4jFileTransfer 
{
	protected String username;
	protected String password;
	protected TransportAddress stunServer;
	
	protected Ice4jAgent agent;
	
	/**
	 * Constructor.
	 * 
	 * @param args
	 */
	public Ice4jFileTransfer (String[] args)
	{
		parseArguments(args);
	}
	
	/**
	 * Display usage.
	 * 
	 * @param s
	 */
	protected void usage(PrintStream s) 
	{
		s.println("Create a tracker for each files in directory.");
		s.println("Note: .torrent files are created or regenerated.");
		s.println();
		s.println("Available options:");
		s.println(" -h,--help 						Show this help and exit.");
		s.println(" -u,--username USERNAME			ice4j username.");
		s.println(" -p,--password PASSWORD			ice4j password.");
		s.println(" -s,--stun-server HOST:PORT		STUN server.");
	}
	
	/**
	 * Parse command line arguments.
	 * 
	 * @return
	 */
	protected CmdLineParser parseArguments (String[] args)
	{
		return parseArguments(args, new CmdLineParser());
	}
	
	/**
	 * Parse command line arguments.
	 * 
	 * @return
	 */
	protected CmdLineParser parseArguments (String[] args, CmdLineParser parser)
	{
		BasicConfigurator.configure();

		CmdLineParser.Option help = parser.addBooleanOption('h', "help");
		CmdLineParser.Option username = parser.addStringOption('u', "username");
		CmdLineParser.Option password = parser.addStringOption('p', "password");
		CmdLineParser.Option stunServer = parser.addStringOption('s', "stun-server");
		
		try {
			parser.parse(args);
		} catch (CmdLineParser.OptionException oe) {
			System.err.println(oe.getMessage());
			usage(System.err);
			System.exit(1);
		}	

		// Display help and exit if requested
		if (Boolean.TRUE.equals((Boolean)parser.getOptionValue(help))) {
			usage(System.out);
			System.exit(0);
		}
		
		// Add arguments
		this.username = (String) parser.getOptionValue(username);
		this.password = (String) parser.getOptionValue(password);
		
		// Parse stun server address
		String sServer = (String) parser.getOptionValue(stunServer);
		int dblPointsIndex = sServer.indexOf(':');
		this.stunServer = new TransportAddress(sServer.substring(0, dblPointsIndex), Integer.parseInt(sServer.substring(dblPointsIndex)), Transport.UDP);

		// Create the ice4j agent
		agent = new Ice4jAgent();
        
		// Add the STUN server
		StunCandidateHarvester stunHarv = new StunCandidateHarvester(this.stunServer, this.username);
        agent.addCandidateHarvester(stunHarv);
		
		// Add the UPnP candidate
		UPNPHarvester upnpHarv = new UPNPHarvester();
		//agent.addCandidateHarvester(upnpHarv);
		
		return parser;
	}
}
