package com.turn.ttorrent.client.socket;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * The socket used by the connection handler to communicate
 * with the client.
 *
 */
public interface SocketInterface 
{
	public int read(ByteBuffer buffer) throws IOException;

	public Socket socket();

	public void close() throws IOException;

	public int write(ByteBuffer data) throws IOException;

	public void configureBlocking(boolean b) throws IOException;

	public boolean isConnected();

}
