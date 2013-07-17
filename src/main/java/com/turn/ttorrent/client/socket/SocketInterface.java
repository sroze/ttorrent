package com.turn.ttorrent.client.socket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * The socket used by the connection handler to communicate
 * with the client.
 *
 */
public interface SocketInterface 
{
	/**
	 * Read bytes from input stream to the byte buffer.
	 * 
	 * @param buffer
	 * @return
	 * @throws IOException
	 */
	public int read(ByteBuffer buffer) throws IOException;

	/**
	 * Get the base socket.
	 * 
	 * @return
	 */
	public Socket socket();

	/**
	 * Close channel connection.
	 * 
	 * @throws IOException
	 */
	public void close() throws IOException;

	/**
	 * Write this bytebuffer data in output stream.
	 * 
	 * @param data
	 * @return
	 * @throws IOException
	 */
	public int write(ByteBuffer data) throws IOException;

	/**
	 * Configure the blocking comportement of channel.
	 * 
	 * @param b
	 * @throws IOException
	 */
	public void configureBlocking(boolean b) throws IOException;

	/**
	 * Is this channel connected ?
	 * 
	 * @return
	 */
	public boolean isConnected();

	/**
	 * Returns the address to which the channel is connected.
	 * 
	 * @return InetAddress
	 */
	public InetAddress getInetAddress ();
	
	/**
	 * Returns the remote port to which this channel is connected.
	 * 
	 * @return
	 */
	public int getPort ();
}
