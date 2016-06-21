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

package com.liferay.sync.engine.lan.fileserver;

import com.liferay.sync.engine.model.ModelListener;
import com.liferay.sync.engine.model.SyncAccount;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetSocketAddress;

import java.util.Map;

/**
 * @author Dennis Ju
 */
public class LanFileServer {

	public int getPort() {
		return _port;
	}

	public ModelListener<SyncAccount> getSyncAccountListener() {
		if (syncAccountListener != null) {
			return syncAccountListener;
		}

		syncAccountListener = new ModelListener<SyncAccount>() {

			@Override
			public void onCreate(SyncAccount syncAccount) {
				_lanFileServerInitializer.updateDomainNameMapping();
			}

			@Override
			public void onRemove(SyncAccount syncAccount) {
				_lanFileServerInitializer.updateDomainNameMapping();
			}

			@Override
			public void onUpdate(
				SyncAccount syncAccount, Map<String, Object> originalValues) {

				if (originalValues.containsKey("active") ||
					originalValues.containsKey("lanCertificate") ||
					originalValues.containsKey("lanKey")) {

					_lanFileServerInitializer.updateDomainNameMapping();
				}
			}

		};

		return syncAccountListener;
	}

	public void start() throws Exception {
		_childEventLoopGroup = new NioEventLoopGroup();
		_parentEventLoopGroup = new NioEventLoopGroup(1);

		ServerBootstrap serverBootstrap = new ServerBootstrap();

		serverBootstrap.group(_parentEventLoopGroup, _childEventLoopGroup);
		serverBootstrap.channel(NioServerSocketChannel.class);

		_lanFileServerInitializer = new LanFileServerInitializer();

		serverBootstrap.childHandler(_lanFileServerInitializer);
		serverBootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);

		ChannelFuture channelFuture = serverBootstrap.bind(0);

		channelFuture.sync();

		Channel channel = channelFuture.channel();

		InetSocketAddress inetSocketAddress =
			(InetSocketAddress)channel.localAddress();

		_port = inetSocketAddress.getPort();

		channelFuture.sync();
	}

	public void stop() {
		if (_childEventLoopGroup != null) {
			_childEventLoopGroup.shutdownGracefully();
		}

		if (_parentEventLoopGroup != null) {
			_parentEventLoopGroup.shutdownGracefully();
		}
	}

	protected ModelListener<SyncAccount> syncAccountListener;

	private EventLoopGroup _childEventLoopGroup;
	private LanFileServerInitializer _lanFileServerInitializer;
	private EventLoopGroup _parentEventLoopGroup;
	private int _port;

}