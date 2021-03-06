package com.turn.ttorrent.tracker;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Calendar;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.simpleframework.http.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.turn.ttorrent.common.protocol.TrackerMessage.AnnounceRequestMessage;
import com.turn.ttorrent.common.protocol.TrackerMessage.ErrorMessage;
import com.turn.ttorrent.common.protocol.TrackerMessage.MessageValidationException;
import com.turn.ttorrent.common.protocol.http.HTTPAnnounceResponseMessage;
import com.turn.ttorrent.common.protocol.udp.UDPAnnounceRequestMessage;
import com.turn.ttorrent.common.protocol.udp.UDPAnnounceResponseMessage;
import com.turn.ttorrent.common.protocol.udp.UDPConnectRequestMessage;
import com.turn.ttorrent.common.protocol.udp.UDPConnectResponseMessage;

/**
 * UDP Tracker service to serve the tracker's announce requests.
 *
 * <p>
 * It only serves torrents the {@link UDPTracker} knows about.
 * </p>
 *
 * <p>
 * The list of torrents {@link #torrents} is a map of torrent hashes to their
 * corresponding Torrent objects, and is maintained by the {@link Tracker} this
 * service is part of. The UDPTrackerService only has a reference to this map, and
 * does not modify it.
 * </p>
 *
 * @author sroze
 * @see <a href="http://bittorrent.org/beps/bep_0015.html">UDP Tracker Protocol for BitTorrent</a>
 */
public class UDPTrackerService 
{
	private static final Logger logger =
		LoggerFactory.getLogger(UDPTrackerService.class);

	/**
	 * Maximum UDP packet size expected, in bytes.
	 *
	 * The biggest packet in the exchange is the announce response, which in 20
	 * bytes + 6 bytes per peer. Common numWant is 50, so 20 + 6 * 50 = 320.
	 * With headroom, we'll ask for 512 bytes.
	 */
	private static final int UDP_PACKET_LENGTH = 512;

	private final String version;
	private final ConcurrentMap<String, TrackedTorrent> torrents;
	private DatagramSocket socket;
	private boolean stop = false;
	private final Random random;
	private ClientsCollectorThread collector = null;
	
	/**
	 * List of tracker clients.
	 * 
	 * We have to keep a list of tracker clients with some informations, such
	 * as the connectionId and the expiration date.
	 */
	private ConcurrentMap<Long, UDPTrackedClient> clients;

	/**
	 * Create a new UDPTrackerService serving the given torrents.
	 *
	 * @param torrents The torrents this UDPTrackerService should serve requests
	 * for.
	 */
	UDPTrackerService(String version, ConcurrentMap<String, TrackedTorrent> torrents) {
		this.version = version;
		this.torrents = torrents;
		
		this.random = new Random();
		this.clients = new ConcurrentHashMap<Long, UDPTrackedClient>();
	}

	/**
	 * Start tracker message receiver.
	 * 
	 * @param socket The opened DatagramSocket
	 */
	public void start(DatagramSocket socket) {
		this.socket = socket;
		
		// Start the collector thread
		if (this.collector != null) {
			this.collector = new ClientsCollectorThread();
			this.collector.setName("udp-clients-collector");
			this.collector.start();
		}
        
        while (!stop) {
            try {
            	// Receive a new packet
            	DatagramPacket receivePacket = new DatagramPacket(new byte[UDP_PACKET_LENGTH], UDP_PACKET_LENGTH);
				socket.receive(receivePacket);
				
				// We've got our new packet, dispatch to handler
				handle(receivePacket);
				
			} catch (IOException e) {
				logger.warn("Could not receive datagram packet ({}) !", e, e);
			}
        }
	}

	/**
	 * Stop tracker service and Datagram socket.
	 * 
	 */
	public void stop() {
		this.stop = true;
		this.socket.close();
		
		if (this.collector != null && this.collector.isAlive()) {
			this.collector.interrupt();
			logger.info("UDP clients collection terminated.");
		}
	}
	
	/**
	 * Handle the incoming request on the tracker service.
	 *
	 * <p>
	 * This analyze the incoming packet and dispatch processing to
	 * specific handlers functions.
	 * </p>
	 * 
	 * <p>
	 * <b>Note:</b> We can't know what type of packet is received, so
	 * we've to try to parse the two types of messages.
	 * </p>
	 *
	 * @param DatagramPacket packet The received packet
	 */
	private void handle(DatagramPacket packet) {
		try {
			ByteBuffer data = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
			
			try {
				// Try to parse a connect request message
				UDPConnectRequestMessage message = UDPConnectRequestMessage.parse(data);
				
				// If we're here, it's a connect request
				this.handleConnectResponse(packet, message);
				return;
			} catch (MessageValidationException e) {
				// Silently ignore this message validation error, 
				// it should be another type of message.
			}
			
			try {
				// Try to parse an announce request
				UDPAnnounceRequestMessage message = UDPAnnounceRequestMessage.parse(data);
				
				// Get the client object
				UDPTrackedClient client = clients.get(new Long(message.getConnectionId()));
				if (client == null) {
					throw new Exception("Unrecognized connectionId");
				}
				
				// It's an announce request, let's handle
				this.handleAnnounceResponse(client, message);
				return;
			} catch (MessageValidationException e) {
				// Silently ignore this message validation error, 
				// it should be another type of message.
			}
			
			throw new MessageValidationException("Unable to know what type of request");
		} catch (Exception e) {
			logger.warn("Error analyzing datagram packet from client at {}: {} (length={}).", 
					new String[] { packet.getAddress().toString(), e.getMessage(), 
					new Integer(packet.getLength()).toString() }, e);
			e.printStackTrace();
		}
	}
	
