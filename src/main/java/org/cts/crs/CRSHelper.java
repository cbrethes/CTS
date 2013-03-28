/*
* Coordinate Transformations Suite (abridged CTS)  is a library developped to 
* perform Coordinate Transformations using well known geodetic algorithms 
* and parameter sets. 
* Its main focus are simplicity, flexibility, interoperability, in this order.
*
* This library has been originaled developed by Michael Michaud under the JGeod
* name. It has been renamed CTS in 2009 and shared to the community from 
* the Atelier SIG code repository.
* 
* Since them, CTS is supported by the Atelier SIG team in collaboration with Michael 
* Michaud.
* The new CTS has been funded  by the French Agence Nationale de la Recherche 
* (ANR) under contract ANR-08-VILL-0005-01 and the regional council 
* "Région Pays de La Loire" under the projet SOGVILLE (Système d'Orbservation 
* Géographique de la Ville).
*
* CTS is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
*
* CTS is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License along with
* CTS. If not, see <http://www.gnu.org/licenses/>.
*
* For more information, please consult: <https://github.com/irstv/cts/>
*/
package org.cts.crs;

import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import org.cts.*;
import org.cts.cs.Axis;
import org.cts.cs.CoordinateSystem;
import org.cts.datum.GeodeticDatum;
import org.cts.datum.PrimeMeridian;
import org.cts.op.Identity;
import org.cts.op.projection.*;
import org.cts.op.transformation.GeocentricTranslation;
import org.cts.op.transformation.SevenParameterTransformation;
import org.cts.parser.proj.ProjKeyParameters;
import org.cts.parser.proj.ProjValueParameters;
import org.cts.units.Measure;
import org.cts.units.Quantity;
import org.cts.units.Unit;

/**
 *
 * @author Michael Michaud, Erwan Bocher
 */
public class CRSHelper {

        static final Logger LOGGER = Logger.getLogger(CRSHelper.class);

        /**
         * Create a coordinateReferenceSystem
         *
         * @param identifier
         * @param parameters
         * @return
         */
        public static CoordinateReferenceSystem createCoordinateReferenceSystem(Identifier identifier, Map<String, String> parameters) {

                //Name of the projection
                String name = parameters.get(ProjKeyParameters.title);

                //Get the datum

                GeodeticDatum geodeticDatum = getDatum(parameters);

                if (geodeticDatum == null) {
                        LOGGER.warn("No datum definition. Cannot create the"
                                + "CoordinateReferenceSystem");
                        return null;
                }

                String sproj = parameters.get(ProjKeyParameters.proj);
                String sunit = parameters.get(ProjKeyParameters.units);
                String stometer = parameters.get(ProjKeyParameters.to_meter);
                if (null == sproj) {
                        LOGGER.warn("No projection defined for this Coordinate Reference System");
                        return null;
                }

                //It's not a projected CRS
                if (sproj.equals(ProjValueParameters.GEOCENT)) {
                        Unit unit = Unit.METER;
                        if (stometer != null) {
                                unit = new Unit(Quantity.LENGTH, "", Double.parseDouble(sunit),
                                        "");
                        }
                        CoordinateSystem cs = new CoordinateSystem(new Axis[]{Axis.X,
                                        Axis.Y, Axis.Z}, new Unit[]{unit, unit, unit});

                        return new GeocentricCRS(identifier, geodeticDatum,
                                cs);
                } else if (sproj.equals(ProjValueParameters.LONGLAT)) {
                        Unit unit = Unit.DEGREE;
                        if (stometer != null) {
                                unit = new Unit(Quantity.LENGTH, "", Double.parseDouble(sunit),
                                        "");
                        }
                        CoordinateSystem cs = new CoordinateSystem(new Axis[]{
                                        Axis.LONGITUDE, Axis.LATITUDE, Axis.HEIGHT}, new Unit[]{
                                        unit, unit, Unit.METER});
                        return new Geographic3DCRS(identifier, geodeticDatum,
                                cs);
                } else {
                        Projection proj = getProjection(sproj, geodeticDatum.getEllipsoid(),
                                parameters);
                        if (null != proj) {
                                return new ProjectedCRS(identifier,
                                        geodeticDatum, proj);
                        } else {
                                LOGGER.warn("Unknown projection : " + sproj);
                                return null;
                        }
                }
        }

