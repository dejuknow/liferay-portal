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

package com.liferay.sync.internal.model.listener;

import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.model.DLFolder;
import com.liferay.portal.kernel.dao.orm.ActionableDynamicQuery;
import com.liferay.portal.kernel.dao.orm.Disjunction;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.Property;
import com.liferay.portal.kernel.dao.orm.PropertyFactoryUtil;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil;
import com.liferay.portal.kernel.exception.ModelListenerException;
import com.liferay.portal.kernel.model.ModelListener;
import com.liferay.portal.kernel.model.ResourcePermission;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.sync.constants.SyncDeviceConstants;
import com.liferay.sync.model.SyncDLObject;
import com.liferay.sync.model.SyncDevice;
import com.liferay.sync.service.SyncDeviceLocalService;

import java.util.Date;
import java.util.List;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Jonathan McCann
 */
@Component(immediate = true, service = ModelListener.class)
public class UserModelListener extends SyncBaseModelListener<User> {

	@Override
	public void onAfterRemove(User user) throws ModelListenerException {
		try {
			List<SyncDevice> syncDevices =
				_syncDeviceLocalService.getSyncDevices(
					user.getUserId(), QueryUtil.ALL_POS, QueryUtil.ALL_POS,
					null);

			for (SyncDevice syncDevice : syncDevices) {
				_syncDeviceLocalService.deleteSyncDevice(syncDevice);
			}
		}
		catch (Exception e) {
			throw new ModelListenerException(e);
		}
	}

	@Override
	public void onBeforeAddAssociation(
			Object classPK, String associationClassName,
			Object associationClassPK)
		throws ModelListenerException {

		if (!associationClassName.equals(Role.class.getName())) {
			return;
		}

		ActionableDynamicQuery actionableDynamicQuery =
			resourcePermissionLocalService.getActionableDynamicQuery();

		actionableDynamicQuery.setAddCriteriaMethod(
			new ActionableDynamicQuery.AddCriteriaMethod() {

				@Override
				public void addCriteria(DynamicQuery dynamicQuery) {
					Property nameProperty = PropertyFactoryUtil.forName("name");
					Property roleIdProperty = PropertyFactoryUtil.forName(
						"roleId");
					Property viewActionIdProperty = PropertyFactoryUtil.forName(
						"viewActionId");

					Disjunction disjunction =
						RestrictionsFactoryUtil.disjunction();

					disjunction.add(
						nameProperty.eq(DLFileEntry.class.getName()));
					disjunction.add(nameProperty.eq(DLFolder.class.getName()));

					dynamicQuery.add(disjunction);

					dynamicQuery.add(roleIdProperty.eq(associationClassPK));
					dynamicQuery.add(viewActionIdProperty.eq(true));
				}

			});
		actionableDynamicQuery.setPerformActionMethod(
			new ActionableDynamicQuery.
				PerformActionMethod<ResourcePermission>() {

				@Override
				public void performAction(
					ResourcePermission resourcePermission) {

					SyncDLObject syncDLObject = getSyncDLObject(
						resourcePermission);

					if (syncDLObject == null) {
						return;
					}

					updateSyncDLObject(syncDLObject);
				}

			});

		try {
			actionableDynamicQuery.performActions();
		}
		catch (Exception e) {
			throw new ModelListenerException(e);
		}
	}

	@Override
	public void onBeforeRemoveAssociation(
			Object classPK, String associationClassName,
			Object associationClassPK)
		throws ModelListenerException {

		if (!associationClassName.equals(Role.class.getName())) {
			return;
		}

		ActionableDynamicQuery actionableDynamicQuery =
			resourcePermissionLocalService.getActionableDynamicQuery();

		actionableDynamicQuery.setAddCriteriaMethod(
			new ActionableDynamicQuery.AddCriteriaMethod() {

				@Override
				public void addCriteria(DynamicQuery dynamicQuery) {
					Property nameProperty = PropertyFactoryUtil.forName("name");
					Property roleIdProperty = PropertyFactoryUtil.forName(
						"roleId");
					Property viewActionIdProperty = PropertyFactoryUtil.forName(
						"viewActionId");

					Disjunction disjunction =
						RestrictionsFactoryUtil.disjunction();

					disjunction.add(
						nameProperty.eq(DLFileEntry.class.getName()));
					disjunction.add(nameProperty.eq(DLFolder.class.getName()));

					dynamicQuery.add(disjunction);

					dynamicQuery.add(roleIdProperty.eq(associationClassPK));
					dynamicQuery.add(viewActionIdProperty.eq(true));
				}

			});
		actionableDynamicQuery.setPerformActionMethod(
			new ActionableDynamicQuery.
				PerformActionMethod<ResourcePermission>() {

				@Override
				public void performAction(
					ResourcePermission resourcePermission) {

					SyncDLObject syncDLObject = getSyncDLObject(
						resourcePermission);

					if (syncDLObject == null) {
						return;
					}

					Date date = new Date();

					syncDLObject.setModifiedTime(date.getTime());
					syncDLObject.setLastPermissionChangeDate(date);

					syncDLObjectLocalService.updateSyncDLObject(syncDLObject);
				}

			});

		try {
			actionableDynamicQuery.performActions();
		}
		catch (Exception e) {
			throw new ModelListenerException(e);
		}
	}

	@Override
	public void onBeforeUpdate(User user) throws ModelListenerException {
		try {
			User originalUser = _userLocalService.getUser(user.getUserId());

			if (originalUser.isActive() && !user.isActive()) {
				List<SyncDevice> syncDevices =
					_syncDeviceLocalService.getSyncDevices(
						user.getUserId(), QueryUtil.ALL_POS, QueryUtil.ALL_POS,
						null);

				for (SyncDevice syncDevice : syncDevices) {
					_syncDeviceLocalService.updateStatus(
						syncDevice.getSyncDeviceId(),
						SyncDeviceConstants.STATUS_INACTIVE);
				}
			}
			else if (!originalUser.isActive() && user.isActive()) {
				List<SyncDevice> syncDevices =
					_syncDeviceLocalService.getSyncDevices(
						user.getUserId(), QueryUtil.ALL_POS, QueryUtil.ALL_POS,
						null);

				for (SyncDevice syncDevice : syncDevices) {
					_syncDeviceLocalService.updateStatus(
						syncDevice.getSyncDeviceId(),
						SyncDeviceConstants.STATUS_ACTIVE);
				}
			}
		}
		catch (Exception e) {
			throw new ModelListenerException(e);
		}
	}

	@Reference
	private SyncDeviceLocalService _syncDeviceLocalService;

	@Reference
	private UserLocalService _userLocalService;

}