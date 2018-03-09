/*******************************************************************************
 * Copyright (c) 2011, 2017 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech - initial API and implementation
 *******************************************************************************/
package org.eclipse.kapua.service.device.registry.event;

import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.model.query.KapuaQuery;
import org.eclipse.kapua.service.KapuaDomainService;
import org.eclipse.kapua.service.KapuaEntityService;
import org.eclipse.kapua.service.device.registry.Device;

/**
 * {@link DeviceEventService} definition.
 *
 * @since 1.0.0
 */
public interface DeviceEventService extends KapuaEntityService<DeviceEvent, DeviceEventCreator>, KapuaDomainService<DeviceEventDomain> {

    public static final DeviceEventDomain DEVICE_EVENT_DOMAIN = new DeviceEventDomain();

    @Override
    public default DeviceEventDomain getServiceDomain() {
        return DEVICE_EVENT_DOMAIN;
    }

    /**
     * Creates the {@link DeviceEvent}.
     * This method allows to specify if the related {@link Device#getLastEventId()} must be updated after the {@link DeviceEvent} creation.<br>
     * Use this methods only on particular cases that does not require update of the {@link Device#getLastEventId()}.
     *
     * @param creator                 The {@link DeviceEventCreator} from which create the {@link DeviceEvent}.
     * @param updateDeviceLastEventId Whether or not update the {@link Device#getLastEventId()}.
     * @return The created {@link DeviceEvent}
     * @throws KapuaException
     * @since 1.0.0
     */
    public DeviceEvent create(DeviceEventCreator creator, boolean updateDeviceLastEventId)
            throws KapuaException;

    /**
     * Returns the {@link DeviceEventListResult} with elements matching the provided query.
     *
     * @param query The {@link DeviceEventQuery} used to filter results.
     * @return The {@link DeviceEventListResult} with elements matching the query parameter.
     * @throws KapuaException
     * @since 1.0.0
     */
    @Override
    public DeviceEventListResult query(KapuaQuery<DeviceEvent> query)
            throws KapuaException;

}