        /**
         * Set the towgs84 parameters to the
         * <code>GeodeticDaum</code>
         *
         * @param gd
         * @param towgs84Parameters
         */
        public static void setDefaultWGS84Parameters(GeodeticDatum gd, Map<String, String> param) {
                CoordinateOperation op;
                String towgs84Parameters = param.get(ProjKeyParameters.towgs84);
                if (null == towgs84Parameters) {
                        return;
                }
                double[] bwp = new double[7];
                String[] sbwp = towgs84Parameters.split(",");
                boolean identity = true;
                boolean translation = true;
                for (int i = 0; i < sbwp.length; i++) {
                        bwp[i] = Double.parseDouble(sbwp[i]);
                        if (bwp[i] != 0) {
                                identity = false;
                        }
                        if (bwp[i] != 0 && i > 2) {
                                translation = false;
                        }
                }
                if (identity) {
                        op = Identity.IDENTITY;
                } else if (translation) {
                        op = new GeocentricTranslation(bwp[0], bwp[1], bwp[2]);
                } else {
                        op = SevenParameterTransformation.createBursaWolfTransformation(
                                bwp[0], bwp[1], bwp[2], bwp[3], bwp[4], bwp[5], bwp[6]);
                }
                if (op != null) {
                        gd.setDefaultToWGS84Operation(op);
                }
        }

        /**
         * Return the prime meridian according its name
         *
         * @param pmName
         * @return
         */
        public static PrimeMeridian getPrimeMeridian(Map<String, String> param) {
                String pmName = param.get(ProjKeyParameters.pm);
                if (null != pmName) {
                        if (pmName.equals("greenwich")) {
                                return PrimeMeridian.GREENWICH;
                        } else if (pmName.equals("paris")) {
                                return PrimeMeridian.PARIS;
                        } else if (pmName.equals("lisbon")) {
                                return PrimeMeridian.LISBON;
                        } else if (pmName.equals("bogota")) {
                                return PrimeMeridian.BOGOTA;
                        } else if (pmName.equals("madrid")) {
                                return PrimeMeridian.MADRID;
                        } else if (pmName.equals("rome")) {
                                return PrimeMeridian.ROME;
                        } else if (pmName.equals("bern")) {
                                return PrimeMeridian.BERN;
                        } else if (pmName.equals("jakarta")) {
                                return PrimeMeridian.JAKARTA;
                        } else if (pmName.equals("ferro")) {
                                return PrimeMeridian.FERRO;
                        } else if (pmName.equals("brussels")) {
                                return PrimeMeridian.BRUSSELS;
                        } else if (pmName.equals("stockholm")) {
                                return PrimeMeridian.STOCKHOLM;
                        } else if (pmName.equals("athens")) {
                                return PrimeMeridian.ATHENS;
                        } else if (pmName.equals("oslo")) {
                                return PrimeMeridian.OSLO;
                        } else {
                                try {
                                        double pmdd = Double.parseDouble(pmName);
                                        return PrimeMeridian.createPrimeMeridianFromDDLongitude(
                                                new Identifier(PrimeMeridian.class,
                                                Identifiable.UNKNOWN), pmdd);
                                } catch (NumberFormatException ex) {
                                        LOGGER.error(pmName + " prime meridian is not parsable");
                                        return null;
                                }
                        }
                } else {
                        return PrimeMeridian.GREENWICH;
                }
        }

        /**
         * Return true is the prime meridian is supported
         *
         * @param pmName
         * @return
         */
        public static boolean isPrimeMeridianSupported(String pmName) {
                if (null != pmName) {
                        if (pmName.equals("greenwich")) {
                                return true;
                        } else if (pmName.equals("paris")) {
                                return true;
                        } else if (pmName.equals("lisbon")) {
                                return true;
                        } else if (pmName.equals("bogota")) {
                                return true;
                        } else if (pmName.equals("madrid")) {
                                return true;
                        } else if (pmName.equals("rome")) {
                                return true;
                        } else if (pmName.equals("bern")) {
                                return true;
                        } else if (pmName.equals("jakarta")) {
                                return true;
                        } else if (pmName.equals("ferro")) {
                                return true;
                        } else if (pmName.equals("brussels")) {
                                return true;
                        } else if (pmName.equals("stockholm")) {
                                return true;
                        } else if (pmName.equals("athens")) {
                                return true;
                        } else if (pmName.equals("oslo")) {
                                return true;
                        } else {
                                try {
                                        Double.parseDouble(pmName);
                                        return true;
                                } catch (NumberFormatException ex) {
                                        LOGGER.error(pmName + " prime meridian is not supported");
                                        return false;
                                }
                        }
                } else {
                        return true;
                }
        }

