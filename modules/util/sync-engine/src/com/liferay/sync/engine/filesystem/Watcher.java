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

package com.liferay.sync.engine.filesystem;

import com.liferay.sync.engine.SyncEngine;
import com.liferay.sync.engine.filesystem.listener.WatchEventListener;
import com.liferay.sync.engine.filesystem.util.WatcherRegistry;
import com.liferay.sync.engine.model.SyncAccount;
import com.liferay.sync.engine.model.SyncFile;
import com.liferay.sync.engine.model.SyncSite;
import com.liferay.sync.engine.model.SyncWatchEvent;
import com.liferay.sync.engine.service.SyncAccountService;
import com.liferay.sync.engine.service.SyncFileService;
import com.liferay.sync.engine.service.SyncSiteService;
import com.liferay.sync.engine.util.BidirectionalMap;
import com.liferay.sync.engine.util.FileUtil;
import com.liferay.sync.engine.util.OSDetector;

import java.io.IOException;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;

import name.pachler.nio.file.ClosedWatchServiceException;
import name.pachler.nio.file.FileSystem;
import name.pachler.nio.file.FileSystems;
import name.pachler.nio.file.Paths;
import name.pachler.nio.file.StandardWatchEventKind;
import name.pachler.nio.file.WatchEvent;
import name.pachler.nio.file.WatchKey;
import name.pachler.nio.file.WatchService;
import name.pachler.nio.file.ext.ExtendedWatchEventKind;
import name.pachler.nio.file.ext.ExtendedWatchEventModifier;
import name.pachler.nio.file.impl.PathImpl;

import org.apache.commons.io.FilenameUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Michael Young
 * @author Shinn Lok
 */
public class Watcher implements Runnable {

	public Watcher(Path filePath, WatchEventListener watchEventListener)
		throws IOException {

		_filePath = filePath;
		_watchEventListener = watchEventListener;

		if (OSDetector.isWindows()) {
			_recursive = false;
		}
		else {
			_recursive = true;
		}

		initWatchService();

		walkFileTree(_filePath);

		WatcherRegistry.register(_watchEventListener.getSyncAccountId(), this);
	}

	public void close() {
		WatcherRegistry.unregister(_watchEventListener.getSyncAccountId());

		try {
			_watchService.close();
		}
		catch (Exception e) {
		}
		finally {
			_watchService = null;
		}
	}

	public List<String> getDownloadedFilePathNames() {
		return _downloadedFilePathNames;
	}

	public void registerFilePath(Path filePath) throws IOException {
		if (Files.notExists(filePath)) {
			return;
		}

		if (!_recursive && (filePath != _filePath)) {
			return;
		}

		WatchKey watchKey = null;

		name.pachler.nio.file.Path jpathwatchFilePath = Paths.get(
			filePath.toString());

		if (OSDetector.isWindows()) {
			watchKey = jpathwatchFilePath.register(
				_watchService,
				new WatchEvent.Kind[] {
					ExtendedWatchEventKind.KEY_INVALID,
					StandardWatchEventKind.ENTRY_CREATE,
					StandardWatchEventKind.ENTRY_DELETE,
					StandardWatchEventKind.ENTRY_MODIFY
				},
				ExtendedWatchEventModifier.FILE_TREE);
		}
		else {
			watchKey = jpathwatchFilePath.register(
				_watchService, ExtendedWatchEventKind.KEY_INVALID,
				StandardWatchEventKind.ENTRY_CREATE,
				StandardWatchEventKind.ENTRY_DELETE,
				StandardWatchEventKind.ENTRY_MODIFY);
		}

		_filePaths.put(watchKey, filePath);

		if (_logger.isTraceEnabled()) {
			_logger.trace("Registered file path {}", filePath);
		}
	}

	@Override
	public void run() {
		while (true) {
			try {
				if (_watchService == null) {
					break;
				}

				WatchKey watchKey = null;

				try {
					watchKey = _watchService.take();
				}
				catch (ClosedWatchServiceException cwse) {
					if (!SyncEngine.isRunning()) {
						break;
					}

					_logger.error(cwse.getMessage(), cwse);

					continue;
				}
				catch (Exception e) {
					if (_logger.isTraceEnabled()) {
						_logger.trace(e.getMessage(), e);
					}

					continue;
				}

				Path parentFilePath = _filePaths.get(watchKey);

				if (parentFilePath == null) {
					continue;
				}

				List<WatchEvent<?>> watchEvents = watchKey.pollEvents();

				for (int i = 0; i < watchEvents.size(); i++) {
					WatchEvent<Path> watchEvent =
						(WatchEvent<Path>)watchEvents.get(i);

					PathImpl pathImpl = (PathImpl)watchEvent.context();

					if (pathImpl == null) {
						continue;
					}

					WatchEvent.Kind<?> kind = watchEvent.kind();

					Path childFilePath = parentFilePath.resolve(
						pathImpl.toString());

					processWatchEvent(kind.name(), childFilePath);
				}

				processFailedFilePaths();

				if (!watchKey.reset()) {
					Path filePath = _filePaths.remove(watchKey);

					if (_logger.isTraceEnabled()) {
						_logger.trace("Unregistered file path {}", filePath);
					}

					if (Files.notExists(filePath)) {
						processMissingFilePath(filePath);
					}

					if (_filePaths.isEmpty()) {
						break;
					}
				}
			}
			catch (Exception e) {
				_logger.error(e.getMessage(), e);
			}
		}
	}

