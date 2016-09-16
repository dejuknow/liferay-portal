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

package com.liferay.sync.internal.servlet;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.util.HttpUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.WebKeys;

import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.osgi.service.component.annotations.Component;

/**
 * @author Shinn Lok
 */
@Component(
	immediate = true,
	property = {
		"osgi.http.whiteboard.context.path=/sync",
		"osgi.http.whiteboard.servlet.name=com.liferay.sync.internal.servlet.SyncRedirectServlet",
		"osgi.http.whiteboard.servlet.pattern=/sync/redirect/*"
	},
	service = Servlet.class
)
public class SyncRedirectServlet extends HttpServlet {

	@Override
	public void service(
			HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

		try {
			String pathInfo = HttpUtil.fixPath(request.getPathInfo());

			if (!isValidRedirect(pathInfo)) {
				return;
			}

			StringBundler sb = new StringBundler(6);

			sb.append(PortalUtil.getPortalURL(request));
			sb.append(PortalUtil.getPathMain());
			sb.append(StringPool.FORWARD_SLASH);

			String requestURL = String.valueOf(request.getRequestURL());

			int index = requestURL.indexOf(pathInfo);

			sb.append(requestURL.substring(index));

			String queryString = request.getQueryString();

			if (queryString != null) {
				sb.append(StringPool.QUESTION);
				sb.append(queryString);
			}

			String redirect = sb.toString();

			request = PortalUtil.getOriginalServletRequest(request);

			User user = PortalUtil.getUser(request);

			if (user.isDefaultUser()) {
				sb.setIndex(0);

				sb.append(PortalUtil.getPortalURL(request));
				sb.append(PortalUtil.getPathMain());
				sb.append("/portal/login?redirect=");
				sb.append(HttpUtil.encodeURL(redirect));

				redirect = sb.toString();
			}
			else {
				HttpSession session = request.getSession();

				session.setAttribute(WebKeys.USER_ID, user.getUserId());
			}

			response.sendRedirect(redirect);
		}
		catch (Exception e) {
			_log.error(e, e);

			PortalUtil.sendError(
				HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e, request,
				response);
		}
	}

	protected boolean isValidRedirect(String path) {
		if (path.equals("document_library/find_folder") ||
			path.equals("document_library/find_file_entry") ||
			path.equals("sync/find_sync_file")) {

			return true;
		}

		return false;
	}

	private static final Log _log = LogFactoryUtil.getLog(
		SyncRedirectServlet.class);

}