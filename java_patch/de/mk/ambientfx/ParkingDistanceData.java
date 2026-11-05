/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
 *
 * AmbientFX - Einparkhilfe-Distanzen (PDC), fuer proximity-reaktive Effekte.
 *
 * Exakt derselbe Mechanismus wie VehicleData/DaylightData/DriveModeData/
 * OutsideTempData, nur gegen "org.dsi.ifc.carparkingsystem.DSICarParkingSystem",
 * Attribute 10 und 11 (ATTR_PDCDISTANCEVALUESFRONT / ATTR_PDCDISTANCEVALUESREAR),
 * Methoden updatePDCDistanceValuesFront/Rear(PDCDistanceValuesFrontRear, int).
 * Per javap gegen vendor/lsd.jar bestaetigt: liefert vier Distanzwerte
 * (links, links-mitte, rechts-mitte, rechts) getrennt fuer vorne und hinten
 * - dieselben Werte, die die Balken/den Signalton der normalen Einparkhilfe
 * speisen.
 *
 * ACHTUNG: wie OutsideTempData noch NICHT live im Fahrzeug getestet - nur
 * per Decompile verifiziert. Insbesondere Einheit/Wertebereich der
 * Distanzwerte (vermutlich Zentimeter, evtl. mit Sonderwerten fuer "kein
 * Hindernis erkannt") sind ungetestet. Effekte, die das nutzen, sollten
 * defensiv mit Ausreissern/Sonderwerten umgehen.
 *
 * Die vielen Leermethoden unten sind Pflicht (Java 1.2, Interface verlangt
 * alle Methoden von DSICarParkingSystemListener - deckt neben PDC auch
 * Rueckfahrkamera/Umgebungsansicht/kabelloses Laden ab), uns interessieren
 * nur die beiden Distanz-Methoden.
 *
 * Java 1.2 kompatibel.
 */
package de.mk.ambientfx;

import de.audi.app.car.common.app.ICarApplication;
import de.audi.app.car.common.comp.AbstractCarComponent;
import de.audi.app.car.common.comp.CarDSIAttributesSet;
import org.dsi.ifc.carparkingsystem.ARACurrentTrailerAngle;
import org.dsi.ifc.carparkingsystem.ARAInfo;
import org.dsi.ifc.carparkingsystem.DSICarParkingSystemListener;
import org.dsi.ifc.carparkingsystem.DisplayContent;
import org.dsi.ifc.carparkingsystem.PDCCrashWarning;
import org.dsi.ifc.carparkingsystem.PDCDistanceValuesFrontRear;
import org.dsi.ifc.carparkingsystem.PDCDistanceValuesFrontRearExt;
import org.dsi.ifc.carparkingsystem.PDCDistanceValuesRightLeft;
import org.dsi.ifc.carparkingsystem.PDCInfo;
import org.dsi.ifc.carparkingsystem.PDCManeuverAssistState;
import org.dsi.ifc.carparkingsystem.PDCPLABargraph;
import org.dsi.ifc.carparkingsystem.PDCPLAStatus;
import org.dsi.ifc.carparkingsystem.PDCPLASystemState;
import org.dsi.ifc.carparkingsystem.PDCPiloPaSystemState;
import org.dsi.ifc.carparkingsystem.PDCSound;
import org.dsi.ifc.carparkingsystem.PDCSoundReproduction;
import org.dsi.ifc.carparkingsystem.PDCStatusLevelFrontRear;
import org.dsi.ifc.carparkingsystem.PDCStatusLevelFrontRearExt;
import org.dsi.ifc.carparkingsystem.PDCStatusLevelRightLeft;
import org.dsi.ifc.carparkingsystem.PDCSteeringInformation;
import org.dsi.ifc.carparkingsystem.PDCWallDetection;
import org.dsi.ifc.carparkingsystem.ParkingSystemViewOptions;
import org.dsi.ifc.carparkingsystem.VPSCameraCleaning;
import org.dsi.ifc.carparkingsystem.VPSCameraStates;
import org.dsi.ifc.carparkingsystem.VPSDefaultMode;
import org.dsi.ifc.carparkingsystem.VPSDynParkingMode;
import org.dsi.ifc.carparkingsystem.VPSOPSOverlay;
import org.dsi.ifc.carparkingsystem.VPSRimProtection;
import org.dsi.ifc.carparkingsystem.VPSVideoInfo;
import org.dsi.ifc.carparkingsystem.WCPanelInfo;
import org.dsi.ifc.carparkingsystem.WCPanelListRecord;
import org.dsi.ifc.carparkingsystem.WCPinPukState;
import org.dsi.ifc.carparkingsystem.WCVehiclePanelInfo;
import org.dsi.ifc.carparkingsystem.WCViewOptions;
import org.dsi.ifc.global.CarArrayListUpdateInfo;

public class ParkingDistanceData extends AbstractCarComponent implements DSICarParkingSystemListener {

    private static final String TAG = "ParkingDistanceData";

    /** Eigene Komponenten-ID (900 VehicleData, 901 DaylightData, 903
     *  DriveModeData, 904 OutsideTempData; 902 bewusst frei). */
    public static final int COMPONENT_ID = 905;

    private static final int ATTR_PDC_DISTANCE_FRONT = 10;
    private static final int ATTR_PDC_DISTANCE_REAR = 11;

    /* Push-basiert, aber im Gegensatz zu Tag/Nacht/Drive-Select sehr
     * kurzlebig (nur relevant waehrend des Einparkens) - deshalb inklusive
     * Zeitstempel fuer eine eigene Freshness-Pruefung durch Effekte. */
    public volatile boolean valid = false;
    public volatile long lastUpdateMs = 0L;

    /** Distanzwerte vorne (links, links-mitte, rechts-mitte, rechts) - Einheit unbestaetigt, vermutlich cm. */
    public volatile int frontLeft, frontLeftMiddle, frontRightMiddle, frontRight;
    /** Distanzwerte hinten, gleiche Reihenfolge. */
    public volatile int rearLeft, rearLeftMiddle, rearRightMiddle, rearRight;

    public ParkingDistanceData(ICarApplication app) {
        super(app, "App.Car.AmbientFx.ParkingDistance");
    }

    /* ---------------- AbstractCarComponent ---------------- */

    protected void initModels() {}

    protected void deinitModels() {}

    protected void initVisibility() {}

    protected void deinitVisibility() {}

    public int getID() {
        return COMPONENT_ID;
    }

    public String getName() {
        return "AmbientFxParkingDistance";
    }

    public String getCurrentViewOptions() {
        return "n/a";
    }

    public CarDSIAttributesSet[] getDSIAttributesSets() {
        return new CarDSIAttributesSet[] {
            new CarDSIAttributesSet(0, new int[] { ATTR_PDC_DISTANCE_FRONT, ATTR_PDC_DISTANCE_REAR }, new int[0])
        };
    }

    public String getDSIListenerClassName() {
        return "org.dsi.ifc.carparkingsystem.DSICarParkingSystemListener";
    }

    public String getDSIClassName() {
        return "org.dsi.ifc.carparkingsystem.DSICarParkingSystem";
    }

    public boolean isUsingDSI() {
        return true;
    }

    protected void dsiAvailable(boolean available) {
        super.dsiAvailable(available);
        Log.i(TAG, "DSICarParkingSystem available=" + available);
        if (!available) {
            valid = false;
        }
    }

    /* ---------------- DSICarParkingSystemListener ---------------- */

    public void updatePDCDistanceValuesFront(PDCDistanceValuesFrontRear d, int validFlag) {
        if (validFlag != 1 || d == null) {
            return;
        }
        frontLeft = d.left;
        frontLeftMiddle = d.leftMiddle;
        frontRightMiddle = d.rightMiddle;
        frontRight = d.right;
        lastUpdateMs = System.currentTimeMillis();
        valid = true;
        Log.i(TAG, "updatePDCDistanceValuesFront: L=" + frontLeft + " LM=" + frontLeftMiddle
                + " RM=" + frontRightMiddle + " R=" + frontRight);
    }

    public void updatePDCDistanceValuesRear(PDCDistanceValuesFrontRear d, int validFlag) {
        if (validFlag != 1 || d == null) {
            return;
        }
        rearLeft = d.left;
        rearLeftMiddle = d.leftMiddle;
        rearRightMiddle = d.rightMiddle;
        rearRight = d.right;
        lastUpdateMs = System.currentTimeMillis();
        valid = true;
        Log.i(TAG, "updatePDCDistanceValuesRear: L=" + rearLeft + " LM=" + rearLeftMiddle
                + " RM=" + rearRightMiddle + " R=" + rearRight);
    }

    /** true wenn in den letzten 3 s eine Distanz-Meldung kam (PDC aktiv/eingeparkt). */
    public boolean isFresh(long nowMs) {
        return valid && (nowMs - lastUpdateMs) < 3000L;
    }

    /** Kleinster aktuell bekannter Abstand ueber alle acht Sensoren (vorne+hinten). */
    public int minDistance() {
        int m = frontLeft;
        if (frontLeftMiddle < m) m = frontLeftMiddle;
        if (frontRightMiddle < m) m = frontRightMiddle;
        if (frontRight < m) m = frontRight;
        if (rearLeft < m) m = rearLeft;
        if (rearLeftMiddle < m) m = rearLeftMiddle;
        if (rearRightMiddle < m) m = rearRightMiddle;
        if (rearRight < m) m = rearRight;
        return m;
    }

    /* Nicht benoetigt - Interface verlangt aber Implementierung (Java 1.2). */
    public void updateParkingSystemViewOptions(ParkingSystemViewOptions a, int b) {}
    public void updatePDCDefaultParkingMode(int a, int b) {}
    public void updatePDCFrequenceFront(int a, int b) {}
    public void updatePDCFrequenceRear(int a, int b) {}
    public void updatePDCFrequenceRight(int a, int b) {}
    public void updatePDCFrequenceLeft(int a, int b) {}
    public void updatePDCVolumeFront(int a, int b) {}
    public void updatePDCVolumeRear(int a, int b) {}
    public void updatePDCVolumeRight(int a, int b) {}
    public void updatePDCVolumeLeft(int a, int b) {}
    public void updatePDCMute(boolean a, int b) {}
    public void updatePDCSystemOnOff(boolean a, int b) {}
    public void updatePDCTrailerHitched(boolean a, int b) {}
    public void updatePDCDistanceValuesRight(PDCDistanceValuesRightLeft a, int b) {}
    public void updatePDCDistanceValuesLeft(PDCDistanceValuesRightLeft a, int b) {}
    public void updatePDCStatusLevelFront(PDCStatusLevelFrontRear a, int b) {}
    public void updatePDCStatusLevelRear(PDCStatusLevelFrontRear a, int b) {}
    public void updatePDCStatusLevelRight(PDCStatusLevelRightLeft a, int b) {}
    public void updatePDCStatusLevelLeft(PDCStatusLevelRightLeft a, int b) {}
    public void updatePDCOPSAutoActivation(boolean a, int b) {}
    public void updatePDCCrashWarning(PDCCrashWarning a, int b) {}
    public void updatePDCSteeringInformation(PDCSteeringInformation a, int b) {}
    public void updatePDCFlankGuard(boolean a, int b) {}
    public void updatePDCSoundReproduction(PDCSoundReproduction a, int b) {}
    public void updatePDCInfo(PDCInfo a, int b) {}
    public void updatePDCFailure(boolean a, int b) {}
    public void updatePDCDistanceValuesFrontExt(PDCDistanceValuesFrontRearExt a, int b) {}
    public void updatePDCDistanceValuesRearExt(PDCDistanceValuesFrontRearExt a, int b) {}
    public void updatePDCStatusLevelFrontExt(PDCStatusLevelFrontRearExt a, int b) {}
    public void updatePDCStatusLevelRearExt(PDCStatusLevelFrontRearExt a, int b) {}
    public void updatePDCWallDetection(PDCWallDetection a, int b) {}
    public void updatePDCPLAMessage(int a, int b) {}
    public void updatePDCSoundFront(PDCSound a, int b) {}
    public void updatePDCSoundRear(PDCSound a, int b) {}
    public void updatePDCSoundLeft(PDCSound a, int b) {}
    public void updatePDCSoundRight(PDCSound a, int b) {}
    public void updatePDCPLAStatus(PDCPLAStatus a, int b) {}
    public void updatePDCPLABargraph(PDCPLABargraph a, int b) {}
    public void updatePDCPLAParkmodeSelection(int a, int b) {}
    public void updatePDCPLASystemState(PDCPLASystemState a, int b) {}
    public void updatePDCOPSVisualisationPosition(int a, int b) {}
    public void updatePDCOffroadMode(boolean a, int b) {}
    public void updatePDCParkboxVisualisation(boolean a, int b) {}
    public void updateVPSFollowUpTime(int a, int b) {}
    public void updateVPSVideoInfo(VPSVideoInfo a, int b) {}
    public void updateVPSColor(int a, int b) {}
    public void updateVPSContrast(int a, int b) {}
    public void updateVPSBrightness(int a, int b) {}
    public void updateVPSDefaultModeRV(VPSDefaultMode a, int b) {}
    public void updateVPSDefaultModeSV(VPSDefaultMode a, int b) {}
    public void updateVPSDefaultModeFV(VPSDefaultMode a, int b) {}
    public void updateVPSDefaultModeBV(VPSDefaultMode a, int b) {}
    public void updateVPSDefaultView(int a, int b) {}
    public void updateVPSDynamicParkingMode(VPSDynParkingMode a, int b) {}
    public void updateVPSOPSOverlay(VPSOPSOverlay a, int b) {}
    public void updateVPSSystemOnOff(boolean a, int b) {}
    public void updateVPSFailure(boolean a, int b) {}
    public void updateVPSExtCamConfig(int a, int b) {}
    public void updateVPSExtCamManActivation(boolean a, int b) {}
    public void updateVPS3DBirdview(int a, int b, int c) {}
    public void updateVPSSystemState(boolean a, int b) {}
    public void updateVPSCameraStates(VPSCameraStates a, int b) {}
    public void updateParkingPopupContent(DisplayContent a, int b) {}
    public void requestParkingPopup(DisplayContent a) {}
    public void acknowledgeParkingPopup(DisplayContent a) {}
    public void responseLifeMonitoring(boolean a) {}
    public void acknowledgePdcSetFactoryDefault(boolean a) {}
    public void acknowledgeVpsSetFactoryDefault(boolean a) {}
    public void updateARAFailure(boolean a, int b) {}
    public void updateARAInfo(ARAInfo a, int b) {}
    public void updateARACurrentTrailerAngle(ARACurrentTrailerAngle a, int b) {}
    public void updateARATargetTrailerAngle(int a, int b) {}
    public void updatePDCManeuverAssistConfig(int a, int b) {}
    public void updatePDCManeuverAssist(boolean a, int b) {}
    public void updatePDCManeuverAssistState(PDCManeuverAssistState a, int b) {}
    public void updatePDCManeuverAssistMessage(int a, int b) {}
    public void updatePDCIPAMessage(int a, int b) {}
    public void updatePDCContinueDrivingAssist(int a, int b) {}
    public void updatePDCIpaConfig(int a, int b) {}
    public void updatePDCPiloPaSystemState(PDCPiloPaSystemState a, int b) {}
    public void updateVPSCameraCleaning(VPSCameraCleaning a, int b) {}
    public void updateVPSRimProtection(VPSRimProtection a, int b) {}
    public void updateWCViewOptions(WCViewOptions a, int b) {}
    public void updateWCSystemOnOff(boolean a, int b) {}
    public void updateWCAutoActivation(boolean a, int b) {}
    public void updateWCPopupContent(int a, int b) {}
    public void updateWCMessage(int a, int b) {}
    public void updateWCPanelPosition(WCPanelInfo a, int b) {}
    public void acknowledgeWCSetFactoryDefault(boolean a) {}
    public void requestWCPopup(int a) {}
    public void acknowledgeWCPopup(int a) {}
    public void updateWCPanelListUpdateInfo(CarArrayListUpdateInfo a, int[] b, int c) {}
    public void updateWCPanelListTotalNumberOfElements(int a, int b) {}
    public void updateWCVehiclePanelInfo(WCVehiclePanelInfo a, int b) {}
    public void updateWCPinPukState(WCPinPukState a, int b) {}
    public void updateWCScanningProgress(int a, int b) {}
    public void updateWCSoftwareUpdateProgress(int a, int b) {}
    public void acknowledgeWCEnterPinPuk(int a) {}
    public void acknowledgeWCScanning(int a) {}
    public void acknowledgeWCPairing(int a) {}
    public void acknowledgeWCSoftwareUpdate(int a) {}
    public void acknowledgeWCChangePin(int a) {}
    public void acknowledgeWCChangePanelName(int a) {}
    public void responseWCPanelList(CarArrayListUpdateInfo a, WCPanelListRecord[] b) {}
}