	/**
	 * Compute and send the connect response.
	 * 
	 * @param packet	The DatagramPacket, used for client recognition
	 * @param request	The UDPConnectRequestMessage
	 */
	private void handleConnectResponse (DatagramPacket packet, UDPConnectRequestMessage request)
	{
		logger.info("Connect request received from "+packet.getAddress());
		
		// Generate a new connectionId
		Long connectionId = null;
		do {
			connectionId = this.random.nextLong();
		} while (this.clients.containsKey(connectionId));
		
		// Create the trackered client
		InetSocketAddress address = new InetSocketAddress(packet.getAddress(), packet.getPort());
		UDPTrackedClient client = new UDPTrackedClient(address, connectionId);
		client.expireIn(Calendar.MINUTE, 2);
		
		// Add client to the list
		this.clients.put(connectionId, client);
		
		// Compute and send the response
		this.send(client.getAddress(), UDPConnectResponseMessage.craft(
				request.getTransactionId(), connectionId.longValue()).getData());
		logger.info("Connect response sent to "+packet.getAddress());
	}

	/**
	 * Compute and send the announce response.
	 * 
	 * <p>
	 * This method will respond to the announce request if the client
	 * is known and connected (he made a connect request) and if
	 * the torrent is known by the tracker.
	 * </p>
	 * 
	 * @param message
	 */
	private void handleAnnounceResponse (UDPTrackedClient client, UDPAnnounceRequestMessage request)
	{
		logger.info("Announce request received from "+client.getAddress());
		
		// The requested torrent must have been announced by the tracker
		TrackedTorrent torrent = this.torrents.get(request.getHexInfoHash());
		if (torrent == null) {
			logger.warn("Requested torrent wasn't announced by tracker: {}.",
				request.getHexInfoHash());
			return;
		}
		
		AnnounceRequestMessage.RequestEvent event = request.getEvent();
		String peerId = request.getHexPeerId();

		// When no event is specified, it's a periodic update while the client
		// is operating. If we don't have a peer for this announce, it means
		// the tracker restarted while the client was running. Consider this
		// announce request as a 'started' event.
		if ((event == null ||
				AnnounceRequestMessage.RequestEvent.NONE.equals(event)) &&
			torrent.getPeer(peerId) == null) {
			event = AnnounceRequestMessage.RequestEvent.STARTED;
		}

		// If an event other than 'started' is specified and we also haven't
		// seen the peer on this torrent before, something went wrong. A
		// previous 'started' announce request should have been made by the
		// client that would have had us register that peer on the torrent this
		// request refers to.
		if (event != null && torrent.getPeer(peerId) == null &&
			!AnnounceRequestMessage.RequestEvent.STARTED.equals(event)) {
			logger.warn("Peer {} must be connected before an announcing request.",
				peerId);
			return;
		}

		// Update the torrent according to the announce event
		TrackedPeer peer = null;
		try {
			peer = torrent.update(event,
				ByteBuffer.wrap(request.getPeerId()),
				request.getHexPeerId(),
				request.getIp(),
				request.getPort(),
				request.getUploaded(),
				request.getDownloaded(),
				request.getLeft());
		} catch (IllegalArgumentException iae) {
			logger.warn("Peer {} sent an invalid event.", peerId);
			return;
		} catch (UnsupportedEncodingException e) {
			logger.warn("Peer {} sent announce request with unsupported encoding.", peerId);
			return;
		}

		// Craft the answer
		UDPAnnounceResponseMessage announceResponse = UDPAnnounceResponseMessage.craft(
			request.getTransactionId(),
			torrent.getAnnounceInterval(), 
			torrent.leechers(),
			torrent.seeders(), 
			torrent.getSomePeers(peer));
		
		// Send the awnser
		this.send(client.getAddress(), announceResponse.getData());
		logger.info("Announce response sent to "+client.getAddress());
	}
	
	/**
	 * Send an UDP message to this address.
	 * 
	 * @param address
	 * @param message
	 */
	private void send(InetSocketAddress address, ByteBuffer data) {
		try {
			this.socket.send(new DatagramPacket(
				data.array(),
				data.capacity(),
				address
			));
		} catch (IOException e) {
			logger.warn("Error sending datagram packet to client at {}: {}.",
				address, e.getMessage());
		}
	}

	/**
	 * This thread is the collector that have to remove expired clients
	 * from the list.
	 * 
	 */
	private class ClientsCollectorThread extends Thread {
		
		private static final int CLIENT_COLLECTION_FREQUENCY_SECONDS = 15;

		@Override
		public void run() {
			logger.info("UDP clients collection started.");
			
			while (!stop) {
				for (UDPTrackedClient client : clients.values()) {
					if (client.isExpired()) {
						clients.remove(client.getConnectionId());
					}
				}

				try {
					Thread.sleep(ClientsCollectorThread.CLIENT_COLLECTION_FREQUENCY_SECONDS * 1000);
				} catch (InterruptedException ie) {
					// Ignore
				}
			}
		}
	}
}