        /**
         * TODO : add others datum Return true if the datum name is supported
         *
         * @param datumName
         * @return
         */
        public static boolean isDatumSupported(String datumName) {
                if (null != datumName) {
                        if (datumName.equals(GeodeticDatum.WGS84.getName())) {
                                return true;
                        } else if (datumName.equals(GeodeticDatum.ED50.getName())) {
                                return true;
                        } else if (datumName.equals(GeodeticDatum.NTF.getName())) {
                                return true;
                        } else if (datumName.equals(GeodeticDatum.NTF_PARIS.getName())) {
                                return true;
                        } else if (datumName.equals(GeodeticDatum.RGF93.getName())) {
                                return true;
                        }
                }
                return false;
        }

        public static GeodeticDatum getDatum(Map<String, String> param) {
                String datumName = param.get(ProjKeyParameters.datum);
                if (null != datumName) {
                        if (datumName.equals(GeodeticDatum.WGS84.getShortName())) {
                                return GeodeticDatum.WGS84;
                        } else if (datumName.equals(GeodeticDatum.ED50.getName())) {
                                return GeodeticDatum.ED50;
                        } else if (datumName.equals(GeodeticDatum.NTF.getName())) {
                                return GeodeticDatum.NTF;
                        } else if (datumName.equals(GeodeticDatum.NTF_PARIS.getName())) {
                                return GeodeticDatum.NTF_PARIS;
                        } else if (datumName.equals(GeodeticDatum.RGF93.getName())) {
                                return GeodeticDatum.RGF93;
                        }
               } else {
                Ellipsoid ell = getEllipsoid(param);
                PrimeMeridian pm = getPrimeMeridian(param);
                if (null != pm && null != ell) {
                    GeodeticDatum gd = new GeodeticDatum(pm, ell);
                    setDefaultWGS84Parameters(gd, param);
                    String nadgrids = param.get(ProjKeyParameters.nadgrids);
                    if (nadgrids != null) {
                        LOGGER.warn("A grid has been founded.");
                        //TODO : used a more generic approach to manage grids
                        //French grid
                        if (nadgrids.contains("ntf_r93.gsb")) {
                        }
                    }
                    return gd;
                }

            }
            return null;
        }

        /**
         * Return the ellipsoid according its name or parameters
         *
         * Try first to find a known ellipsoid then to create an ellipsoid from
         * its parameter then to get the ellipsoid associated with a known datum
         *
         * @param ellipsoidName
         * @return
         */
        public static Ellipsoid getEllipsoid(Map<String, String> param) {
                String ellipsoidName = param.get(ProjKeyParameters.ellps);
                String a = param.get(ProjKeyParameters.a);
                String b = param.get(ProjKeyParameters.b);
                String rf = param.get(ProjKeyParameters.rf);
                String datum = param.get(ProjKeyParameters.datum);

                if (null != ellipsoidName) {
                        if (ellipsoidName.equals("GRS80")) {
                                return Ellipsoid.GRS80;
                        } else if (ellipsoidName.equals("WGS_1984") || (ellipsoidName.equals("WGS 84"))) {
                                return Ellipsoid.WGS84;
                        } else if (ellipsoidName.equals("International_1924")) {
                                return Ellipsoid.INTERNATIONAL1924;
                        } else if (ellipsoidName.equals("clrk66")) {
                                return Ellipsoid.CLARKE1866;
                        } else if (ellipsoidName.equals("clrk80")) {
                                return Ellipsoid.CLARKE1880ARC;
                        } else if (ellipsoidName.equals("Clarke_1880_IGN")
                                || ellipsoidName.equals("Clarke 1880 (IGN)")
                                || ellipsoidName.equals("Clarke_1880")) {
                                return Ellipsoid.CLARKE1880IGN;
                        } else if (ellipsoidName.equals("Clarke_1880_RGS")
                                || ellipsoidName.equals("Clarke 1880 (RGS)")) {
                                return Ellipsoid.CLARKE1880RGS;
                        } else if (ellipsoidName.equals("sphere")) {
                                return Ellipsoid.SPHERE;
                        } else if (ellipsoidName.equals("bessel")) {
                                return Ellipsoid.BESSEL1841;
                        } else if (ellipsoidName.equals("krass")) {
                                return Ellipsoid.KRASSOWSKI;
                        } else {
                                LOGGER.warn(ellipsoidName + " return default ellipsoid WGS84");
                                return Ellipsoid.WGS84;
                        }

                } else if (null != a && (null != b || null != rf)) {
                        double a_ = Double.parseDouble(a);
                        if (null != b) {
                                double b_ = Double.parseDouble(b);
                                return Ellipsoid.createEllipsoidFromSemiMinorAxis(a_, b_);
                        } else if (null != rf) {
                                double rf_ = Double.parseDouble(rf);
                                return Ellipsoid.createEllipsoidFromInverseFlattening(a_, rf_);
                        } else {
                                LOGGER.warn("Ellipsoid cannot be defined");
                                return null;
                        }

                } else if (null != datum) {
                        GeodeticDatum gd = getDatum(param);
                        if (gd != null) {
                                return gd.getEllipsoid();
                        } else {
                                LOGGER.warn("The unknown datum do not define an ellipsoid");
                                return null;
                        }
                } else {
                        LOGGER.warn("No way found to define an ellipsoid");
                        return null;
                }
        }

