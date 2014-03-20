/**
 * Copyright (C) 2012-2014 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *     - Apache License, version 2.0
 *     - Apache Software License, version 1.0
 *     - GNU Lesser General Public License, version 3
 *     - Mozilla Public License, versions 1.0, 1.1 and 2.0
 *     - Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public
 * License version 2 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 */
package org.n52.sos.ds.hibernate;

import org.hibernate.Session;
import org.n52.sos.ds.ConnectionProvider;
import org.n52.sos.ds.ConnectionProviderException;
import org.n52.sos.exception.ows.NoApplicableCodeException;
import org.n52.sos.ogc.ows.OwsExceptionReport;
import org.n52.sos.service.Configurator;

/**
 * @since 4.0.0
 * 
 */
public class HibernateSessionHolder {

    private final ConnectionProvider connectionProvider;

    public HibernateSessionHolder() {
        this.connectionProvider = Configurator.getInstance().getDataConnectionProvider();
    }

    public static Session getSession(Object connection) throws OwsExceptionReport {
        if (!(connection instanceof Session)) {
            throw new NoApplicableCodeException().withMessage("The parameter connection is not an Hibernate Session!");
        }
        return (Session) connection;
    }

    public Session getSession() throws OwsExceptionReport {
        try {
            return getSession(connectionProvider.getConnection());
        } catch (ConnectionProviderException cpe) {
            throw new NoApplicableCodeException().causedBy(cpe).withMessage("Error while getting new Session!");
        }
    }

    public void returnSession(Session session) {
        this.connectionProvider.returnConnection(session);
    }
}
