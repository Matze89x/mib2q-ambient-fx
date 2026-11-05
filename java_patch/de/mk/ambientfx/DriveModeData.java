/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
 *
 * AmbientFX - Audi-Drive-Select-Quelle (Comfort/Auto/Dynamic/Eco/Individual).
 *
 * Exakt derselbe Mechanismus wie VehicleData/DaylightData (eine
 * AbstractCarComponent, die sich als OSGi-DSIListener registriert), nur
 * gegen "org.dsi.ifc.cardrivingcharacteristics.DSICarDrivingCharacteristics",
 * Attribut 13 (ATTR_CHARISMAACTIVEPROFILE), Methode
 * updateCharismaActiveProfile(int profile, int validFlag). Per javap gegen
 * vendor/lsd.jar bestaetigt (Konstanten): COMFORT=1, AUTO_NORMAL=2,
 * DYNAMIC=3, EFFICIENCY=5 ("Eco"), INDIVIDUAL=7. Das Attribut war schon
 * vorher in der Projektdoku notiert ("Drive-Select-Modus: Attr. 13"), hier zum
 * ersten Mal tatsaechlich angebunden (fuer MomentumSelect). LIVE bestaetigt
 * (26.09.2026, Log-Mitschnitt am Drehregler durchgeschaltet): 1=comfort,
 * 2=auto, 3=dynamic, 5=efficiency, 7=individual - exakt wie per javap
 * erwartet, keine Abweichung.
 *
 * Bewusst eine eigene, generische Fahrzeugdaten-Komponente (wie VehicleData/
 * DaylightData) statt Teil von Momentum/MomentumSelect - damit auch
 * kuenftige Effekte den Drive-Select-Modus abfragen koennen, ohne von
 * MomentumSelect abzuhaengen. Zugriff fuer Effekte ueber
 * AmbientFx.getInstance().getDriveMode() - bewusst NICHT als zusaetzlicher
 * Parameter in Effect.tick(), damit Momentum unveraendert bleibt.
 *
 * Die vielen Leermethoden unten sind Pflicht (Java 1.2, Interface verlangt
 * alle Methoden von DSICarDrivingCharacteristicsListener - ein grosses
 * Interface, das auch Feder/Daempfer/Spoiler/Sound mit abdeckt), uns
 * interessiert nur updateCharismaActiveProfile.
 *
 * Java 1.2 kompatibel.
 */
package de.mk.ambientfx;

import de.audi.app.car.common.app.ICarApplication;
import de.audi.app.car.common.comp.AbstractCarComponent;
import de.audi.app.car.common.comp.CarDSIAttributesSet;
import org.dsi.ifc.cardrivingcharacteristics.CharismaListUpdateInfo;
import org.dsi.ifc.cardrivingcharacteristics.CharismaProgButton;
import org.dsi.ifc.cardrivingcharacteristics.CharismaSetupTableWithOptionMask;
import org.dsi.ifc.cardrivingcharacteristics.CharismaSetupTableWithoutOptionMask;
import org.dsi.ifc.cardrivingcharacteristics.CharismaViewOptions;
import org.dsi.ifc.cardrivingcharacteristics.DSICarDrivingCharacteristicsListener;
import org.dsi.ifc.cardrivingcharacteristics.SoundViewOptions;
import org.dsi.ifc.cardrivingcharacteristics.SpoilerState;
import org.dsi.ifc.cardrivingcharacteristics.SpoilerViewOptions;
import org.dsi.ifc.cardrivingcharacteristics.SuspensionControlActuatorInfo;
import org.dsi.ifc.cardrivingcharacteristics.SuspensionControlAirProfiles;
import org.dsi.ifc.cardrivingcharacteristics.SuspensionControlDRCProfiles;
import org.dsi.ifc.cardrivingcharacteristics.SuspensionControlHeightInfo;
import org.dsi.ifc.cardrivingcharacteristics.SuspensionControlOperationMessages;
import org.dsi.ifc.cardrivingcharacteristics.SuspensionControleABCPreview;
import org.dsi.ifc.cardrivingcharacteristics.SuspensionControlViewOptions;
import org.dsi.ifc.cardrivingcharacteristics.TADVehicleInfo;
import org.dsi.ifc.cardrivingcharacteristics.TADViewOptions;

