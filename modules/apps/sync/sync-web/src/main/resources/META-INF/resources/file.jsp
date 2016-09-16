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

<%@ include file="/init.jsp" %>

<%
long groupId = ParamUtil.getLong(request, "groupId");
long folderId = ParamUtil.getLong(request, "folderId");
long fileEntryId = ParamUtil.getLong(request, "fileEntryId");
long fileEntryTypeId = ParamUtil.getLong(request, "fileEntryTypeId");

long assetClassPK = 0;
DLFileVersion fileVersion = null;
long fileVersionId = 0;

DLFileEntry fileEntry = null;

if (fileEntryId > 0) {
	fileEntry = DLFileEntryServiceUtil.getFileEntry(fileEntryId);
}

if (fileEntry != null) {
	assetClassPK = fileEntry.getFileEntryId();
	fileVersion = fileEntry.getLatestFileVersion(false);

	fileVersionId = fileVersion.getFileVersionId();

	fileEntryTypeId = ParamUtil.getLong(request, "fileEntryTypeId", fileEntry.getFileEntryTypeId());
}

if ((fileVersion != null) && !fileVersion.isApproved() && Validator.isNotNull(fileVersion.getVersion()) && !fileVersion.getVersion().equals(DLFileEntryConstants.VERSION_DEFAULT)) {
	assetClassPK = fileVersion.getFileVersionId();
}
%>

<liferay-portlet:actionURL name="updateSyncFile" var="updateSyncFileURL" />

<aui:form action="<%= updateSyncFileURL %>" cssClass="lfr-dynamic-form" enctype="multipart/form-data" method="post" name="fm">
	<aui:input name="redirect" type="hidden" value="<%= currentURL %>" />
	<aui:input name="fileEntryId" type="hidden" value="<%= fileEntryId %>" />

	<aui:model-context bean="<%= fileEntry %>" model="<%= DLFileEntry.class %>" />

	<aui:fieldset-group markupView="lexicon">
		<aui:fieldset collapsed="<%= false %>" collapsible="<%= true %>" label="details">
			<aui:input disabled="<%= true %>" name="title" />

			<aui:input name="description" />
		</aui:fieldset>

		<%
		List<DLFileEntryType> fileEntryTypes = SyncUtil.getFileEntryTypes(groupId, folderId);
		%>

		<aui:fieldset collapsed="<%= true %>" collapsible="<%= true %>" label="document-type">
			<c:choose>
				<c:when test="<%= (fileEntryTypes != null) && (fileEntryTypes.size() >= 1) %>">
					<aui:select label="type" name="fileEntryTypeId">

						<%
						for (DLFileEntryType curDLFileEntryType : fileEntryTypes) {
							if ((curDLFileEntryType.getFileEntryTypeId() == DLFileEntryTypeConstants.FILE_ENTRY_TYPE_ID_BASIC_DOCUMENT) || (fileEntryTypeId == curDLFileEntryType.getFileEntryTypeId()) || DLFileEntryTypePermission.contains(permissionChecker, curDLFileEntryType, ActionKeys.VIEW)) {
						%>

								<aui:option label="<%= HtmlUtil.escape(curDLFileEntryType.getName(locale)) %>" selected="<%= (fileEntryTypeId == curDLFileEntryType.getPrimaryKey()) %>" value="<%= curDLFileEntryType.getPrimaryKey() %>" />

						<%
							}
						}
						%>

					</aui:select>
				</c:when>
				<c:otherwise>
					<aui:input name="fileEntryTypeId" type="hidden" value="<%= fileEntryTypeId %>" />
				</c:otherwise>
			</c:choose>

			<%
			if (fileEntryTypeId > 0) {
				DLFileEntryType fileEntryType = DLFileEntryTypeServiceUtil.getFileEntryType(fileEntryTypeId);

				List<DDMStructure> ddmStructures = fileEntryType.getDDMStructures();

				for (DDMStructure ddmStructure : ddmStructures) {
					DDMFormValues ddmFormValues = null;

					DLFileEntryMetadata fileEntryMetadata = DLFileEntryMetadataLocalServiceUtil.fetchFileEntryMetadata(ddmStructure.getStructureId(), fileVersionId);

					if (fileEntryMetadata != null) {
						StorageEngine storageEngine = (StorageEngine)request.getAttribute(SyncWebKeys.STORAGE_ENGINE);

						ddmFormValues = storageEngine.getDDMFormValues(fileEntryMetadata.getDDMStorageId());
					}
			%>

					<div class="document-type-fields">
						<liferay-ddm:html
							classNameId="<%= PortalUtil.getClassNameId(com.liferay.dynamic.data.mapping.model.DDMStructure.class) %>"
							classPK="<%= ddmStructure.getPrimaryKey() %>"
							ddmFormValues="<%= ddmFormValues %>"
							fieldsNamespace="<%= String.valueOf(ddmStructure.getPrimaryKey()) %>"
							requestedLocale="<%= locale %>"
						/>
					</div>

			<%
				}
			}
			%>

		</aui:fieldset>

		<aui:fieldset collapsed="<%= true %>" collapsible="<%= true %>" label="categorization">
			<liferay-asset:asset-categories-selector className="<%= DLFileEntry.class.getName() %>" classPK="<%= assetClassPK %>" classTypePK="<%= fileEntryTypeId %>" />

			<liferay-asset:asset-tags-selector className="<%= DLFileEntry.class.getName() %>" classPK="<%= assetClassPK %>" />
		</aui:fieldset>

		<aui:fieldset collapsed="<%= true %>" collapsible="<%= true %>" label="related-assets">
			<liferay-ui:input-asset-links
				className="<%= DLFileEntry.class.getName() %>"
				classPK="<%= assetClassPK %>"
			/>
		</aui:fieldset>
	</aui:fieldset-group>

	<aui:button-row>
		<aui:button type="submit" />
	</aui:button-row>
</aui:form>

<aui:script>
	var form = $('#<portlet:namespace />fm');

	var assetTagNames = form.fm('assetTagNames');
	var description = form.fm('description');
	var fileEntryTypeId = form.fm('fileEntryTypeId');

	fileEntryTypeId.on(
		'change',
		function() {
			var data = {
				assetTagNames: assetTagNames.val(),
				description: description.val(),
				fileEntryTypeId: fileEntryTypeId.val()

				<%
				long[] groupIds = PortalUtil.getCurrentAndAncestorSiteGroupIds(scopeGroupId);

				for (AssetVocabulary vocabulary : AssetVocabularyServiceUtil.getGroupVocabularies(groupIds)) {
					String assetCategoryKey = "assetCategoryIds_" + vocabulary.getVocabularyId();
				%>

					, <%= assetCategoryKey %>: form.fm('<%= assetCategoryKey %>').val()

				<%
				}
				%>

			};

			// LPS-67638

			Liferay.Portlet.destroy('#p_p_id<portlet:namespace />');

			Liferay.Portlet.refresh('#p_p_id<portlet:namespace />', Liferay.Util.ns('<portlet:namespace />', data));
		})
</aui:script>