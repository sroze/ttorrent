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

import com.turn.ttorrent.client.Client;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.tracker.TrackedTorrent;
import com.turn.ttorrent.tracker.Tracker;
import com.turn.ttorrent.tracker.UDPTracker;

public class DirectoryTracker extends Ice4jFileTransfer
{
	protected File directory;
	
	/**
	 * Display usage.
	 * 
	 * @param s
	 */
	protected void usage(PrintStream s) 
	{
		s.println("Usage: DirectoryTracker [options]");
		super.usage(s);
		s.println(" -d,--directory DIRECTORY	Tracker directory");
		s.println();
	}
	
	/**
	 * Parse command line arguments.
	 * 
	 */
	protected CmdLineParser parseArguments (String[] args)
	{
		
		CmdLineParser parser = new CmdLineParser();
		CmdLineParser.Option directory = parser.addStringOption('d', "directory");
		super.parseArguments(args, parser);
		
		// Store directory
		this.directory = new File((String) parser.getOptionValue(directory));
		
		return parser;
	}
	
	/**
	 * Constructor.
	 * 
	 * @param args
	 */
	public DirectoryTracker (String[] args)
	{
		super(args);
		
		// Let's start the Tracker
		// Create the Tracker
		Tracker t = null;
		try {
			t = new UDPTracker(this.agent);
			t.start();
			System.out.println("Tracker started.");

			// Parse files in directory
			FilenameFilter filter = new FilenameFilter() {
				public boolean accept(File dir, String name) {
					List<String> accepted_ends = Arrays.asList(".avi", ".txt", ".mp3", ".ogg", ".flv");
					for (String end : accepted_ends) {
						if (name.endsWith(end)) {
							return true;
						}
					}

					return false;
				}
			};

			File parent = directory;
			System.out.println("Analysing directory: "+directory);
			for (File f : parent.listFiles(filter)) {
				try {
					// Try to generate the .torrent file
					File torrent_file = new File(f.getParentFile(), f.getName()+".torrent");
					Torrent torrent = Torrent.create(new File(f.getAbsolutePath()), new URI(t.getAnnounceUrl().toString()), "createdByTtorrent");
					System.out.println("Created torrent "+torrent.getName()+" for file: "+f.getAbsolutePath());
					torrent.save(new FileOutputStream(torrent_file));

					// Announce file to tracker
					TrackedTorrent tt = new TrackedTorrent(torrent);
					t.announce(tt);
					System.out.println("Torrent "+torrent.getName()+" announced");

					// Share torrent
					System.out.println("Sharing "+torrent.getName()+"...");
				    Client seeder = new Client(InetAddress.getLocalHost(), new SharedTorrent(torrent, parent, true));
				    seeder.share();

				} catch (Exception e) {
					System.err.println("Unable to describe, announce or share file: "+f.toString());
					e.printStackTrace(System.err);
				}
			}

			// Wait for user signal
			BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
			try {
				reader.readLine();
			} finally {
				reader.close();
			}

		} catch (Exception e) {
			System.err.println("Unable to start tracker.");
			e.printStackTrace(System.err);
			System.exit(1);
		} finally {
			if (t != null) {
				t.stop();
				System.out.println("Tracker stopped.");
			}
	    }
	}
	
	/**
	 * Main function.
	 * 
	 * @param args
	 */
	public static void main(String[] args) 
	{
		new DirectoryTracker(args);
	}

}
