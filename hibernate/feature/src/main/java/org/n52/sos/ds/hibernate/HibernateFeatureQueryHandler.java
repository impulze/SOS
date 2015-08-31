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
package org.n52.sos.ds.hibernate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.n52.sos.config.annotation.Configurable;
import org.n52.sos.ds.FeatureQueryHandler;
import org.n52.sos.ds.FeatureQueryHandlerQueryObject;
import org.n52.sos.ds.HibernateDatasourceConstants;
import org.n52.sos.ds.I18NDAO;
import org.n52.sos.ds.hibernate.dao.DaoFactory;
import org.n52.sos.ds.hibernate.dao.FeatureOfInterestDAO;
import org.n52.sos.ds.hibernate.dao.FeatureOfInterestTypeDAO;
import org.n52.sos.ds.hibernate.dao.HibernateSqlQueryConstants;
import org.n52.sos.ds.hibernate.entities.FeatureOfInterest;
import org.n52.sos.ds.hibernate.entities.TFeatureOfInterest;
import org.n52.sos.ds.hibernate.util.SpatialRestrictions;
import org.n52.sos.exception.ows.NoApplicableCodeException;
import org.n52.sos.exception.ows.concrete.NotYetSupportedException;
import org.n52.sos.i18n.I18NDAORepository;
import org.n52.sos.i18n.LocalizedString;
import org.n52.sos.i18n.metadata.I18NFeatureMetadata;
import org.n52.sos.ogc.OGCConstants;
import org.n52.sos.ogc.filter.SpatialFilter;
import org.n52.sos.ogc.gml.AbstractFeature;
import org.n52.sos.ogc.gml.CodeWithAuthority;
import org.n52.sos.ogc.om.features.samplingFeatures.SamplingFeature;
import org.n52.sos.ogc.ows.OwsExceptionReport;
import org.n52.sos.ogc.sos.SosConstants;
import org.n52.sos.ogc.sos.SosEnvelope;
import org.n52.sos.service.ServiceConfiguration;
import org.n52.sos.util.CollectionHelper;
import org.n52.sos.util.GeometryHandler;
import org.n52.sos.util.JTSHelper;
import org.n52.sos.util.JavaHelper;
import org.n52.sos.util.SosHelper;
import org.n52.sos.util.StringHelper;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;


@Configurable
public class HibernateFeatureQueryHandler implements FeatureQueryHandler, HibernateSqlQueryConstants {
    @Deprecated
    @Override
    public AbstractFeature getFeatureByID(String featureID, Object connection, String version)
            throws OwsExceptionReport {
        FeatureQueryHandlerQueryObject queryObject = new FeatureQueryHandlerQueryObject();
        queryObject.setConnection(connection);
        queryObject.addFeatureIdentifier(featureID);
        queryObject.setVersion(version);
        return getFeatureByID(queryObject);
    }

    @Override
    public AbstractFeature getFeatureByID(FeatureQueryHandlerQueryObject queryObject) throws OwsExceptionReport {
    	final Session session = HibernateSessionHolder.getSession(queryObject.getConnection());
    
        try {
        	final FeatureOfInterest foi = new FeatureOfInterestDAO().getFeatureOfInterestObjects(session).get(0);
        	return createSosAbstractFeature(foi, queryObject);
        } catch (final HibernateException he) {
            throw new NoApplicableCodeException().causedBy(he).withMessage(
                    "An error occurred while querying feature data for a featureOfInterest identifier!");
        }

    }

    @Deprecated
    @Override
    public Collection<String> getFeatureIDs(final SpatialFilter filter, final Object connection)
            throws OwsExceptionReport {
    	final Session session = HibernateSessionHolder.getSession(connection);
    	final List<FeatureOfInterest> fois = new FeatureOfInterestDAO().getFeatureOfInterestObjects(session);
    	final List<String> foiIdentifiers = new ArrayList<String>();
    	final Iterator<FeatureOfInterest> iterator = fois.iterator();
    	final Geometry bbox = getGeometryHandler().switchCoordinateAxisFromToDatasourceIfNeeded(filter.getGeometry());

    	while (iterator.hasNext()) {
    		final FeatureOfInterest foi = iterator.next();

    		if (SpatialRestrictions.filter(foi.getGeom(), filter.getOperator(), bbox)) {
    			foiIdentifiers.add(foi.getIdentifier());
    		}
    	}

    	return foiIdentifiers;
    }

