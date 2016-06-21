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

package com.liferay.sync.engine.lan;

import com.liferay.sync.engine.document.library.handler.Handler;
import com.liferay.sync.engine.lan.util.LanClientUtil;
import com.liferay.sync.engine.lan.util.LanPEMParserUtil;
import com.liferay.sync.engine.lan.util.LanTokenUtil;
import com.liferay.sync.engine.model.SyncAccount;
import com.liferay.sync.engine.model.SyncFile;
import com.liferay.sync.engine.model.SyncLanClient;
import com.liferay.sync.engine.service.SyncAccountService;
import com.liferay.sync.engine.service.SyncLanClientService;
import com.liferay.sync.engine.service.SyncLanEndpointService;
import com.liferay.sync.engine.util.PropsValues;

import java.io.IOException;

import java.net.Socket;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Dennis Ju
 */
public class LanSession {

	public static LanSession getLanSession() {
		if (_lanSession == null) {
			_lanSession = new LanSession();
		}

		return _lanSession;
	}

	public LanSession() {
		_downloadHttpClient = _createHttpClient(
			PropsValues.SYNC_LAN_SESSION_DOWNLOAD_CONNECT_TIMEOUT,
			PropsValues.SYNC_LAN_SESSION_DOWNLOAD_MAX_PER_ROUTE,
			PropsValues.SYNC_LAN_SESSION_DOWNLOAD_MAX_TOTAL,
			PropsValues.SYNC_LAN_SESSION_DOWNLOAD_SOCKET_TIMEOUT);

		_queryHttpClient = _createHttpClient(
			PropsValues.SYNC_LAN_SESSION_QUERY_CONNECT_TIMEOUT,
			PropsValues.SYNC_LAN_SESSION_QUERY_POOL_MAX_SIZE,
			PropsValues.SYNC_LAN_SESSION_QUERY_POOL_MAX_SIZE,
			PropsValues.SYNC_LAN_SESSION_QUERY_SOCKET_TIMEOUT);
	}

	public HttpGet downloadFile(SyncFile syncFile, final Handler handler)
		throws Exception {

		Object[] objects = findSyncLanClient(syncFile);

		if (objects == null) {
			handler.handleException(new Exception("none found"));

			return null;
		}

		SyncLanClient syncLanClient = (SyncLanClient)objects[0];

		String url = _getUrl(syncLanClient, syncFile);

		final HttpGet httpGet = new HttpGet(url);

		String encryptedToken = (String)objects[1];

		String token = LanTokenUtil.decryptToken(
			syncFile.getKey(), encryptedToken);

		httpGet.addHeader("token", token);

		Runnable runnable = new Runnable() {

			@Override
			public void run() {
				try {
					_downloadHttpClient.execute(
						httpGet, handler, HttpClientContext.create());
				}
				catch (Exception e) {
					handler.handleException(e);
				}
			}

		};

		_downloadExecutorService.execute(runnable);

		return httpGet;
	}

	public Object[] findSyncLanClient(SyncFile syncFile) throws Exception {
		SyncAccount syncAccount = SyncAccountService.fetchSyncAccount(
			syncFile.getSyncAccountId());

		List<String> syncLanClientUuids =
			SyncLanEndpointService.findSyncLanClientUuids(
				syncAccount.getLanServerUuid(), syncFile.getRepositoryId());

		if (syncLanClientUuids.isEmpty()) {
			return null;
		}

		List<Callable<Object[]>> querySyncLanClientCallables =
			Collections.synchronizedList(
				new ArrayList<>(syncLanClientUuids.size()));

		for (String syncLanClientUuid : syncLanClientUuids) {
			SyncLanClient syncLanClient =
				SyncLanClientService.fetchSyncLanClient(syncLanClientUuid);

			querySyncLanClientCallables.add(
				createQuerySyncLanClientCallable(syncLanClient, syncFile));
		}

		int queryPoolSize = Math.min(
			syncLanClientUuids.size(),
			PropsValues.SYNC_LAN_SESSION_QUERY_POOL_MAX_SIZE);

		List<Callable<Object[]>> querySyncLanClientPool = new ArrayList<>(
			queryPoolSize);

		for (int i = 0; i < queryPoolSize; i++) {
			Callable callable = new Callable<Object[]>() {

				@Override
				public Object[] call() throws Exception {
					Callable<Object[]> querySyncLanClientCallable =
						querySyncLanClientCallables.remove(0);

					try {
						return querySyncLanClientCallable.call();
					}
					catch (Exception e) {
						return this.call();
					}
				}

			};

			querySyncLanClientPool.add(callable);
		}

		ExecutorService executorService = _getExecutorService();

		try {
			return executorService.invokeAny(
				querySyncLanClientPool,
				PropsValues.SYNC_LAN_SESSION_QUERY_TOTAL_TIMEOUT,
				TimeUnit.MILLISECONDS);
		}
		catch (Exception e) {
			return null;
		}
	}

