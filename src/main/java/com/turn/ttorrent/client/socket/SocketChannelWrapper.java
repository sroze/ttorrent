package com.turn.ttorrent.client.socket;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * A SocketChanell wrapper, to implement the @see SocketInterface
 * interface.
 *
 */
public class SocketChannelWrapper implements SocketInterface
{
	/**
	 * The SocketChannel instance.
	 * 
	 */
	protected SocketChannel channel;
	
	/**
	 * Constructor.
	 * 
	 * @param channel
	 */
	public SocketChannelWrapper (SocketChannel channel)
	{
		this.channel = channel;
	}

	@Override
	public int read(ByteBuffer buffer) throws IOException {
		return channel.read(buffer);
	}

	@Override
	public Socket socket() {
		return channel.socket();
	}

	@Override
	public void close() throws IOException {
		channel.close();
	}

	@Override
	public int write(ByteBuffer data) throws IOException {
		return channel.write(data);
	}

	@Override
	public void configureBlocking(boolean block) throws IOException {
		channel.configureBlocking(block);
	}

	@Override
	public boolean isConnected() {
		return channel.isConnected();
	}
}
