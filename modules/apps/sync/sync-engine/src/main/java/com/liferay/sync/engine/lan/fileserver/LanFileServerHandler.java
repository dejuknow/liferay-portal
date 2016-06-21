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

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.HEAD;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import com.liferay.sync.engine.lan.util.LanTokenUtil;
import com.liferay.sync.engine.model.SyncAccount;
import com.liferay.sync.engine.model.SyncFile;
import com.liferay.sync.engine.service.SyncAccountService;
import com.liferay.sync.engine.service.SyncFileService;
import com.liferay.sync.engine.util.GetterUtil;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;

import java.net.SocketAddress;

import java.util.List;

import javax.activation.MimetypesFileTypeMap;

import org.apache.commons.lang.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Dennis Ju
 */
public class LanFileServerHandler
	extends SimpleChannelInboundHandler<FullHttpRequest> {

	@Override
	public void channelRead0(
			ChannelHandlerContext channelHandlerContext,
			FullHttpRequest fullHttpRequest)
		throws Exception {

		ChannelPipeline channelPipeline = channelHandlerContext.pipeline();

		if (channelPipeline.get(SslHandler.class) == null) {
			_sendError(channelHandlerContext, BAD_REQUEST);

			return;
		}

		DecoderResult decoderResult = fullHttpRequest.decoderResult();

		if (!decoderResult.isSuccess()) {
			_sendError(channelHandlerContext, BAD_REQUEST);

			return;
		}

		if (fullHttpRequest.method() == GET) {
			processGetRequest(channelHandlerContext, fullHttpRequest);
		}
		else if (fullHttpRequest.method() == HEAD) {
			processHeadRequest(channelHandlerContext, fullHttpRequest);
		}
		else {
			_sendError(channelHandlerContext, BAD_REQUEST);
		}
	}

	@Override
	public void exceptionCaught(
		ChannelHandlerContext channelHandlerContext, Throwable cause) {

		_logger.error(cause.getMessage(), cause);

		Channel channel = channelHandlerContext.channel();

		if (channel.isActive()) {
			_sendError(channelHandlerContext, INTERNAL_SERVER_ERROR);
		}
	}

	protected void processGetRequest(
			ChannelHandlerContext channelHandlerContext,
			FullHttpRequest fullHttpRequest)
		throws Exception {

		HttpHeaders httpHeaders = fullHttpRequest.headers();

		String token = httpHeaders.get("token");

		if ((token == null) || token.isEmpty()) {
			Channel channel = channelHandlerContext.channel();

			SocketAddress remoteAddress = channel.remoteAddress();

			_logger.error("Lan client {} did not send token", remoteAddress);

			_sendError(channelHandlerContext, NOT_FOUND);

			return;
		}

		if (!LanTokenUtil.consumeToken(token)) {
			Channel channel = channelHandlerContext.channel();

			SocketAddress remoteAddress = channel.remoteAddress();

			_logger.error(
				"Lan client {} token not found or expired", remoteAddress);

			_sendError(channelHandlerContext, NOT_FOUND);

			return;
		}

		SyncFile syncFile = _getSyncFile(fullHttpRequest);

		if (syncFile == null) {
			_sendError(channelHandlerContext, NOT_FOUND);

			return;
		}

		sendFile(channelHandlerContext, fullHttpRequest, syncFile);
	}

	protected void processHeadRequest(
		ChannelHandlerContext channelHandlerContext,
		FullHttpRequest fullHttpRequest) {

		SyncFile syncFile = _getSyncFile(fullHttpRequest);

		if (syncFile == null) {
			_sendError(channelHandlerContext, NOT_FOUND);

			return;
		}

		String key = syncFile.getKey();

		if ((key == null) || key.isEmpty()) {
			_sendError(channelHandlerContext, NOT_FOUND);

			return;
		}

		String encryptedToken = null;

		try {
			encryptedToken = LanTokenUtil.createEncryptedToken(key);
		}
		catch (Exception e) {
			_sendError(channelHandlerContext, NOT_FOUND);

			return;
		}

		HttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK);

		HttpHeaders httpHeaders = httpResponse.headers();

		httpHeaders.set("encryptedToken", encryptedToken);

		channelHandlerContext.writeAndFlush(httpResponse);
	}

	protected void sendFile(
			ChannelHandlerContext channelHandlerContext,
			FullHttpRequest fullHttpRequest, SyncFile syncFile)
		throws Exception {

		HttpResponse httpResponse = new DefaultHttpResponse(HTTP_1_1, OK);

		File file = new File(syncFile.getFilePathName());

		RandomAccessFile randomAccessFile = null;

		try {
			randomAccessFile = new RandomAccessFile(file, "r");
		}
		catch (FileNotFoundException fnfe) {
			_logger.error(fnfe.getMessage(), fnfe);

			_sendError(channelHandlerContext, NOT_FOUND);

			return;
		}

		long length = randomAccessFile.length();

		HttpUtil.setContentLength(httpResponse, length);

		HttpHeaders httpHeaders = httpResponse.headers();

		MimetypesFileTypeMap mimetypesFileTypeMap = new MimetypesFileTypeMap();

		httpHeaders.set(
			HttpHeaderNames.CONTENT_TYPE,
			mimetypesFileTypeMap.getContentType(syncFile.getName()));

		if (HttpUtil.isKeepAlive(fullHttpRequest)) {
			httpHeaders.set(
				HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
		}

		channelHandlerContext.write(httpResponse);

		ChunkedFile chunkedFile = new ChunkedFile(
			randomAccessFile, 0, length, 8192);

		HttpChunkedInput httpChunkedInput = new HttpChunkedInput(chunkedFile);

		ChannelFuture channelFuture = channelHandlerContext.writeAndFlush(
			httpChunkedInput, channelHandlerContext.newProgressivePromise());

		if (!HttpUtil.isKeepAlive(fullHttpRequest)) {
			channelFuture.addListener(ChannelFutureListener.CLOSE);
		}
	}

	private SyncFile _getSyncFile(FullHttpRequest fullHttpRequest) {
		String[] pathArray = StringUtils.split(fullHttpRequest.uri(), "/");

		if (pathArray.length != 4) {
			return null;
		}

		String lanServerUuid = pathArray[0];
		long repositoryId = GetterUtil.getLong(pathArray[1]);
		long typePK = GetterUtil.getLong(pathArray[2]);
		long versionId = GetterUtil.getLong(pathArray[3]);

		if (lanServerUuid.isEmpty() || (repositoryId == 0) || (typePK == 0) ||
			(versionId == 0)) {

			return null;
		}

		List<SyncAccount> syncAccounts = SyncAccountService.findSyncAccounts(
			lanServerUuid);

		for (SyncAccount syncAccount : syncAccounts) {
			SyncFile syncFile = SyncFileService.fetchSyncFile(
				repositoryId, syncAccount.getSyncAccountId(), typePK,
				versionId);

			if (syncFile.getState() == SyncFile.STATE_SYNCED) {
				return syncFile;
			}
		}

		return null;
	}

	private void _sendError(
		ChannelHandlerContext channelHandlerContext,
		HttpResponseStatus httpResponseStatus) {

		HttpResponse httpResponse = new DefaultFullHttpResponse(
			HTTP_1_1, httpResponseStatus);

		ChannelFuture channelFuture = channelHandlerContext.writeAndFlush(
			httpResponse);

		channelFuture.addListener(ChannelFutureListener.CLOSE);
	}

	private static final Logger _logger = LoggerFactory.getLogger(
		LanFileServerHandler.class);

}