        /**
         * Create a projection based on its name.
         *
         * @param projectionName
         * @param ell
         * @param param
         * @return
         */
        public static Projection getProjection(String projectionName, Ellipsoid ell,
                Map<String, String> param) {
                String slat_0 = param.get("lat_0");
                String slat_1 = param.get("lat_1");
                String slat_2 = param.get("lat_2");
                String slon_0 = param.get("lon_0");
                String sk_0 = param.get("k_0");
                String sx_0 = param.get("x_0");
                String sy_0 = param.get("y_0");
                double lat_0 = slat_0 != null ? Double.parseDouble(slat_0) : 0.;
                double lat_1 = slat_1 != null ? Double.parseDouble(slat_1) : 0.;
                double lat_2 = slat_2 != null ? Double.parseDouble(slat_2) : 0.;
                double lon_0 = slon_0 != null ? Double.parseDouble(slon_0) : 0.;
                double k_0 = sk_0 != null ? Double.parseDouble(sk_0) : 0.;
                double x_0 = sx_0 != null ? Double.parseDouble(sx_0) : 0.;
                double y_0 = sy_0 != null ? Double.parseDouble(sy_0) : 0.;
                Map<String, Measure> map = new HashMap<String, Measure>();
                map.put(Parameter.CENTRAL_MERIDIAN, new Measure(lon_0, Unit.DEGREE));
                map.put(Parameter.LATITUDE_OF_ORIGIN, new Measure(lat_0, Unit.DEGREE));
                map.put(Parameter.STANDARD_PARALLEL_1, new Measure(lat_1, Unit.DEGREE));
                map.put(Parameter.STANDARD_PARALLEL_2, new Measure(lat_2, Unit.DEGREE));
                map.put(Parameter.SCALE_FACTOR, new Measure(k_0, Unit.UNIT));
                map.put(Parameter.FALSE_EASTING, new Measure(x_0, Unit.METER));
                map.put(Parameter.FALSE_NORTHING, new Measure(y_0, Unit.METER));

                if (projectionName.equalsIgnoreCase(ProjValueParameters.LCC)) {
                        if (param.get("lat_2") != null) {
                                return new LambertConicConformal2SP(ell, map);
                        } else {
                                return new LambertConicConformal1SP(ell, map);
                        }
                } else if (projectionName.equalsIgnoreCase(ProjValueParameters.TMERC)) {
                        return new TransverseMercator(ell, map);
                } else if (projectionName.equalsIgnoreCase(ProjValueParameters.UTM)) {
                        int zone = param.get("zone") != null ? Integer.parseInt(param.get("zone")) : 0;
                        lon_0 = (6.0 * (zone - 1) + 183.0) % 360.0;
                        y_0 = param.containsKey("south") ? 10000000.0 : 0.0;
                        map.put(Parameter.CENTRAL_MERIDIAN, new Measure(lon_0,
                                Unit.DEGREE));
                        map.put(Parameter.FALSE_NORTHING, new Measure(y_0, Unit.METER));
                        return new UniversalTransverseMercator(ell, map);
                } else {
                        throw new RuntimeException("Cannot create the projection " + projectionName);
                }
        }
}
