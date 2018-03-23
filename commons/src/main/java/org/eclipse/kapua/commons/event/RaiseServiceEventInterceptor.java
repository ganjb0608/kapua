/*******************************************************************************
 * Copyright (c) 2017 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech - initial API and implementation
 *******************************************************************************/
package org.eclipse.kapua.commons.event;

import java.lang.annotation.Annotation;
import java.util.Date;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.eclipse.kapua.commons.core.InterceptorBind;
import org.eclipse.kapua.commons.model.id.KapuaEid;
import org.eclipse.kapua.commons.security.KapuaSecurityUtils;
import org.eclipse.kapua.commons.security.KapuaSession;
import org.eclipse.kapua.commons.service.event.store.api.ServiceEventUtil;
import org.eclipse.kapua.commons.service.event.store.internal.EventStoreDAO;
import org.eclipse.kapua.commons.service.internal.AbstractKapuaService;
import org.eclipse.kapua.event.RaiseServiceEvent;
import org.eclipse.kapua.event.ServiceEvent;
import org.eclipse.kapua.event.ServiceEventBusException;
import org.eclipse.kapua.event.ServiceEvent.EventStatus;
import org.eclipse.kapua.locator.KapuaProvider;
import org.eclipse.kapua.model.KapuaEntity;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.KapuaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event interceptor. It builds the event object and sends it to the event bus.
 * 
 * @since 1.0
 */