	protected Callable<Object[]> createQuerySyncLanClientCallable(
		SyncLanClient syncLanClient, SyncFile syncFile) {

		String url = _getUrl(syncLanClient, syncFile);

		final HttpHead httpHead = new HttpHead(url);

		return new Callable<Object[]>() {

			@Override
			public Object[] call() throws Exception {
				HttpResponse httpResponse = _queryHttpClient.execute(
					httpHead, HttpClientContext.create());

				StatusLine statusLine = httpResponse.getStatusLine();

				if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
					throw new Exception();
				}

				Header[] headers = httpResponse.getHeaders("encryptedToken");

				if (headers.length == 0) {
					_logger.error(
						"Sync lan client did not return encrypted token");

					throw new Exception();
				}

				Header header = headers[0];

				String encryptedToken = header.getValue();

				return new Object[] {syncLanClient, encryptedToken};
			}

		};
	}

	private static HttpClient _createHttpClient(
		int connectTimeout, int maxPerRoute, int maxTotal, int socketTimeout) {

		RegistryBuilder<ConnectionSocketFactory> registryBuilder =
			RegistryBuilder.create();

		try {
			SSLConnectionSocketFactory sslConnectionSocketFactory =
				_getSSLSocketFactory();

			registryBuilder.register("https", sslConnectionSocketFactory);
		}
		catch (Exception e) {
			_logger.error(e.getMessage(), e);
		}

		Registry<ConnectionSocketFactory> registry = registryBuilder.build();

		PoolingHttpClientConnectionManager connectionManager =
			new PoolingHttpClientConnectionManager(registry);

		connectionManager.setDefaultMaxPerRoute(maxPerRoute);
		connectionManager.setMaxTotal(maxTotal);

		HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();

		httpClientBuilder.disableAutomaticRetries();

		RequestConfig.Builder builder = RequestConfig.custom();

		builder.setConnectTimeout(connectTimeout);
		builder.setSocketTimeout(socketTimeout);

		httpClientBuilder.setDefaultRequestConfig(builder.build());

		httpClientBuilder.setConnectionManager(connectionManager);

		return httpClientBuilder.build();
	}

	private static ExecutorService _getExecutorService() {
		if (_queryExecutorService != null) {
			return _queryExecutorService;
		}

		_queryExecutorService = new ThreadPoolExecutor(
			PropsValues.SYNC_LAN_SESSION_QUERY_POOL_MAX_SIZE,
			PropsValues.SYNC_LAN_SESSION_QUERY_POOL_MAX_SIZE, 60,
			TimeUnit.SECONDS, new LinkedBlockingQueue<>());

		_queryExecutorService.allowCoreThreadTimeOut(true);

		return _queryExecutorService;
	}

	private static SSLConnectionSocketFactory _getSSLSocketFactory()
		throws Exception {

		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

		keyStore.load(null, null);

		for (SyncAccount syncAccount : SyncAccountService.findAll()) {
			if (!syncAccount.isActive()) {
				continue;
			}

			try {
				PrivateKey privateKey = LanPEMParserUtil.parsePrivateKey(
					syncAccount.getLanKey());

				if (privateKey == null) {
					_logger.error(
						"SyncAccount {} missing valid private key",
						syncAccount.getSyncAccountId());

					continue;
				}

				X509Certificate x509Certificate =
					LanPEMParserUtil.parseX509Certificate(
						syncAccount.getLanCertificate());

				if (x509Certificate == null) {
					_logger.error(
						"SyncAccount {} missing valid certificate",
						syncAccount.getSyncAccountId());

					continue;
				}

				keyStore.setCertificateEntry(
					syncAccount.getLanServerUuid(), x509Certificate);

				keyStore.setKeyEntry(
					syncAccount.getLanServerUuid(), privateKey,
					"".toCharArray(), new Certificate[] {x509Certificate});
			}
			catch (Exception e) {
				_logger.error(e.getMessage(), e);
			}
		}

		KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
			KeyManagerFactory.getDefaultAlgorithm());

		keyManagerFactory.init(keyStore, "".toCharArray());

		TrustManagerFactory trustManagerFactory =
			TrustManagerFactory.getInstance(
				TrustManagerFactory.getDefaultAlgorithm());

		trustManagerFactory.init(keyStore);

		SSLContext sslContext = SSLContext.getInstance("TLS");

		sslContext.init(
			keyManagerFactory.getKeyManagers(),
			trustManagerFactory.getTrustManagers(), null);

		return new SSLConnectionSocketFactory(
			sslContext, new NoopHostnameVerifier()) {

			@Override
			public Socket createLayeredSocket(
					Socket socket, String target, int port,
					HttpContext httpContext)
				throws IOException {

				HttpClientContext httpClientContext =
					(HttpClientContext)httpContext;

				HttpRequest httpRequest = httpClientContext.getRequest();

				RequestLine requestLine = httpRequest.getRequestLine();

				String[] parts = StringUtils.split(requestLine.getUri(), "/");

				String sniCompliantLanServerUuid = LanClientUtil.getSNIHostname(
					parts[0]);

				return super.createLayeredSocket(
					socket, sniCompliantLanServerUuid, port, httpContext);
			}

		};
	}

	private static String _getUrl(
		SyncLanClient syncLanClient, SyncFile syncFile) {

		SyncAccount syncAccount = SyncAccountService.fetchSyncAccount(
			syncFile.getSyncAccountId());

		StringBuilder sb = new StringBuilder();

		sb.append("https://");
		sb.append(syncLanClient.getHostname());
		sb.append(":");
		sb.append(syncLanClient.getPort());
		sb.append("/");
		sb.append(syncAccount.getLanServerUuid());
		sb.append("/");
		sb.append(syncFile.getRepositoryId());
		sb.append("/");
		sb.append(syncFile.getTypePK());
		sb.append("/");
		sb.append(syncFile.getVersionId());

		return sb.toString();
	}

	private static final Logger _logger = LoggerFactory.getLogger(
		LanSession.class);

	private static LanSession _lanSession;
	private static ThreadPoolExecutor _queryExecutorService;

	private final ExecutorService _downloadExecutorService =
		Executors.newCachedThreadPool();
	private final HttpClient _downloadHttpClient;
	private final HttpClient _queryHttpClient;

}