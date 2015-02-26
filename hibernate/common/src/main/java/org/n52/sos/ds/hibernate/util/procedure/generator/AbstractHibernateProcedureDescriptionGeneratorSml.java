/**
 * Copyright (C) 2012-2015 52°North Initiative for Geospatial Open Source
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
package org.n52.sos.ds.hibernate.util.procedure.generator;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.n52.sos.cache.ContentCache;
import org.n52.sos.ds.I18NDAO;
import org.n52.sos.ds.hibernate.dao.AbstractObservationDAO;
import org.n52.sos.ds.hibernate.dao.DaoFactory;
import org.n52.sos.ds.hibernate.dao.ObservationConstellationDAO;
import org.n52.sos.ds.hibernate.dao.ProcedureDAO;
import org.n52.sos.ds.hibernate.entities.AbstractObservation;
import org.n52.sos.ds.hibernate.entities.ObservationConstellation;
import org.n52.sos.ds.hibernate.entities.Procedure;
import org.n52.sos.ds.hibernate.entities.interfaces.BlobObservation;
import org.n52.sos.ds.hibernate.entities.interfaces.BooleanObservation;
import org.n52.sos.ds.hibernate.entities.interfaces.CategoryObservation;
import org.n52.sos.ds.hibernate.entities.interfaces.CountObservation;
import org.n52.sos.ds.hibernate.entities.interfaces.GeometryObservation;
import org.n52.sos.ds.hibernate.entities.interfaces.NumericObservation;
import org.n52.sos.ds.hibernate.entities.interfaces.TextObservation;
import org.n52.sos.ds.hibernate.util.HibernateHelper;
import org.n52.sos.exception.ows.NoApplicableCodeException;
import org.n52.sos.i18n.I18NDAORepository;
import org.n52.sos.i18n.LocalizedString;
import org.n52.sos.i18n.metadata.I18NProcedureMetadata;
import org.n52.sos.ogc.OGCConstants;
import org.n52.sos.ogc.gml.CodeType;
import org.n52.sos.ogc.om.OmConstants;
import org.n52.sos.ogc.ows.OwsExceptionReport;
import org.n52.sos.ogc.sensorML.AbstractProcess;
import org.n52.sos.ogc.sensorML.elements.SmlIdentifier;
import org.n52.sos.ogc.sensorML.elements.SmlIo;
import org.n52.sos.ogc.sensorML.elements.SmlPosition;
import org.n52.sos.ogc.sos.SosProcedureDescription;
import org.n52.sos.ogc.swe.SweAbstractDataComponent;
import org.n52.sos.ogc.swe.SweConstants;
import org.n52.sos.ogc.swe.SweCoordinate;
import org.n52.sos.ogc.swe.SweConstants.SweCoordinateName;
import org.n52.sos.ogc.swe.simpleType.SweBoolean;
import org.n52.sos.ogc.swe.simpleType.SweCategory;
import org.n52.sos.ogc.swe.simpleType.SweCount;
import org.n52.sos.ogc.swe.simpleType.SweQuantity;
import org.n52.sos.ogc.swe.simpleType.SweText;
import org.n52.sos.service.Configurator;
import org.n52.sos.service.ProcedureDescriptionSettings;
import org.n52.sos.service.ServiceConfiguration;
import org.n52.sos.util.CollectionHelper;
import org.n52.sos.util.GeometryHandler;
import org.n52.sos.util.JavaHelper;
import org.n52.sos.util.StringHelper;
import org.n52.sos.util.http.HTTPStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Coordinate;

public abstract class AbstractHibernateProcedureDescriptionGeneratorSml {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(AbstractHibernateProcedureDescriptionGeneratorSml.class);

    public static final String SQL_QUERY_GET_UNIT_FOR_OBSERVABLE_PROPERTY = "getUnitForObservableProperty";

    public static final String SQL_QUERY_GET_UNIT_FOR_OBSERVABLE_PROPERTY_PROCEDURE =
            "getUnitForObservablePropertyProcedure";

    public static final String SQL_QUERY_GET_UNIT_FOR_OBSERVABLE_PROPERTY_PROCEDURE_OFFERING =
            "getUnitForObservablePropertyProcedureOffering";

    protected static final String POSITION_NAME = "sensorPosition";

    protected static final Joiner COMMA_JOINER = Joiner.on(",");

    private Locale locale = ServiceConfiguration.getInstance().getDefaultLanguage();

    public abstract SosProcedureDescription generateProcedureDescription(Procedure procedure, Locale i18n,
            Session session) throws OwsExceptionReport;

    protected void setLocale(Locale i18n) {
        this.locale = i18n;
    }

    protected Locale getLocale() {
        return locale;
    }

    protected boolean isSetLocale() {
        return locale != null;
    }

    /**
     * Set common values to procedure description
     *
     * @param procedure
     *            Hibernate procedure entity
     * @param abstractProcess
     *            SensorML process
     * @param session
     *            the session
     *
     * @throws OwsExceptionReport
     *             If an error occurs
     */
    protected void setCommonValues(Procedure procedure, AbstractProcess abstractProcess, Session session)
            throws OwsExceptionReport {
        String identifier = procedure.getIdentifier();
        String[] observableProperties = getObservablePropertiesForProcedure(identifier);

        // 1 set description, names
        addNameAndDescription(procedure, abstractProcess);

        // 2 identifier
        abstractProcess.setIdentifier(identifier);

        // 3 set identification
        abstractProcess.setIdentifications(createIdentifications(identifier));

        // 7 set inputs/outputs --> observableProperties
        if (getServiceConfig().isAddOutputsToSensorML()
                && !"hydrology".equalsIgnoreCase(Configurator.getInstance().getProfileHandler().getActiveProfile()
                        .getIdentifier())) {
            abstractProcess.setInputs(createInputs(observableProperties));
            abstractProcess.setOutputs(createOutputs(procedure, observableProperties, session));
        }
    }

    private void addNameAndDescription(Procedure procedure, AbstractProcess abstractProcess) throws OwsExceptionReport {
        I18NDAO<I18NProcedureMetadata> i18nDAO = I18NDAORepository.getInstance().getDAO(I18NProcedureMetadata.class);
        Locale requestedLocale = getLocale();
        if (i18nDAO == null) {
            // no locale support
            ProcedureDAO featureDAO = new ProcedureDAO();
            abstractProcess.addName(featureDAO.getName(procedure));
            abstractProcess.setDescription(featureDAO.getDescription(procedure));
        } else {
            if (requestedLocale != null) {
                // specific locale was requested
                I18NProcedureMetadata i18n = i18nDAO.getMetadata(procedure.getIdentifier(), requestedLocale);
                Optional<LocalizedString> name = i18n.getName().getLocalization(requestedLocale);
                if (name.isPresent()) {
                    abstractProcess.addName(name.get().asCodeType());
                }
                Optional<LocalizedString> description = i18n.getDescription().getLocalization(requestedLocale);
                if (description.isPresent()) {
                    abstractProcess.setDescription(description.get().getText());
                }
            } else {
                Locale defaultLocale = ServiceConfiguration.getInstance().getDefaultLanguage();
                final I18NProcedureMetadata i18n;
                if (ServiceConfiguration.getInstance().isShowAllLanguageValues()) {
                    // load all names
                    i18n = i18nDAO.getMetadata(procedure.getIdentifier());
                } else {
                    // load only name in default locale
                    i18n = i18nDAO.getMetadata(procedure.getIdentifier(), defaultLocale);
                }
                for (LocalizedString name : i18n.getName()) {
                    // either all or default only
                    abstractProcess.addName(name.asCodeType());
                }
                // choose always the description in the default locale
                Optional<LocalizedString> description = i18n.getDescription().getLocalization(defaultLocale);
                if (description.isPresent()) {
                    abstractProcess.setDescription(description.get().getText());
                }
            }
        }
    }

    /**
     * Create a names collection for procedure description
     *
     * @param procedure
     *            Hibernate procedure entity
     *
     * @return Collection with names
     */
    private List<CodeType> createNames(Procedure procedure) {
        // locale
        return Lists.newArrayList(new CodeType(procedure.getIdentifier()));
    }

    private List<SmlIo<?>> createInputs(String[] observableProperties) throws OwsExceptionReport {
        final List<SmlIo<?>> inputs = Lists.newArrayListWithExpectedSize(observableProperties.length);
        int i = 1;
        for (String observableProperty : observableProperties) {
            inputs.add(new SmlIo<String>().setIoName("input#" + i++).setIoValue(getInputComponent(observableProperty)));
        }
        return inputs;
    }

    protected abstract SweAbstractDataComponent getInputComponent(String observableProperty);

    /**
     * Create SensorML output list from observableProperties
     *
     * @param procedure
     *            Hibernate procedure entity
     * @param observableProperties
     *            Properties observed by the procedure
     *
     * @return Output list
     *
     * @throws OwsExceptionReport
     *             If an error occurs
     */
    private List<SmlIo<?>> createOutputs(Procedure procedure, String[] observableProperties, Session session)
            throws OwsExceptionReport {
        try {
            final List<SmlIo<?>> outputs = Lists.newArrayListWithExpectedSize(observableProperties.length);
            int i = 1;
            final boolean supportsObservationConstellation =
                    HibernateHelper.isEntitySupported(ObservationConstellation.class);
            for (String observableProperty : observableProperties) {
                final SmlIo<?> output;
                if (supportsObservationConstellation) {
                    output =
                            createOutputFromObservationConstellation(procedure.getIdentifier(), observableProperty,
                                    session);
                } else {
                    output =
                            createOutputFromExampleObservation(procedure.getIdentifier(), observableProperty, session);
                }
                if (output != null) {
                    output.setIoName("output#" + i++);
                    outputs.add(output);
                }
            }
            return outputs;
        } catch (final HibernateException he) {
            throw new NoApplicableCodeException().causedBy(he).withMessage("Error while querying observation data!")
                    .setStatus(HTTPStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private SmlIo<?> createOutputFromObservationConstellation(String procedure, String observableProperty,
            Session session) throws OwsExceptionReport {
        List<ObservationConstellation> observationConstellations =
                new ObservationConstellationDAO().getObservationConstellations(procedure, observableProperty, session);
        if (CollectionHelper.isNotEmpty(observationConstellations)) {
            String unit = queryUnit(observationConstellations.iterator().next(), session);
            ObservationConstellation oc = observationConstellations.iterator().next();
            if (oc.isSetObservationType()) {
                final String observationType = oc.getObservationType().getObservationType();
                if (OmConstants.OBS_TYPE_MEASUREMENT.equals(observationType)) {
                    final SweQuantity quantity = new SweQuantity();
                    quantity.setDefinition(observableProperty);
                    if (StringHelper.isNotEmpty(unit)) {
                        quantity.setUom(unit);
                    }
                    return new SmlIo<Double>(quantity);
                } else if (OmConstants.OBS_TYPE_CATEGORY_OBSERVATION.equals(observationType)) {
                    final SweCategory category = new SweCategory();
                    category.setDefinition(observableProperty);
                    if (StringHelper.isNotEmpty(unit)) {
                        category.setUom(unit);
                    }
                    return new SmlIo<String>(category);
                } else if (OmConstants.OBS_TYPE_COUNT_OBSERVATION.equals(observationType)) {
                    return new SmlIo<String>(new SweCategory().setDefinition(observableProperty));
                } else if (OmConstants.OBS_TYPE_TEXT_OBSERVATION.equals(observationType)) {
                    return new SmlIo<String>(new SweText().setDefinition(observableProperty));
                } else if (OmConstants.OBS_TYPE_TRUTH_OBSERVATION.equals(observationType)) {
                    return new SmlIo<Boolean>(new SweBoolean().setDefinition(observableProperty));
                } else if (OmConstants.OBS_TYPE_GEOMETRY_OBSERVATION.equals(observationType)) {
                    // TODO implement GeometryObservation
                    logTypeNotSupported(OmConstants.OBS_TYPE_GEOMETRY_OBSERVATION);
                } else if (OmConstants.OBS_TYPE_COMPLEX_OBSERVATION.equals(observationType)) {
                    // TODO implement ComplexObservation
                    logTypeNotSupported(OmConstants.OBS_TYPE_COMPLEX_OBSERVATION);
                } else if (OmConstants.OBS_TYPE_UNKNOWN.equals(observationType)) {
                    // TODO implement UnknownObservation
                    logTypeNotSupported(OmConstants.OBS_TYPE_UNKNOWN);
                } else if (OmConstants.OBS_TYPE_SWE_ARRAY_OBSERVATION.equals(observationType)) {
                    // TODO implement SWEArrayObservation
                    logTypeNotSupported(OmConstants.OBS_TYPE_SWE_ARRAY_OBSERVATION);
                }
            }
        }
        return null;
    }

    private String queryUnit(ObservationConstellation oc, Session session) throws OwsExceptionReport {
        if (HibernateHelper.isNamedQuerySupported(SQL_QUERY_GET_UNIT_FOR_OBSERVABLE_PROPERTY_PROCEDURE_OFFERING,
                session)) {
            Query namedQuery = session.getNamedQuery(SQL_QUERY_GET_UNIT_FOR_OBSERVABLE_PROPERTY_PROCEDURE_OFFERING);
            namedQuery.setParameter(ObservationConstellation.OBSERVABLE_PROPERTY, oc.getObservableProperty()
                    .getIdentifier());
            namedQuery.setParameter(ObservationConstellation.PROCEDURE, oc.getProcedure().getIdentifier());
            namedQuery.setParameter(ObservationConstellation.OFFERING, oc.getOffering().getIdentifier());
            LOGGER.debug("QUERY queryUnit(observationConstellation) with NamedQuery: {}",
                    SQL_QUERY_GET_UNIT_FOR_OBSERVABLE_PROPERTY_PROCEDURE_OFFERING);
            return (String) namedQuery.uniqueResult();
        } else if (HibernateHelper
                .isNamedQuerySupported(SQL_QUERY_GET_UNIT_FOR_OBSERVABLE_PROPERTY_PROCEDURE, session)) {
            Query namedQuery = session.getNamedQuery(SQL_QUERY_GET_UNIT_FOR_OBSERVABLE_PROPERTY_PROCEDURE);
            namedQuery.setParameter(ObservationConstellation.OBSERVABLE_PROPERTY, oc.getObservableProperty()
                    .getIdentifier());
            namedQuery.setParameter(ObservationConstellation.PROCEDURE, oc.getProcedure().getIdentifier());
            LOGGER.debug("QUERY queryUnit(observationConstellation) with NamedQuery: {}",
                    SQL_QUERY_GET_UNIT_FOR_OBSERVABLE_PROPERTY_PROCEDURE);
            return (String) namedQuery.uniqueResult();
        } else if (HibernateHelper.isNamedQuerySupported(SQL_QUERY_GET_UNIT_FOR_OBSERVABLE_PROPERTY, session)) {
            Query namedQuery = session.getNamedQuery(SQL_QUERY_GET_UNIT_FOR_OBSERVABLE_PROPERTY);
            namedQuery.setParameter(ObservationConstellation.OBSERVABLE_PROPERTY, oc.getObservableProperty()
                    .getIdentifier());
            LOGGER.debug("QUERY queryUnit(observationConstellation) with NamedQuery: {}",
                    SQL_QUERY_GET_UNIT_FOR_OBSERVABLE_PROPERTY);
            return (String) namedQuery.uniqueResult();
        }
        AbstractObservation exampleObservation =
                getExampleObservation(oc.getProcedure().getIdentifier(), oc.getObservableProperty()
                        .getIdentifier(), session);
        if (exampleObservation != null && exampleObservation.isSetUnit()) {
            return exampleObservation.getUnit().getUnit();
        }
        return null;
    }

    /**
     * Logger method for class
     *
     * @param clazz
     *            Name of not supported class
     */
    private void logTypeNotSupported(Class<?> clazz) {
        LOGGER.debug("Type '{}' is not supported by the current implementation", clazz.getName());
    }

    /**
     * Logger method for class
     *
     * @param observationType
     *            Name of not supported class
     */
    private void logTypeNotSupported(String observationType) {
        LOGGER.debug("ObservationType '{}' is not supported by the current implementation", observationType);
    }

    private SmlIo<?> createOutputFromExampleObservation(String procedure, String observableProperty, Session session)
            throws OwsExceptionReport {
        final AbstractObservation exampleObservation = getExampleObservation(procedure, observableProperty, session);
        if (exampleObservation == null) {
            return null;
        }
        if (exampleObservation instanceof BlobObservation) {
            // TODO implement BlobObservations
            logTypeNotSupported(BlobObservation.class);
        } else if (exampleObservation instanceof BooleanObservation) {
            return new SmlIo<Boolean>(new SweBoolean().setDefinition(observableProperty));
        } else if (exampleObservation instanceof CategoryObservation) {
            final SweCategory category = new SweCategory();
            category.setDefinition(observableProperty);
            if (exampleObservation.isSetUnit()) {
                category.setUom(exampleObservation.getUnit().getUnit());
            }
            return new SmlIo<String>(category);
        } else if (exampleObservation instanceof CountObservation) {
            return new SmlIo<Integer>(new SweCount().setDefinition(observableProperty));
        } else if (exampleObservation instanceof GeometryObservation) {
            // TODO implement GeometryObservations
            logTypeNotSupported(GeometryObservation.class);
        } else if (exampleObservation instanceof NumericObservation) {
            final SweQuantity quantity = new SweQuantity();
            quantity.setDefinition(observableProperty);
            if (exampleObservation.isSetUnit()) {
                quantity.setUom(exampleObservation.getUnit().getUnit());
            }
            return new SmlIo<Double>(quantity);
        } else if (exampleObservation instanceof TextObservation) {
            return new SmlIo<String>(new SweText().setDefinition(observableProperty));
        }
        return null;
    }

    /**
     * Create SensorML Position from Hibernate procedure entity
     *
     * @param procedure
     *            Hibernate procedure entity
     *
     * @return SensorML Position
     */
    protected SmlPosition createPosition(Procedure procedure) {
        SmlPosition position = new SmlPosition();
        position.setName(POSITION_NAME);
        position.setFixed(true);
        int srid = GeometryHandler.getInstance().getDefaultResponseEPSG();
        if (procedure.isSetLongLat()) {
            // 8.1 set latlong position
            position.setPosition(createCoordinatesForPosition(procedure.getLongitude(), procedure.getLatitude(),
                    procedure.getAltitude()));

        } else if (procedure.isSetGeometry()) {
            // 8.2 set position from geometry
            if (procedure.getGeom().getSRID() > 0) {
                srid = procedure.getGeom().getSRID();
            }
            final Coordinate c = procedure.getGeom().getCoordinate();
            position.setPosition(createCoordinatesForPosition(c.y, c.x, c.z));
        }
        if (procedure.isSetSrid()) {
            srid = procedure.getSrid();
        }
        position.setReferenceFrame(getServiceConfig().getSrsNamePrefixSosV2() + srid);
        return position;
    }

    /**
     * Create SWE Coordinates for SensorML Position
     *
     * @param longitude
     *            Longitude value
     * @param latitude
     *            Latitude value
     * @param altitude
     *            Altitude value
     *
     * @return List with SWE Coordinate
     */
    private List<SweCoordinate<?>> createCoordinatesForPosition(Object longitude, Object latitude, Object altitude) {
        SweQuantity yq = createSweQuantity(latitude, SweConstants.Y_AXIS, procedureSettings().getLatLongUom());
        SweQuantity xq = createSweQuantity(longitude, SweConstants.X_AXIS, procedureSettings().getLatLongUom());
        SweQuantity zq = createSweQuantity(altitude, SweConstants.Z_AXIS, procedureSettings().getAltitudeUom());
        // TODO add Integer: Which SweSimpleType to use?
        return Lists.<SweCoordinate<?>> newArrayList(new SweCoordinate<Double>(SweCoordinateName.northing.name(), yq),
                new SweCoordinate<Double>(SweCoordinateName.easting.name(), xq), new SweCoordinate<Double>(
                        SweCoordinateName.altitude.name(), zq));
    }

    /**
     * Create SWE Quantity for SWE coordinate
     *
     * @param value
     *            Value
     * @param axis
     *            Axis id
     * @param uom
     *            UnitOfMeasure
     *
     * @return SWE Quantity
     */
    private SweQuantity createSweQuantity(Object value, String axis, String uom) {
        return new SweQuantity().setAxisID(axis).setUom(uom).setValue(JavaHelper.asDouble(value));
    }

    private List<String> createDescriptions(Procedure procedure, String[] observableProperties) {
        // locale
        String template = procedureSettings().getDescriptionTemplate();
        String identifier = procedure.getIdentifier();
        String obsProps = COMMA_JOINER.join(observableProperties);
        String type = procedure.isSpatial() ? "sensor system" : "procedure";
        return Lists.newArrayList(String.format(template, type, identifier, obsProps));
    }

    private List<SmlIdentifier> createIdentifications(final String identifier) {
        return Lists.newArrayList(createIdentifier(identifier));
    }

    private SmlIdentifier createIdentifier(final String identifier) {
        return new SmlIdentifier(OGCConstants.URN_UNIQUE_IDENTIFIER_END, OGCConstants.URN_UNIQUE_IDENTIFIER,
                identifier);
    }
    
    
    protected boolean hasChildProcedure(String procedure) {
        return CollectionHelper.isNotEmpty(getCache().getChildProcedures(procedure, false, false));
    }

    /**
     * Get example observation for output list creation
     *
     * @param identifier
     *            Procedure identifier
     * @param observableProperty
     *            ObservableProperty identifier
     * @param session
     *            the session
     *
     * @return Example observation
     *
     * @throws OwsExceptionReport
     *             If an error occurs.
     */
    @VisibleForTesting
    AbstractObservation getExampleObservation(String identifier, String observableProperty, Session session)
            throws OwsExceptionReport {
        AbstractObservationDAO observationDAO = DaoFactory.getInstance().getObservationDAO(session);
        final Criteria c = observationDAO.getObservationCriteriaFor(identifier, observableProperty, session);
        c.setMaxResults(1);
        LOGGER.debug("QUERY getExampleObservation(identifier, observableProperty): {}",
                HibernateHelper.getSqlString(c));
        final AbstractObservation example = (AbstractObservation) c.uniqueResult();
        if (example == null) {
            LOGGER.debug(
                    "Could not receive example observation from database for procedure '{}' observing property '{}'.",
                    identifier, observableProperty);
        }
        return example;
    }

    @VisibleForTesting
    ServiceConfiguration getServiceConfig() {
        return ServiceConfiguration.getInstance();
    }

    @VisibleForTesting
    String[] getObservablePropertiesForProcedure(String identifier) {
        Set<String> props = getCache().getObservablePropertiesForProcedure(identifier);
        return props.toArray(new String[props.size()]);
    }

    @VisibleForTesting
    ProcedureDescriptionSettings procedureSettings() {
        return ProcedureDescriptionSettings.getInstance();
    }

    @VisibleForTesting
    ContentCache getCache() {
        return Configurator.getInstance().getCache();
    }
}
