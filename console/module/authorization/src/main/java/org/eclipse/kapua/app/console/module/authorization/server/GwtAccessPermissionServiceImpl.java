/*******************************************************************************
 * Copyright (c) 2017, 2018 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech - initial API and implementation
 *******************************************************************************/
package org.eclipse.kapua.app.console.module.authorization.server;

import com.extjs.gxt.ui.client.Style.SortDir;
import com.extjs.gxt.ui.client.data.BasePagingLoadResult;
import com.extjs.gxt.ui.client.data.PagingLoadConfig;
import com.extjs.gxt.ui.client.data.PagingLoadResult;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.kapua.app.console.module.api.client.GwtKapuaException;
import org.eclipse.kapua.app.console.module.api.server.KapuaRemoteServiceServlet;
import org.eclipse.kapua.app.console.module.api.server.util.KapuaExceptionHandler;
import org.eclipse.kapua.app.console.module.api.shared.model.GwtXSRFToken;
import org.eclipse.kapua.app.console.module.api.shared.util.GwtKapuaCommonsModelConverter;
import org.eclipse.kapua.app.console.module.authorization.shared.model.GwtAccessPermission;
import org.eclipse.kapua.app.console.module.authorization.shared.model.GwtAccessPermissionCreator;
import org.eclipse.kapua.app.console.module.authorization.shared.service.GwtAccessPermissionService;
import org.eclipse.kapua.app.console.module.authorization.shared.util.GwtKapuaAuthorizationModelConverter;
import org.eclipse.kapua.app.console.module.authorization.shared.util.KapuaGwtAuthorizationModelConverter;
import org.eclipse.kapua.commons.model.query.FieldSortCriteria;
import org.eclipse.kapua.commons.model.query.FieldSortCriteria.SortOrder;
import org.eclipse.kapua.commons.model.query.predicate.AttributePredicateImpl;
import org.eclipse.kapua.commons.security.KapuaSecurityUtils;
import org.eclipse.kapua.locator.KapuaLocator;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.authorization.access.AccessInfo;
import org.eclipse.kapua.service.authorization.access.AccessInfoService;
import org.eclipse.kapua.service.authorization.access.AccessPermission;
import org.eclipse.kapua.service.authorization.access.AccessPermissionCreator;
import org.eclipse.kapua.service.authorization.access.AccessPermissionFactory;
import org.eclipse.kapua.service.authorization.access.AccessPermissionListResult;
import org.eclipse.kapua.service.authorization.access.AccessPermissionPredicates;
import org.eclipse.kapua.service.authorization.access.AccessPermissionQuery;
import org.eclipse.kapua.service.authorization.access.AccessPermissionService;
import org.eclipse.kapua.service.user.User;
import org.eclipse.kapua.service.user.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public class GwtAccessPermissionServiceImpl extends KapuaRemoteServiceServlet implements GwtAccessPermissionService {

    private static final long serialVersionUID = 3606053200278262228L;

    @Override
    public GwtAccessPermission create(GwtXSRFToken xsrfToken, GwtAccessPermissionCreator gwtAccessPermissionCreator) throws GwtKapuaException {

        //
        // Checking XSRF token
        checkXSRFToken(xsrfToken);

        //
        // Do create
        GwtAccessPermission gwtAccessPermission = null;
        try {
            // Convert from GWT Entity
            AccessPermissionCreator accessPermissionCreator = GwtKapuaAuthorizationModelConverter.convertAccessPermissionCreator(gwtAccessPermissionCreator);

            // Create
            KapuaLocator locator = KapuaLocator.getInstance();
            AccessPermissionService accessPermissionService = locator.getService(AccessPermissionService.class);
            AccessPermission accessPermission = accessPermissionService.create(accessPermissionCreator);

            // Convert
            gwtAccessPermission = KapuaGwtAuthorizationModelConverter.convertAccessPermission(accessPermission);

        } catch (Throwable t) {
            KapuaExceptionHandler.handle(t);
        }

        //
        // Return result
        return gwtAccessPermission;
    }

    @Override
    public void delete(GwtXSRFToken gwtXsrfToken, String scopeShortId, String accessPermissionShortId) throws GwtKapuaException {

        //
        // Checking XSRF token
        checkXSRFToken(gwtXsrfToken);

        //
        // Do delete
        try {
            // Convert from GWT Entity
            KapuaId scopeId = GwtKapuaCommonsModelConverter.convertKapuaId(scopeShortId);
            KapuaId accessPermissionId = GwtKapuaCommonsModelConverter.convertKapuaId(accessPermissionShortId);

            // Delete
            KapuaLocator locator = KapuaLocator.getInstance();
            AccessPermissionService accessPermissionService = locator.getService(AccessPermissionService.class);
            accessPermissionService.delete(scopeId, accessPermissionId);
        } catch (Throwable t) {
            KapuaExceptionHandler.handle(t);
        }
    }

    @Override
    public PagingLoadResult<GwtAccessPermission> findByUserId(PagingLoadConfig loadConfig, String scopeShortId, String userShortId) throws GwtKapuaException {
        //
        // Do get
        int totalLength = 0;
        List<GwtAccessPermission> gwtAccessPermissions = new ArrayList<GwtAccessPermission>();
        if (userShortId != null) {

            try {
                KapuaLocator locator = KapuaLocator.getInstance();
                AccessInfoService accessInfoService = locator.getService(AccessInfoService.class);
                AccessPermissionService accessPermissionService = locator.getService(AccessPermissionService.class);
                AccessPermissionFactory accessPermissionFactory = locator.getFactory(AccessPermissionFactory.class);
                final UserService userService = locator.getService(UserService.class);
                final KapuaId scopeId = GwtKapuaCommonsModelConverter.convertKapuaId(scopeShortId);
                KapuaId userId = GwtKapuaCommonsModelConverter.convertKapuaId(userShortId);

                AccessInfo accessInfo = accessInfoService.findByUserId(scopeId, userId);
                final User user = userService.find(scopeId, userId);

                if (accessInfo != null) {
                    AccessPermissionQuery accessPermissionQuery = accessPermissionFactory.newQuery(scopeId);
                    accessPermissionQuery.setPredicate(new AttributePredicateImpl<KapuaId>(AccessPermissionPredicates.ACCESS_INFO_ID, accessInfo.getId()));
                    accessPermissionQuery.setLimit(loadConfig.getLimit());
                    accessPermissionQuery.setOffset(loadConfig.getOffset());
                    String sortField = StringUtils.isEmpty(loadConfig.getSortField()) ? "createdOn" : loadConfig.getSortField();
                    SortOrder sortOrder = loadConfig.getSortDir().equals(SortDir.DESC) ? SortOrder.DESCENDING : SortOrder.ASCENDING;
                    FieldSortCriteria sortCriteria = new FieldSortCriteria(sortField, sortOrder);
                    accessPermissionQuery.setSortCriteria(sortCriteria);
                    AccessPermissionListResult accessPermissionList = accessPermissionService.query(accessPermissionQuery);
                    if (!accessPermissionList.isEmpty()) {
                        totalLength = Long.valueOf(accessPermissionService.count(accessPermissionQuery)).intValue();
                        for (AccessPermission accessPermission : accessPermissionList.getItems()) {
                            User createdUser = KapuaSecurityUtils.doPrivileged(new Callable<User>() {

                                @Override
                                public User call() throws Exception {
                                    return userService.find(scopeId, user.getCreatedBy());
                                }
                            });
                            GwtAccessPermission gwtAccessPermission = KapuaGwtAuthorizationModelConverter.convertAccessPermission(accessPermission);
                            gwtAccessPermission.setCreatedByName(createdUser.getName());
                            gwtAccessPermissions.add(gwtAccessPermission);
                        }
                    }
                }
            } catch (Throwable t) {
                KapuaExceptionHandler.handle(t);
            }
        }
        return new BasePagingLoadResult<GwtAccessPermission>(gwtAccessPermissions, loadConfig.getOffset(), totalLength);
    }
}