	public void unregisterFilePath(Path filePath) {
		WatchKey watchKey = _filePaths.removeValue(filePath);

		if (watchKey == null) {
			return;
		}

		watchKey.cancel();

		if (_logger.isTraceEnabled()) {
			_logger.trace("Unregistered file path {}", filePath);
		}
	}

	protected void addCreatedFilePathName(String filePathName) {
		clearCreatedFilePathNames();

		long now = System.currentTimeMillis();

		while (_createdFilePathNames.putIfAbsent(now, filePathName) != null) {
			now++;
		}
	}

	protected void clearCreatedFilePathNames() {
		Map<Long, String> map = _createdFilePathNames.headMap(
			System.currentTimeMillis() - 5000);

		map.clear();
	}

	protected void fireWatchEventListener(String eventType, Path filePath) {
		_watchEventListener.watchEvent(eventType, filePath);
	}

	protected Path getFilePath() {
		return _filePath;
	}

	protected void initWatchService() {
		FileSystem fileSystem = FileSystems.getDefault();

		_watchService = fileSystem.newWatchService();
	}

	protected boolean isIgnoredFilePath(Path filePath) {
		if (Files.notExists(filePath)) {
			return true;
		}

		String fileName = String.valueOf(filePath.getFileName());

		if (FileUtil.isIgnoredFilePath(filePath) ||
			((Files.isDirectory(filePath) && (fileName.length() > 100)) ||
			 (!Files.isDirectory(filePath) && (fileName.length() > 255)))) {

			if (_logger.isDebugEnabled()) {
				_logger.debug("Ignored file path {}", filePath);
			}

			return true;
		}

		if (!OSDetector.isWindows()) {
			String sanitizedFileName = FileUtil.getSanitizedFileName(
				fileName, FilenameUtils.getExtension(fileName));

			if (!sanitizedFileName.equals(fileName)) {
				String sanitizedFilePathName = FileUtil.getFilePathName(
					String.valueOf(filePath.getParent()), sanitizedFileName);

				sanitizedFilePathName = FileUtil.getNextFilePathName(
					sanitizedFilePathName);

				FileUtil.moveFile(
					filePath, java.nio.file.Paths.get(sanitizedFilePathName));

				return true;
			}
		}

		return false;
	}

	protected void processFailedFilePaths() throws IOException {
		for (Path failedFilePath : _failedFilePaths) {
			if (Files.notExists(failedFilePath)) {
				_failedFilePaths.remove(failedFilePath);

				continue;
			}

			if (!Files.isReadable(failedFilePath)) {
				continue;
			}

			_failedFilePaths.remove(failedFilePath);

			if (Files.isDirectory(failedFilePath)) {
				registerFilePath(failedFilePath);
			}
			else {
				SyncFile syncFile = SyncFileService.fetchSyncFile(
					failedFilePath.toString());

				if (syncFile == null) {
					fireWatchEventListener(
						SyncWatchEvent.EVENT_TYPE_CREATE, failedFilePath);
				}
				else if (FileUtil.isModified(syncFile, failedFilePath)) {
					fireWatchEventListener(
						SyncWatchEvent.EVENT_TYPE_MODIFY, failedFilePath);
				}
			}
		}
	}

	protected void processMissingFilePath(Path missingFilePath) {
		SyncAccount syncAccount = SyncAccountService.fetchSyncAccount(
			_watchEventListener.getSyncAccountId());

		Path syncAccountFilePath = java.nio.file.Paths.get(
			syncAccount.getFilePathName());

		if (Files.notExists(syncAccountFilePath)) {
			syncAccount.setActive(false);
			syncAccount.setUiEvent(
				SyncAccount.UI_EVENT_SYNC_ACCOUNT_FOLDER_MISSING);

			SyncAccountService.update(syncAccount);
		}
		else {
			SyncSite syncSite = SyncSiteService.fetchSyncSite(
				missingFilePath.toString(), syncAccount.getSyncAccountId());

			if (syncSite != null) {
				syncSite.setActive(false);
				syncSite.setUiEvent(SyncSite.UI_EVENT_SYNC_SITE_FOLDER_MISSING);

				SyncSiteService.update(syncSite);
			}
		}
	}

