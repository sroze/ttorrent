package test;

import jargs.gnu.CmdLineParser;

import java.util.List;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import com.turn.ttorrent.client.Client;
import com.turn.ttorrent.client.Client.ClientState;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.tracker.TrackedTorrent;
import com.turn.ttorrent.tracker.Tracker;
import com.turn.ttorrent.tracker.UDPTracker;

public class SimpleClient extends Ice4jFileTransfer
{
	protected File output;
	protected File torrent;
	
	/**
	 * Display usage.
	 * 
	 * @param s
	 */
	protected void usage(PrintStream s) 
	{
		s.println("Usage: SimpleClient [options] torrent");
		super.usage(s);
		s.println(" -o,--output DIRECTORY	Output directory");
		s.println();
	}
	
	/**
	 * Parse command line arguments.
	 * 
	 */
	protected CmdLineParser parseArguments (String[] args)
	{
		
		CmdLineParser parser = new CmdLineParser();
		CmdLineParser.Option output = parser.addStringOption('o', "output");
		super.parseArguments(args, parser);
		
		// Store directory
		this.output = new File((String) parser.getOptionValue(output));
		
		// Check that it's the correct usage
		String[] otherArgs = parser.getRemainingArgs();
		if (otherArgs.length != 1) {
			usage(System.err);
			System.exit(1);
		}

		// Get the .torrent file path
		this.torrent = new File(otherArgs[0]);
		
		return parser;
	}
	
	/**
	 * Constructor.
	 * 
	 * @param args
	 */
	public SimpleClient (String[] args)
	{
		super(args);
		
		// Let's start the Client
		try {
			SharedTorrent torrent = SharedTorrent.fromFile(this.torrent, output);
			System.out.println("Starting client for torrent: "+torrent.getName());
			Client client = new Client(this.agent, torrent);

			try {
		    	System.out.println("Start to download: "+torrent.getName());
		    	client.share(); // SEEDING for completion signal
		    	// client.download()    // DONE for completion signal

		        while (!ClientState.SEEDING.equals(client.getState())) {
		        	// Check if there's an error
		        	if (ClientState.ERROR.equals(client.getState())) {
			            throw new Exception("ttorrent client Error State");
		        	}

		        	// Display statistics
		        	System.out.printf("%f %% - %d bytes downloaded - %d bytes uploaded\n", torrent.getCompletion(), torrent.getDownloaded(), torrent.getUploaded());

		        	// Wait one second
		        	TimeUnit.SECONDS.sleep(1);
		        }

		        System.out.println("download completed.");
			} catch (Exception e) {
				System.err.println("An error occurs...");
		    	e.printStackTrace(System.err);
			} finally {
				System.out.println("stop client.");
			    client.stop();
			}
	    } catch (Exception e) {
	    	System.err.println("An error occurs...");
	    	e.printStackTrace(System.err);
	    }
	}
	
	/**
	 * Main function.
	 * 
	 * @param args
	 */
	public static void main(String[] args) 
	{
		new SimpleClient(args);
	}

}
