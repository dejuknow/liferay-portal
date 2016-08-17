<%--
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
--%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<%@ taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet" %>

<%@ taglib uri="http://liferay.com/tld/asset" prefix="liferay-asset" %><%@
taglib uri="http://liferay.com/tld/aui" prefix="aui" %><%@
taglib uri="http://liferay.com/tld/ddm" prefix="liferay-ddm" %><%@
taglib uri="http://liferay.com/tld/frontend" prefix="liferay-frontend" %><%@
taglib uri="http://liferay.com/tld/portlet" prefix="liferay-portlet" %><%@
taglib uri="http://liferay.com/tld/theme" prefix="liferay-theme" %><%@
taglib uri="http://liferay.com/tld/ui" prefix="liferay-ui" %><%@
taglib uri="http://liferay.com/tld/util" prefix="liferay-util" %>

<%@ page import="com.liferay.asset.kernel.model.AssetVocabulary" %><%@
page import="com.liferay.asset.kernel.service.AssetVocabularyServiceUtil" %><%@
page import="com.liferay.document.library.kernel.model.DLFileEntry" %><%@
page import="com.liferay.document.library.kernel.model.DLFileEntryConstants" %><%@
page import="com.liferay.document.library.kernel.model.DLFileEntryMetadata" %><%@
page import="com.liferay.document.library.kernel.model.DLFileEntryType" %><%@
page import="com.liferay.document.library.kernel.model.DLFileEntryTypeConstants" %><%@
page import="com.liferay.document.library.kernel.model.DLFileVersion" %><%@
page import="com.liferay.document.library.kernel.service.DLFileEntryMetadataLocalServiceUtil" %><%@
page import="com.liferay.document.library.kernel.service.DLFileEntryServiceUtil" %><%@
page import="com.liferay.document.library.kernel.service.DLFileEntryTypeServiceUtil" %><%@
page import="com.liferay.dynamic.data.mapping.kernel.DDMStructure" %><%@
page import="com.liferay.dynamic.data.mapping.storage.DDMFormValues" %><%@
page import="com.liferay.dynamic.data.mapping.storage.StorageEngine" %><%@
page import="com.liferay.ip.geocoder.IPGeocoder" %><%@
page import="com.liferay.ip.geocoder.IPInfo" %><%@
page import="com.liferay.portal.kernel.dao.orm.QueryUtil" %><%@
page import="com.liferay.portal.kernel.dao.search.ResultRow" %><%@
page import="com.liferay.portal.kernel.dao.search.RowChecker" %><%@
page import="com.liferay.portal.kernel.dao.search.SearchContainer" %><%@
page import="com.liferay.portal.kernel.language.LanguageUtil" %><%@
page import="com.liferay.portal.kernel.language.UnicodeLanguageUtil" %><%@
page import="com.liferay.portal.kernel.model.Group" %><%@
page import="com.liferay.portal.kernel.portlet.LiferayWindowState" %><%@
page import="com.liferay.portal.kernel.portlet.PortletURLUtil" %><%@
page import="com.liferay.portal.kernel.security.permission.ActionKeys" %><%@
page import="com.liferay.portal.kernel.security.permission.ResourceActionsUtil" %><%@
page import="com.liferay.portal.kernel.service.GroupLocalServiceUtil" %><%@
page import="com.liferay.portal.kernel.util.GetterUtil" %><%@
page import="com.liferay.portal.kernel.util.HtmlUtil" %><%@
page import="com.liferay.portal.kernel.util.ListUtil" %><%@
page import="com.liferay.portal.kernel.util.OrderByComparator" %><%@
page import="com.liferay.portal.kernel.util.OrderByComparatorFactoryUtil" %><%@
page import="com.liferay.portal.kernel.util.ParamUtil" %><%@
page import="com.liferay.portal.kernel.util.PortalUtil" %><%@
page import="com.liferay.portal.kernel.util.PrefsPropsUtil" %><%@
page import="com.liferay.portal.kernel.util.StringPool" %><%@
page import="com.liferay.portal.kernel.util.StringUtil" %><%@
page import="com.liferay.portal.kernel.util.Validator" %><%@
page import="com.liferay.portal.kernel.util.WebKeys" %><%@
page import="com.liferay.portlet.documentlibrary.service.permission.DLFileEntryTypePermission" %><%@
page import="com.liferay.sync.constants.SyncAdminPortletKeys" %><%@
page import="com.liferay.sync.constants.SyncConstants" %><%@
page import="com.liferay.sync.constants.SyncDeviceConstants" %><%@
page import="com.liferay.sync.constants.SyncPermissionsConstants" %><%@
page import="com.liferay.sync.exception.OAuthPortletUndeployedException" %><%@
page import="com.liferay.sync.model.SyncDevice" %><%@
page import="com.liferay.sync.oauth.helper.SyncOAuthHelperUtil" %><%@
page import="com.liferay.sync.service.SyncDeviceLocalServiceUtil" %><%@
page import="com.liferay.sync.service.configuration.SyncServiceConfigurationKeys" %><%@
page import="com.liferay.sync.service.configuration.SyncServiceConfigurationValues" %><%@
page import="com.liferay.sync.util.SyncUtil" %><%@
page import="com.liferay.sync.web.internal.constants.SyncWebKeys" %>

<%@ page import="java.util.ArrayList" %><%@
page import="java.util.LinkedHashMap" %><%@
page import="java.util.List" %>

<%@ page import="javax.portlet.PortletURL" %>

<liferay-frontend:defineObjects />

<liferay-theme:defineObjects />

<portlet:defineObjects />