	protected void processWatchEvent(String eventType, Path filePath)
		throws IOException {

		if (!_recursive && filePath.startsWith(_filePath.resolve(".data"))) {
			return;
		}

		if (eventType.equals(SyncWatchEvent.EVENT_TYPE_CREATE)) {
			if (isIgnoredFilePath(filePath)) {
				return;
			}

			addCreatedFilePathName(filePath.toString());

			if (_downloadedFilePathNames.remove(filePath.toString())) {
				return;
			}

			fireWatchEventListener(eventType, filePath);

			if (Files.isDirectory(filePath)) {
				walkFileTree(filePath);
			}
		}
		else if (eventType.equals(SyncWatchEvent.EVENT_TYPE_DELETE)) {
			processMissingFilePath(filePath);

			fireWatchEventListener(SyncWatchEvent.EVENT_TYPE_DELETE, filePath);
		}
		else if (eventType.equals(SyncWatchEvent.EVENT_TYPE_MODIFY)) {
			if (_downloadedFilePathNames.remove(filePath.toString()) ||
				(removeCreatedFilePathName(filePath.toString()) &&
				 !FileUtil.isValidChecksum(filePath)) ||
				Files.isDirectory(filePath)) {

				return;
			}

			fireWatchEventListener(SyncWatchEvent.EVENT_TYPE_MODIFY, filePath);
		}
	}

	protected boolean removeCreatedFilePathName(String filePathName) {
		clearCreatedFilePathNames();

		Collection<String> filePathNames = _createdFilePathNames.values();

		return filePathNames.remove(filePathName);
	}

	protected void walkFileTree(Path filePath) throws IOException {
		if (isIgnoredFilePath(filePath)) {
			return;
		}

		Files.walkFileTree(
			filePath,
			new SimpleFileVisitor<Path>() {

				@Override
				public FileVisitResult postVisitDirectory(
						Path filePath, IOException ioe)
					throws IOException {

					if (ioe != null) {
						_failedFilePaths.add(filePath);

						return FileVisitResult.CONTINUE;
					}

					return super.postVisitDirectory(filePath, null);
				}

				@Override
				public FileVisitResult preVisitDirectory(
						Path filePath, BasicFileAttributes basicFileAttributes)
					throws IOException {

					if (filePath.equals(_filePath.resolve(".data"))) {
						return FileVisitResult.SKIP_SUBTREE;
					}

					SyncFile syncFile = SyncFileService.fetchSyncFile(
						filePath.toString());

					if (syncFile == null) {
						fireWatchEventListener(
							SyncWatchEvent.EVENT_TYPE_CREATE, filePath);
					}

					try {
						registerFilePath(filePath);
					}
					catch (IOException ioe) {
						_logger.error(ioe.getMessage(), ioe);
					}

					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(
						Path filePath, BasicFileAttributes basicFileAttributes)
					throws IOException {

					if (Files.notExists(filePath) ||
						isIgnoredFilePath(filePath)) {

						return FileVisitResult.CONTINUE;
					}

					SyncFile syncFile = SyncFileService.fetchSyncFile(
						filePath.toString());

					if (syncFile == null) {
						fireWatchEventListener(
							SyncWatchEvent.EVENT_TYPE_CREATE, filePath);
					}
					else if (FileUtil.isModified(syncFile, filePath)) {
						fireWatchEventListener(
							SyncWatchEvent.EVENT_TYPE_MODIFY, filePath);
					}

					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(
						Path filePath, IOException ioe)
					throws IOException {

					_failedFilePaths.add(filePath);

					return FileVisitResult.CONTINUE;
				}

			}
		);
	}

	private static final Logger _logger = LoggerFactory.getLogger(
		Watcher.class);

	private final ConcurrentNavigableMap<Long, String> _createdFilePathNames =
		new ConcurrentSkipListMap<>();
	private final List<String> _downloadedFilePathNames = new ArrayList<>();
	private final List<Path> _failedFilePaths = new CopyOnWriteArrayList<>();
	private final Path _filePath;
	private final BidirectionalMap<WatchKey, Path> _filePaths =
		new BidirectionalMap<>();
	private final boolean _recursive;
	private final WatchEventListener _watchEventListener;
	private WatchService _watchService;

}