    @Override
    public Collection<String> getFeatureIDs(FeatureQueryHandlerQueryObject queryObject) throws OwsExceptionReport {
        return getFeatureIDs(queryObject.getSpatialFitler(), queryObject.getConnection());
    }

    @Deprecated
    @Override
    public Map<String, AbstractFeature> getFeatures(Collection<String> featureIDs, List<SpatialFilter> spatialFilters,
            Object connection, String version) throws OwsExceptionReport {
        FeatureQueryHandlerQueryObject queryObject = new FeatureQueryHandlerQueryObject();
        queryObject.setFeatureIdentifiers(featureIDs);
        queryObject.setSpatialFilters(spatialFilters);
        queryObject.setConnection(connection);
        queryObject.setVersion(version);
        return getFeatures(queryObject);
    }

    @Override
    public Map<String, AbstractFeature> getFeatures(FeatureQueryHandlerQueryObject queryObject)
            throws OwsExceptionReport {
        try {
            if (GeometryHandler.getInstance().isSpatialDatasource()) {
                return getFeaturesForSpatialDatasource(queryObject);
            } else {
                return getFeaturesForNonSpatialDatasource(queryObject);
            }
        } catch (final HibernateException he) {
            throw new NoApplicableCodeException().causedBy(he).withMessage(
                    "Error while querying features from data source!");
        }
    }

    @Deprecated
    @Override
    public SosEnvelope getEnvelopeForFeatureIDs(Collection<String> featureIDs, Object connection)
            throws OwsExceptionReport {
        return getEnvelopeForFeatureIDs(new FeatureQueryHandlerQueryObject().setFeatureIdentifiers(featureIDs)
                .setConnection(connection));
    }

    @Override
    public SosEnvelope getEnvelopeForFeatureIDs(FeatureQueryHandlerQueryObject queryObject) throws OwsExceptionReport {
        final Session session = HibernateSessionHolder.getSession(queryObject.getConnection());
        if (!queryObject.isSetFeatureIdentifiers()) {
        	return null;
        }

        final Envelope envelope = new Envelope();
        final List<FeatureOfInterest> featuresOfInterest = new FeatureOfInterestDAO().getFeatureOfInterestObjects(session);

        for (final FeatureOfInterest feature : featuresOfInterest) {
        	final Geometry geometry = getGeomtery(feature, session);

    		if (geometry != null) {
    			envelope.expandToInclude(geometry.getEnvelopeInternal());
    		}
        }

        if (!envelope.isNull()) {
        	return new SosEnvelope(envelope, getDefaultEPSG());
        }

        return null;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.n52.sos.ds.FeatureQueryHandler#insertFeature(org.n52.sos.ogc.om.features
     * .samplingFeatures.SamplingFeature, java.lang.Object)
     *
     * FIXME check semantics of this method in respect to its name and the
     * documentation in the super class
     */
    @Override
    public String insertFeature(final SamplingFeature samplingFeature, final Object connection)
            throws OwsExceptionReport {
        if (StringHelper.isNotEmpty(samplingFeature.getUrl())) {
            if (samplingFeature.isSetIdentifier()) {
                return samplingFeature.getIdentifierCodeWithAuthority().getValue();
            } else {
                return samplingFeature.getUrl();
            }
        } else {
            final Session session = HibernateSessionHolder.getSession(connection);
            String featureIdentifier;
            if (!samplingFeature.isSetIdentifier()) {
                featureIdentifier =
                        SosConstants.GENERATED_IDENTIFIER_PREFIX
                                + JavaHelper.generateID(samplingFeature.getXmlDescription());
                samplingFeature.setIdentifier(new CodeWithAuthority(featureIdentifier));
            }
            return insertFeatureOfInterest(samplingFeature, session).getIdentifier();
        }
    }

