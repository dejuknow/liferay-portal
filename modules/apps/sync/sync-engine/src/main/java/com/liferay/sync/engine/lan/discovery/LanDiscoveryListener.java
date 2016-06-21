/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.sync.engine.lan.discovery;

import com.liferay.sync.engine.util.PropsValues;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;

/**
 * @author Dennis Ju
 */
public class LanDiscoveryListener {

	public void listen() throws Exception {
		_eventLoopGroup = new NioEventLoopGroup();

		try {
			Bootstrap bootstrap = new Bootstrap();

			bootstrap.channel(NioDatagramChannel.class);
			bootstrap.group(_eventLoopGroup);
			bootstrap.handler(new LanDiscoveryListenerHandler());

			ChannelFuture channelFuture = bootstrap.bind(
				PropsValues.SYNC_LAN_UDP_PORT);

			channelFuture.sync();

			Channel channel = channelFuture.channel();

			ChannelFuture closeChannelFuture = channel.closeFuture();

			closeChannelFuture.await();
		}
		finally {
			shutdown();
		}
	}

	public void shutdown() {
		if (_eventLoopGroup != null) {
			_eventLoopGroup.shutdownGracefully();
		}
	}

	private EventLoopGroup _eventLoopGroup;

}