@KapuaProvider
@InterceptorBind(matchSubclassOf = KapuaService.class, matchAnnotatedWith = RaiseServiceEvent.class)
public class RaiseServiceEventInterceptor implements MethodInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RaiseServiceEventInterceptor.class);

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object returnObject = null;

        try {
            // if(!create) then the entity id can be set here
            ServiceEvent serviceEvent = ServiceEventScope.begin();

            KapuaSession session = KapuaSecurityUtils.getSession();
            // Context ID is initialized/managed by the EventScope object
            serviceEvent.setTimestamp(new Date());
            serviceEvent.setUserId(session.getUserId());
            serviceEvent.setScopeId(session.getScopeId());
            fillEvent(invocation, serviceEvent);

            // execute the business logic
            returnObject = invocation.proceed();

            // Raise service event if the execution is successful
            try {
                sendEvent(invocation, serviceEvent, returnObject);
            } catch (ServiceEventBusException e) {
                LOGGER.warn("Error sending event: {}", e.getMessage(), e);
            }

            return returnObject;

        } finally {
            ServiceEventScope.end();
        }
    }

    private void fillEvent(MethodInvocation invocation, ServiceEvent serviceEvent) {
        // fill the inputs
        StringBuilder inputs = new StringBuilder();
        for (Object obj : invocation.getArguments()) {
            inputs.append(obj != null ? obj.toString() : "null");
            inputs.append(", ");
        }
        if (inputs.length() > 2) {
            inputs.replace(inputs.length() - 2, inputs.length(), "");
        }
        serviceEvent.setInputs(inputs.toString());
        if (invocation.getThis() instanceof AbstractKapuaService) {
            // get the service name
            // the service is wrapped by guice so getThis --> getSuperclass() should provide the intercepted class
            // then keep the interface from this object
            serviceEvent.setOperation(invocation.getMethod().getName());
            Class<?> wrappedClass = ((AbstractKapuaService) invocation.getThis()).getClass().getSuperclass(); // this object should be not null
            Class<?>[] impementedClass = wrappedClass.getInterfaces();
            // assuming that the KapuaService implemented is specified by the first implementing interface
            String serviceInterfaceName = impementedClass[0].getName();
            // String splittedServiceInterfaceName[] = serviceInterfaceName.split("\\.");
            // String serviceName = splittedServiceInterfaceName.length > 0 ? splittedServiceInterfaceName[splittedServiceInterfaceName.length-1] : "";
            // String cleanedServiceName = serviceName.substring(0, serviceName.length()-"Service".length()).toLowerCase();
            String cleanedServiceName = serviceInterfaceName;
            LOGGER.info("Service name '{}' ", cleanedServiceName);
            serviceEvent.setService(cleanedServiceName);
            Object[] arguments = invocation.getArguments();
            KapuaEntity kapuaEntity = null;
            KapuaId scopeId = null;
            KapuaId entityId = null;
            if (arguments != null) {
                for (Object tmp : arguments) {
                    LOGGER.info("Scan for entity. Object: {}", tmp != null ? tmp.getClass() : "null");
                    if (tmp instanceof KapuaEntity) {
                        kapuaEntity = (KapuaEntity) tmp;
                        break;
                    }
                    if (tmp instanceof KapuaId) {
                        if (entityId != null && scopeId != null) {
                            continue;
                        }
                        if (entityId != null) {
                            scopeId = entityId;
                            entityId = (KapuaId) tmp;
                            break;
                        }
                        else {
                            entityId = (KapuaId) tmp;
                        }
                    }
                }
            }
            if (kapuaEntity != null) {
                serviceEvent.setEntityType(kapuaEntity.getClass().getName());
                serviceEvent.setEntityId(kapuaEntity.getId());
                serviceEvent.setScopeId(kapuaEntity.getScopeId());
                LOGGER.info("Entity '{}' with id '{}' found!", new Object[] { kapuaEntity.getClass().getName(), kapuaEntity.getId() });
            } else {
                // otherwise assume that the second identifier is the entity id (if there are more than one) or take the first one (if there is one)
                if (scopeId != null) {
                    // leave the scope id from the session if no scope id is detected ion the input parameters
                    serviceEvent.setScopeId(scopeId);
                }
                serviceEvent.setEntityId(entityId);
                String serviceInterface = impementedClass[0].getAnnotatedInterfaces()[0].getType().getTypeName();
                String genericsList = serviceInterface.substring(serviceInterface.indexOf('<') + 1, serviceInterface.indexOf('>'));
                String[] entityClassesToScan = genericsList.replaceAll("\\,", "").split(" ");
                for (String str : entityClassesToScan) {
                    try {
                        if (KapuaEntity.class.isAssignableFrom(Class.forName(str))) {
                            serviceEvent.setEntityType(str);
                        }
                    } catch (ClassNotFoundException e) {
                        // do nothing
                        LOGGER.warn("Cannon find class {}", str, e);
                    }
                }
            }
        } else {
            Annotation[] annotations = invocation.getMethod().getDeclaredAnnotations();
            for (Annotation annotation : annotations) {
                if (RaiseServiceEvent.class.isAssignableFrom(annotation.annotationType())) {
                    RaiseServiceEvent raiseKapuaEvent = (RaiseServiceEvent) annotation;
                    serviceEvent.setService(raiseKapuaEvent.service());
                    serviceEvent.setEntityType(raiseKapuaEvent.entityType());
                    serviceEvent.setOperation(raiseKapuaEvent.operation());
                    serviceEvent.setNote(raiseKapuaEvent.note());
                    break;
                }
            }
        }
    }

    private void sendEvent(MethodInvocation invocation, ServiceEvent serviceEvent, Object returnedValue) throws ServiceEventBusException {
        String address = ServiceMap.getAddress(serviceEvent.getService());
        try {
            ServiceEventBusManager.getInstance().publish(address, serviceEvent);
            LOGGER.info("SENT event from service {} to {} - entity type {} - entity id {} - context id {}",
                    new Object[] { serviceEvent.getService(), address, serviceEvent.getEntityType(), serviceEvent.getEntityId(), serviceEvent.getContextId() });
            // if message was sent successfully then confirm the event in the event table
            updateEventStatus(invocation, serviceEvent, EventStatus.SENT);
        } catch (ServiceEventBusException e) {
            LOGGER.warn("Error sending event", e);
            // mark event status as SEND_ERROR
            updateEventStatus(invocation, serviceEvent, EventStatus.SEND_ERROR);
        }
    }

    private void updateEventStatus(MethodInvocation invocation, ServiceEvent serviceEventBus, EventStatus newServiceEventStatus) {
        if (invocation.getThis() instanceof AbstractKapuaService) {
            try {
                serviceEventBus.setStatus(newServiceEventStatus);
                ((AbstractKapuaService) invocation.getThis()).getEntityManagerSession().onTransactedAction(
                        em -> EventStoreDAO.update(em,
                                ServiceEventUtil.mergeToEntity(EventStoreDAO.find(em, KapuaEid.parseCompactId(serviceEventBus.getId())), serviceEventBus)));
            } catch (Throwable t) {
                // this may be a valid condition if the HouseKeeper is doing the update concurrently with this task
                LOGGER.warn("Error updating event status: {}", t.getMessage(), t);
            }
        }
    }

}