    @Deprecated
    @Override
    public int getDefaultEPSG() {
        return getStorageEPSG();
    }

    @Deprecated
    @Override
    public int getDefault3DEPSG() {
        return getStorage3DEPSG();
    }

    @Override
    public int getStorageEPSG() {
        return GeometryHandler.getInstance().getStorageEPSG();
    }

    @Override
    public int getStorage3DEPSG() {
        return GeometryHandler.getInstance().getStorage3DEPSG();
    }

    @Override
    public int getDefaultResponseEPSG() {
        return GeometryHandler.getInstance().getDefaultResponseEPSG();
    }

    @Override
    public int getDefaultResponse3DEPSG() {
        return GeometryHandler.getInstance().getDefaultResponse3DEPSG();
    }

    protected GeometryHandler getGeometryHandler() {
        return GeometryHandler.getInstance();
    }

    /**
     * Creates a map with FOI identifier and SOS feature
     * <p/>
     *
     * @param features
     *            FeatureOfInterest objects
     * @param queryObject
     *            SOS version
     *            <p/>
     * @return Map with FOI identifier and SOS feature
     *         <p/>
     * @throws OwsExceptionReport
     *             * If feature type is not supported
     */
    protected Map<String, AbstractFeature> createSosFeatures(final List<FeatureOfInterest> features,
            final FeatureQueryHandlerQueryObject queryObject, Session session) throws OwsExceptionReport {
        final Map<String, AbstractFeature> sosAbstractFois = new HashMap<String, AbstractFeature>();
        for (final FeatureOfInterest feature : features) {
            final AbstractFeature sosFeature = createSosAbstractFeature(feature, queryObject, session);
            sosAbstractFois.put(feature.getIdentifier(), sosFeature);
        }
        // TODO if sampledFeatures are also in sosAbstractFois, reference them.
        return sosAbstractFois;
    }

    protected FeatureOfInterest getFeatureOfInterest(final String identifier, final Geometry geometry,
            final Session session) throws OwsExceptionReport {
    	final List<FeatureOfInterest> fois = new FeatureOfInterestDAO().getFeatureOfInterestObjects(session);

        if (!identifier.startsWith(SosConstants.GENERATED_IDENTIFIER_PREFIX)) {
        	for (final FeatureOfInterest foi: fois) {
        		if (foi.getIdentifier().equals(identifier)) {
        			return foi;
        		}
        	}
        } else {
        	final Geometry checkGeometry = GeometryHandler.getInstance().switchCoordinateAxisFromToDatasourceIfNeeded(geometry);

        	for (final FeatureOfInterest foi: fois) {
        		if (foi.getGeom().equals(checkGeometry)) {
        			return foi;
        		}
        	}
        }

        return null;
    }

    protected AbstractFeature createSosAbstractFeature(final FeatureOfInterest feature,
            final FeatureQueryHandlerQueryObject queryObject) throws OwsExceptionReport {
        final Session session = HibernateSessionHolder.getSession(queryObject.getConnection());
        return createSosAbstractFeature(feature, queryObject, session);
    }