public class DriveModeData extends AbstractCarComponent implements DSICarDrivingCharacteristicsListener {

    private static final String TAG = "DriveModeData";

    /** Eigene Komponenten-ID (VehicleData=900, DaylightData=901; 902 bewusst
     *  frei gelassen - dort haengt in einer anderen, parallel laufenden
     *  Session noch ein KeyProbe-Entwurf im Git-Stash). */
    public static final int COMPONENT_ID = 903;

    /** org.dsi.ifc.cardrivingcharacteristics.DSICarDrivingCharacteristics.ATTR_CHARISMAACTIVEPROFILE. */
    private static final int ATTR_CHARISMA_ACTIVE_PROFILE = 13;

    /* Werte 1:1 aus DSICarDrivingCharacteristics.CHARISMAPROFILES_* (javap
     * gegen vendor/lsd.jar) - hier dupliziert, damit Effekte nicht extra
     * das grosse DSI-Interface importieren muessen. */
    public static final int PROFILE_COMFORT = 1;
    public static final int PROFILE_AUTO_NORMAL = 2;
    public static final int PROFILE_DYNAMIC = 3;
    public static final int PROFILE_EFFICIENCY = 5; /* "Eco" im Menue */
    public static final int PROFILE_INDIVIDUAL = 7;
    public static final int PROFILE_UNKNOWN = -1;

    /* Push-basiert (aendert sich selten) - wie DaylightData kein Freshness-
     * Timeout noetig, letzter gemeldeter Wert bleibt bis zur naechsten
     * echten Aenderung gueltig. */
    public volatile boolean valid = false;
    public volatile int profile = PROFILE_UNKNOWN;

    public DriveModeData(ICarApplication app) {
        super(app, "App.Car.AmbientFx.DriveMode");
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
        return "AmbientFxDriveMode";
    }

    public String getCurrentViewOptions() {
        return "n/a";
    }

    public CarDSIAttributesSet[] getDSIAttributesSets() {
        return new CarDSIAttributesSet[] {
            new CarDSIAttributesSet(0, new int[] { ATTR_CHARISMA_ACTIVE_PROFILE }, new int[0])
        };
    }

    public String getDSIListenerClassName() {
        return "org.dsi.ifc.cardrivingcharacteristics.DSICarDrivingCharacteristicsListener";
    }

    public String getDSIClassName() {
        return "org.dsi.ifc.cardrivingcharacteristics.DSICarDrivingCharacteristics";
    }

    public boolean isUsingDSI() {
        return true;
    }

    protected void dsiAvailable(boolean available) {
        super.dsiAvailable(available);
        Log.i(TAG, "DSICarDrivingCharacteristics available=" + available);
        if (!available) {
            valid = false;
        }
    }

    /* ---------------- DSICarDrivingCharacteristicsListener ---------------- */

    public void updateCharismaActiveProfile(int p, int validFlag) {
        if (validFlag != 1) {
            return;
        }
        profile = p;
        valid = true;
        Log.i(TAG, "updateCharismaActiveProfile: " + p);
    }

