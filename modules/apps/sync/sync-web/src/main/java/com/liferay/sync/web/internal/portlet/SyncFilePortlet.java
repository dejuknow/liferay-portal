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

package com.liferay.sync.web.internal.portlet;

import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.service.DLAppService;
import com.liferay.dynamic.data.mapping.storage.StorageEngine;
import com.liferay.portal.kernel.portlet.PortletURLFactoryUtil;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextFactory;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.sync.constants.SyncPortletKeys;
import com.liferay.sync.service.SyncDLObjectService;
import com.liferay.sync.web.internal.constants.SyncWebKeys;

import java.io.IOException;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.Portlet;
import javax.portlet.PortletException;
import javax.portlet.PortletRequest;
import javax.portlet.PortletURL;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;

import javax.servlet.http.HttpServletRequest;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Shinn Lok
 */
@Component(
	immediate = true,
	property = {
		"com.liferay.portlet.add-default-resource=true",
		"com.liferay.portlet.css-class-wrapper=portlet-sync-file",
		"com.liferay.portlet.display-category=category.hidden",
		"com.liferay.portlet.preferences-owned-by-group=true",
		"com.liferay.portlet.private-request-attributes=false",
		"com.liferay.portlet.private-session-attributes=false",
		"com.liferay.portlet.render-weight=50",
		"com.liferay.portlet.struts-path=sync",
		"com.liferay.portlet.use-default-template=true",
		"javax.portlet.display-name=Sync File",
		"javax.portlet.expiration-cache=0",
		"javax.portlet.init-param.template-path=/",
		"javax.portlet.init-param.view-template=/file.jsp",
		"javax.portlet.name=" + SyncPortletKeys.SYNC_FILE_PORTLET,
		"javax.portlet.resource-bundle=content.Language",
		"javax.portlet.security-role-ref=guest,power-user,user",
		"javax.portlet.supports.mime-type=text/html",
		"portlet.add.default.resource.check.whitelist=" + SyncPortletKeys.SYNC_FILE_PORTLET
	},
	service = Portlet.class
)
public class SyncFilePortlet extends MVCPortlet {

	@Override
	public void render(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws IOException, PortletException {

		HttpServletRequest request = _portal.getHttpServletRequest(
			renderRequest);

		request.setAttribute(SyncWebKeys.STORAGE_ENGINE, _storageEngine);

		super.render(renderRequest, renderResponse);
	}

	public void updateSyncFile(
			ActionRequest actionRequest, ActionResponse actionResponse)
		throws Exception {

		long fileEntryId = ParamUtil.getLong(actionRequest, "fileEntryId");
		String description = ParamUtil.getString(actionRequest, "description");

		ServiceContext serviceContext = ServiceContextFactory.getInstance(
			DLFileEntry.class.getName(), actionRequest);

		FileEntry fileEntry = _dlAppService.getFileEntry(fileEntryId);

		_syncDLObjectService.updateFileEntry(
			fileEntryId, fileEntry.getFileName(), fileEntry.getMimeType(),
			fileEntry.getTitle(), description, null, false, null, null,
			serviceContext);

		actionRequest.setAttribute(
			WebKeys.REDIRECT, getRedirect(actionRequest, fileEntry));
	}

	protected String getRedirect(
			ActionRequest actionRequest, FileEntry fileEntry)
		throws Exception {

		PortletURL portletURL = PortletURLFactoryUtil.create(
			actionRequest, SyncPortletKeys.SYNC_FILE_PORTLET,
			PortletRequest.RENDER_PHASE);

		portletURL.setParameter("mvcPath", "/file.jsp");
		portletURL.setParameter(
			"groupId", String.valueOf(fileEntry.getGroupId()));
		portletURL.setParameter(
			"folderId", String.valueOf(fileEntry.getFolderId()));
		portletURL.setParameter(
			"fileEntryId", String.valueOf(fileEntry.getFileEntryId()));
		portletURL.setWindowState(actionRequest.getWindowState());

		return portletURL.toString();
	}

	@Reference(unbind = "-")
	protected void setDLAppService(DLAppService dlAppService) {
		_dlAppService = dlAppService;
	}

	@Reference(unbind = "-")
	protected void setPortal(Portal portal) {
		_portal = portal;
	}

	@Reference(unbind = "-")
	protected void setStorageEngine(StorageEngine storageEngine) {
		_storageEngine = storageEngine;
	}

	@Reference(unbind = "-")
	protected void setSyncDLObjectService(
		SyncDLObjectService syncDLObjectService) {

		_syncDLObjectService = syncDLObjectService;
	}

	private DLAppService _dlAppService;
	private Portal _portal;
	private StorageEngine _storageEngine;
	private SyncDLObjectService _syncDLObjectService;

}