    /**
     * Creates a SOS feature from the FeatureOfInterest object
     *
     * @param feature
     *            FeatureOfInterest object
     * @param version
     *            SOS version
     * @return SOS feature
     * @throws OwsExceptionReport
     */
    protected AbstractFeature createSosAbstractFeature(final FeatureOfInterest feature,
            final FeatureQueryHandlerQueryObject queryObject, Session session) throws OwsExceptionReport {
        if (feature == null) {
            return null;
        }
        FeatureOfInterestDAO featureOfInterestDAO = new FeatureOfInterestDAO();
        final CodeWithAuthority identifier = featureOfInterestDAO.getIdentifier(feature);
        if (!SosHelper.checkFeatureOfInterestIdentifierForSosV2(feature.getIdentifier(), queryObject.getVersion())) {
            identifier.setValue(null);
        }
        final SamplingFeature sampFeat = new SamplingFeature(identifier);
        addNameAndDescription(queryObject, feature, sampFeat, featureOfInterestDAO);
        sampFeat.setGeometry(getGeomtery(feature, session));
        sampFeat.setFeatureType(feature.getFeatureOfInterestType().getFeatureOfInterestType());
        sampFeat.setUrl(feature.getUrl());
        if (feature.isSetDescriptionXml()) {
            sampFeat.setXmlDescription(feature.getDescriptionXml());
        }
        if (feature instanceof TFeatureOfInterest) {
            final Set<FeatureOfInterest> parentFeatures = ((TFeatureOfInterest) feature).getParents();
            if (parentFeatures != null && !parentFeatures.isEmpty()) {
                final List<AbstractFeature> sampledFeatures = new ArrayList<AbstractFeature>(parentFeatures.size());
                for (final FeatureOfInterest parentFeature : parentFeatures) {
                    sampledFeatures.add(createSosAbstractFeature(parentFeature, queryObject, session));
                }
                sampFeat.setSampledFeatures(sampledFeatures);
            }
        }
        return sampFeat;
    }

    private void addNameAndDescription(FeatureQueryHandlerQueryObject query,
                                       FeatureOfInterest feature,
                                       SamplingFeature samplingFeature,
                                       FeatureOfInterestDAO featureDAO)
            throws OwsExceptionReport {
        I18NDAO<I18NFeatureMetadata> i18nDAO = I18NDAORepository.getInstance().getDAO(I18NFeatureMetadata.class);
        Locale requestedLocale = query.getI18N();
        // set name as human readable identifier if set
        if (feature.isSetName()) {
        	samplingFeature.setHumanReadableIdentifier(feature.getName());
        }
        if (i18nDAO == null) {
            // no i18n support
            samplingFeature.addName(featureDAO.getName(feature));
            samplingFeature.setDescription(featureDAO.getDescription(feature));
        } else {
            I18NFeatureMetadata i18n = i18nDAO.getMetadata(feature.getIdentifier());
            if (requestedLocale != null) {
                // specific locale was requested
                Optional<LocalizedString> name = i18n.getName().getLocalizationOrDefault(requestedLocale);
                if (name.isPresent()) {
                    samplingFeature.addName(name.get().asCodeType());
                }
                Optional<LocalizedString> description = i18n.getDescription().getLocalizationOrDefault(requestedLocale);
                if (description.isPresent()) {
                    samplingFeature.setDescription(description.get().getText());
                }
            } else {
                if (ServiceConfiguration.getInstance().isShowAllLanguageValues()) {
                    for (LocalizedString name : i18n.getName()) {
                        samplingFeature.addName(name.asCodeType());
                    }
                } else {
                    Optional<LocalizedString> name = i18n.getName().getDefaultLocalization();
                    if (name.isPresent()) {
                        samplingFeature.addName(name.get().asCodeType());
                    }
                }
                // choose always the description in the default locale
                Optional<LocalizedString> description = i18n.getDescription().getDefaultLocalization();
                if (description.isPresent()) {
                    samplingFeature.setDescription(description.get().getText());
                }
            }
        }
    }