    /* Nicht benoetigt - Interface verlangt aber Implementierung (Java 1.2). */
    public void updateSuspensionControlViewOptions(SuspensionControlViewOptions v, int f) {}
    public void updateSuspensionControlLiftMode(boolean v, int f) {}
    public void updateSuspensionControlCarJackMode(boolean v, int f) {}
    public void updateSuspensionControlTrailerMode(boolean v, int f) {}
    public void updateSuspensionControlLoadingMode(boolean v, int f) {}
    public void updateSuspensionControlActiveProfile(int v, int f) {}
    public void updateSuspensionControlAccessibleAirProfiles(SuspensionControlAirProfiles v, int f) {}
    public void updateSuspensionControlAccessibleDRCProfiles(SuspensionControlDRCProfiles v, int f) {}
    public void updateSuspensionControlVehicleStatus(int v, int f) {}
    public void updateSuspensionControlCurrentLevel(int v, int f) {}
    public void updateSuspensionControlTargetLevel(int v, int f) {}
    public void updateSuspensionControlHeightInfo(SuspensionControlHeightInfo v, int f) {}
    public void updateSuspensionControlOperationMessages(SuspensionControlOperationMessages v, int f) {}
    public void updateSuspensionControlSnowChainMode(boolean v, int f) {}
    public void updateSuspensionControlVehicleStateControl(boolean v, int f) {}
    public void updateSuspensionControlActiveMode(int v, boolean w, int f) {}
    public void updateeABCEasyEntry(boolean v, int f) {}
    public void updateeABCPitchControl(boolean v, int f) {}
    public void updateeABCSpecialPosition(boolean v, int f) {}
    public void updateeABCPreview(int v, int f) {}
    public void updateeABCPreviewState(SuspensionControleABCPreview v, int f) {}
    public void updateSuspensionControlActuatorInfo(SuspensionControlActuatorInfo v, int f) {}
    public void updateCharismaViewOptions(CharismaViewOptions v, int f) {}
    public void updateCharismaActiveOperationMode(int v, int f) {}
    public void updateCharismaListUpdateInfo(CharismaListUpdateInfo v, int f) {}
    public void updateCharismaContent(int v, int f) {}
    public void updateCharismaTrailerDetection(boolean v, int f) {}
    public void updateCharismaTrailerSetting(boolean v, int f) {}
    public void updateCharismaProgButton(CharismaProgButton v, int f) {}
    public void responseCharismaListWithOptionMask(int v, int f, int g, CharismaSetupTableWithOptionMask[] w) {}
    public void responseCharismaListWithoutOptionMask(int v, int f, int g, CharismaSetupTableWithoutOptionMask[] w) {}
    public void requestCharismaPopup(int v) {}
    public void acknowledgeCharismaPopup(int v) {}
    public void acknowledgeCharismaSetFactoryDefault(boolean v) {}
    public void updateCharismaSound(boolean v, int f) {}
    public void updateTADViewOptions(TADViewOptions v, int f) {}
    public void updateTADContent(int v, int f) {}
    public void requestTADPopup(int v) {}
    public void acknowledgeTADPopup(int v) {}
    public void acknowledgeTADSetFactoryDefault(boolean v) {}
    public void acknowledgeTADMaxMinAngleReset(boolean v) {}
    public void updateTADVehicleInfo(TADVehicleInfo v, int f) {}
    public void updateTADCurrentRollAngle(float v, int f) {}
    public void updateTADCurrentPitchAngle(float v, int f) {}
    public void updateTADPosMaxRollAngle(float v, int f) {}
    public void updateTADNegMaxRollAngle(float v, int f) {}
    public void updateTADPosMaxPitchAngle(float v, int f) {}
    public void updateTADNegMaxPitchAngle(float v, int f) {}
    public void updateSpoilerViewOptions(SpoilerViewOptions v, int f) {}
    public void updateSpoilerPositionSelection(int v, int f) {}
    public void updateSpoilerState(SpoilerState v, int f) {}
    public void updateSpoilerActuation(boolean v, int f) {}
    public void updateSpoilerMessages(int v, int f) {}
    public void updateSpoilerSystemOnOff(boolean v, int f) {}
    public void acknowledgeSpoilerSetFactoryDefault(boolean v) {}
    public void updateSoundViewOptions(SoundViewOptions v, int f) {}
    public void updateSoundStyle(int v, int f) {}
    public void updateSoundSystemOnOff(boolean v, int f) {}
    public void updateSoundOnOff(boolean v, int f) {}
    public void acknowledgeSoundSetFactoryDefault(boolean v) {}
}
