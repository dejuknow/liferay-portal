/**
 * Copyright (c) 2000-2011 Liferay, Inc. All rights reserved.
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

package com.liferay.portal.kernel.spring.context;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.servlet.PortalClassLoaderServletContextListener;
import com.liferay.portal.kernel.util.ContextPathUtil;
import com.liferay.portal.kernel.util.PortalClassInvoker;
import com.liferay.portal.kernel.util.PortalClassLoaderUtil;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextListener;

/**
 * @author Brian Wing Shun Chan
 */
public class PortletContextLoaderListener
	extends PortalClassLoaderServletContextListener {

	public static String getLockKey(ServletContext servletContext) {
		String contextPath = ContextPathUtil.getContextPath(servletContext);

		return getLockKey(contextPath);
	}

	public static String getLockKey(String contextPath) {
		try {
			Object returnValue = PortalClassInvoker.invoke(
				false, _CLASS_NAME, "getLockKey",
				new String[] {String.class.getName()}, contextPath);

			return String.valueOf(returnValue);
		}
		catch (Exception e) {
			_log.error(e, e);

			throw new IllegalStateException("Unable to get lock key", e);
		}
	}

	@Override
	protected ServletContextListener getInstance() throws Exception {
		Class<?> clazz = Class.forName(
			_CLASS_NAME, true, PortalClassLoaderUtil.getClassLoader());

		return (ServletContextListener)clazz.newInstance();
	}

	private static final String _CLASS_NAME =
		"com.liferay.portal.spring.context.PortletContextLoaderListener";

	private static Log _log = LogFactoryUtil.getLog(
		PortletContextLoaderListener.class.getName());

}