    protected FeatureOfInterest insertFeatureOfInterest(final SamplingFeature samplingFeature, final Session session)
            throws OwsExceptionReport {
        if (!GeometryHandler.getInstance().isSpatialDatasource()) {
            throw new NotYetSupportedException("Insertion of full encoded features for non spatial datasources");
        }
        FeatureOfInterestDAO featureOfInterestDAO = new FeatureOfInterestDAO();
        final String newId = samplingFeature.getIdentifierCodeWithAuthority().getValue();
        FeatureOfInterest feature = getFeatureOfInterest(newId, samplingFeature.getGeometry(), session);
        if (feature == null) {
            feature = new TFeatureOfInterest();
            featureOfInterestDAO.addIdentifierNameDescription(samplingFeature, feature, session);
            processGeometryPreSave(samplingFeature, feature, session);
            if (samplingFeature.isSetXmlDescription()) {
                feature.setDescriptionXml(samplingFeature.getXmlDescription());
            }
            if (samplingFeature.isSetFeatureType()) {
                feature.setFeatureOfInterestType(new FeatureOfInterestTypeDAO().getOrInsertFeatureOfInterestType(
                        samplingFeature.getFeatureType(), session));
            }
            if (samplingFeature.isSetSampledFeatures()) {
                Set<FeatureOfInterest> parents =
                        Sets.newHashSetWithExpectedSize(samplingFeature.getSampledFeatures().size());
                for (AbstractFeature sampledFeature : samplingFeature.getSampledFeatures()) {
                    if (!OGCConstants.UNKNOWN.equals(sampledFeature.getIdentifierCodeWithAuthority().getValue())) {
                        if (sampledFeature instanceof SamplingFeature) {
                            parents.add(insertFeatureOfInterest((SamplingFeature) sampledFeature, session));
                        } else {
                            parents.add(insertFeatureOfInterest(new SamplingFeature(sampledFeature.getIdentifierCodeWithAuthority()), session));
                        }
                    }
                }
                ((TFeatureOfInterest) feature).setParents(parents);
            }
            session.save(feature);
            session.flush();
            session.refresh(feature);
            featureOfInterestDAO.insertNameAndDescription(feature, samplingFeature, session);
//            return newId;
//        } else {
//            return feature.getIdentifier();
        }
        return feature;
    }

    protected void processGeometryPreSave(final SamplingFeature ssf, final FeatureOfInterest f, Session session)
            throws OwsExceptionReport {
        f.setGeom(getGeometryHandler().switchCoordinateAxisFromToDatasourceIfNeeded(ssf.getGeometry()));
    }

    /**
     * Get the geometry from featureOfInterest object.
     *
     * @param feature
     * @return geometry
     * @throws OwsExceptionReport
     */
    protected Geometry getGeomtery(final FeatureOfInterest feature, Session session) throws OwsExceptionReport {
        if (feature.isSetGeometry()) {
            return GeometryHandler.getInstance().switchCoordinateAxisFromToDatasourceIfNeeded(feature.getGeom());
        } else if (feature.isSetLongLat()) {
            int epsg = getStorageEPSG();
            if (feature.isSetSrid()) {
                epsg = feature.getSrid();
            }
            final String wktString =
                    GeometryHandler.getInstance().getWktString(feature.getLongitude(), feature.getLatitude(), epsg);
            final Geometry geom = JTSHelper.createGeometryFromWKT(wktString, epsg);
            if (feature.isSetAltitude()) {
                geom.getCoordinate().z = GeometryHandler.getInstance().getValueAsDouble(feature.getAltitude());
                if (geom.getSRID() == getStorage3DEPSG()) {
                    geom.setSRID(getStorage3DEPSG());
                }
            }
            return geom;
            // return
            // GeometryHandler.getInstance().switchCoordinateAxisOrderIfNeeded(geom);
        } else {
            if (session != null) {
                List<Geometry> geometries = DaoFactory.getInstance().getObservationDAO().getSamplingGeometries(feature.getIdentifier(), session);
                int srid = GeometryHandler.getInstance().getStorageEPSG();
                if (!CollectionHelper.nullEmptyOrContainsOnlyNulls(geometries)) {
                    List<Coordinate> coordinates = Lists.newLinkedList();
                    Geometry lastGeoemtry = null;
                    for (Geometry geometry : geometries) {
                        if (geometry != null && (lastGeoemtry == null || !geometry.equalsTopo(lastGeoemtry))) {
                        	coordinates.add(GeometryHandler.getInstance().switchCoordinateAxisFromToDatasourceIfNeeded(geometry).getCoordinate());
                            lastGeoemtry = geometry;
                            if (geometry.getSRID() != srid) {
                                srid = geometry.getSRID();
                             }
                        }
                        if (geometry.getSRID() != srid) {
                           srid = geometry.getSRID();
                        }
                        if (!geometry.equalsTopo(lastGeoemtry)) {
                            coordinates.add(GeometryHandler.getInstance().switchCoordinateAxisFromToDatasourceIfNeeded(geometry).getCoordinate());
                            lastGeoemtry = geometry;
                        }
                    }
                    Geometry geom = null;
                    if (coordinates.size() == 1) {
                        geom = new GeometryFactory().createPoint(coordinates.iterator().next());
                    } else {
                        geom = new GeometryFactory().createLineString(coordinates.toArray(new Coordinate[coordinates.size()]));
                    }
                    geom.setSRID(srid);
                    return geom;
                }
            }
        }
        return null;
    }

    protected Map<String, AbstractFeature> getFeaturesForNonSpatialDatasource(
            FeatureQueryHandlerQueryObject queryObject) throws OwsExceptionReport {
        final Session session = HibernateSessionHolder.getSession(queryObject.getConnection());
        final Map<String, AbstractFeature> featureMap = new HashMap<String, AbstractFeature>(0);
        List<Geometry> envelopes = null;
        boolean hasSpatialFilter = false;
        if (queryObject.isSetSpatialFilters()) {
            hasSpatialFilter = true;
            envelopes = new ArrayList<Geometry>(queryObject.getSpatialFilters().size());
            for (final SpatialFilter filter : queryObject.getSpatialFilters()) {
                envelopes.add(GeometryHandler.getInstance().getFilterForNonSpatialDatasource(filter));
            }
        }
        final List<FeatureOfInterest> featuresOfInterest =
                new FeatureOfInterestDAO().getFeatureOfInterestObject(queryObject.getFeatureIdentifiers(), session);
        for (final FeatureOfInterest feature : featuresOfInterest) {
            final SamplingFeature sosAbstractFeature =
                    (SamplingFeature) createSosAbstractFeature(feature, queryObject);
            if (!hasSpatialFilter) {
                featureMap.put(sosAbstractFeature.getIdentifierCodeWithAuthority().getValue(), sosAbstractFeature);
            } else {
                if (GeometryHandler.getInstance().featureIsInFilter(sosAbstractFeature.getGeometry(), envelopes)) {
                    featureMap.put(sosAbstractFeature.getIdentifierCodeWithAuthority().getValue(), sosAbstractFeature);
                }
            }
        }
        return featureMap;
    }

    protected Map<String, AbstractFeature> getFeaturesForSpatialDatasource(FeatureQueryHandlerQueryObject queryObject)
            throws OwsExceptionReport {
        final Session session = HibernateSessionHolder.getSession(queryObject.getConnection());
        final List<FeatureOfInterest> fois = new FeatureOfInterestDAO().getFeatureOfInterestObjects(session);
        final List<FeatureOfInterest> resultFOIs = new ArrayList<FeatureOfInterest>();
        boolean filtered = false;

        if (queryObject.isSetFeatureIdentifiers()) {
        	filtered = true;

        	for (final FeatureOfInterest foi: fois) {
        		for (final String identifier: queryObject.getFeatureIdentifiers()) {
        			if (foi.getIdentifier().equals(identifier)) {
        				resultFOIs.add(foi);
        			}
        		}
        	}
        }

        if (queryObject.isSetSpatialFilters()) {
        	filtered = true;

        	for (final SpatialFilter filter : queryObject.getSpatialFilters()) {
        		final Geometry checkGeometry = getGeometryHandler().switchCoordinateAxisFromToDatasourceIfNeeded(filter.getGeometry());

        		for (final FeatureOfInterest foi: fois) {
        			if (SpatialRestrictions.filter(foi.getGeom(), filter.getOperator(), checkGeometry)) {
        				resultFOIs.add(foi);
        			}
        		}
        	}
        }

        if (filtered) {
            return createSosFeatures(resultFOIs, queryObject, session);
        } else {
            return Collections.emptyMap();
        }
    }

    @Override
    public String getDatasourceDaoIdentifier() {
        return HibernateDatasourceConstants.ORM_DATASOURCE_DAO_IDENTIFIER;
    }
}
