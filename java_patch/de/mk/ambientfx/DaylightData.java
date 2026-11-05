/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
 *
 * AmbientFX - Tag/Nacht-Quelle.
 *
 * Bewusst NICHT VehicleData/DSICarVehicleStates (lowBeamHeadlightIsActive
 * blieb im Live-Test durchgehend false, unabhaengig vom Lichtschalter).
 * Stattdessen derselbe DSI-Dienst und dasselbe Attribut, das
 * de.audi.app.terminalmode.NightDayModeHandler nachweislich fuer die
 * echte Tag/Nacht-Umschaltung von CarPlay nutzt (per Decompile bestaetigt):
 * "org.dsi.ifc.generalvehiclestates.DSIGeneralVehicleStates", Attribut 16,
 * Methode updateDisplayDayNightDesign(boolean isNight, int validFlag).
 * Im Fahrzeug getestet: Lichtschalter-Wechsel toggelt das Signal zuverlaessig.
 *
 * Diese Klasse nutzt exakt denselben Mechanismus wie VehicleData (eine
 * AbstractCarComponent, die sich als OSGi-DSIListener registriert), nur
 * gegen den anderen DSI-Dienst und Attribut 16 statt 14/15. Die vielen
 * Leermethoden unten sind Pflicht (Java 1.2, Interface verlangt alle
 * Methoden von DSIGeneralVehicleStatesListener), uns interessiert nur
 * updateDisplayDayNightDesign.
 *
 * Java 1.2 kompatibel.
 */
package de.mk.ambientfx;

import de.audi.app.car.common.app.ICarApplication;
import de.audi.app.car.common.comp.AbstractCarComponent;
import de.audi.app.car.common.comp.CarDSIAttributesSet;
import org.dsi.ifc.generalvehiclestates.AirbagData;
import org.dsi.ifc.generalvehiclestates.DSIGeneralVehicleStatesListener;
import org.dsi.ifc.generalvehiclestates.TLOViewOptions;
import org.dsi.ifc.generalvehiclestates.TankInfo;
import org.dsi.ifc.global.CarViewOption;

public class DaylightData extends AbstractCarComponent implements DSIGeneralVehicleStatesListener {

    private static final String TAG = "DaylightData";

    /** Eigene Komponenten-ID (Stock-IDs liegen bei 1..~60; VehicleData nutzt 900). */
    public static final int COMPONENT_ID = 901;

    /** Attribut, an dem updateDisplayDayNightDesign haengt - aus NightDayModeHandler (CarPlay) uebernommen. */
    private static final int ATTR_DAY_NIGHT_DESIGN = 16;

    /* Push-basiert (aendert sich selten) - kein Freshness-Timeout wie bei VehicleData noetig,
     * der zuletzt gemeldete Wert bleibt gueltig bis zur naechsten echten Aenderung. */
    public volatile boolean valid = false;
    public volatile boolean night = false;

    public DaylightData(ICarApplication app) {
        super(app, "App.Car.AmbientFx.Daylight");
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
        return "AmbientFxDaylight";
    }

    public String getCurrentViewOptions() {
        return "n/a";
    }

    public CarDSIAttributesSet[] getDSIAttributesSets() {
        return new CarDSIAttributesSet[] {
            new CarDSIAttributesSet(0, new int[] { ATTR_DAY_NIGHT_DESIGN }, new int[0])
        };
    }

    public String getDSIListenerClassName() {
        return "org.dsi.ifc.generalvehiclestates.DSIGeneralVehicleStatesListener";
    }

    public String getDSIClassName() {
        return "org.dsi.ifc.generalvehiclestates.DSIGeneralVehicleStates";
    }

    public boolean isUsingDSI() {
        return true;
    }

    protected void dsiAvailable(boolean available) {
        super.dsiAvailable(available);
        Log.i(TAG, "DSIGeneralVehicleStates available=" + available);
        if (!available) {
            valid = false;
        }
    }

    /* ---------------- DSIGeneralVehicleStatesListener ---------------- */

    public void updateDisplayDayNightDesign(boolean isNight, int validFlag) {
        if (validFlag != 1) {
            return;
        }
        night = isNight;
        valid = true;
        Log.i(TAG, "updateDisplayDayNightDesign: " + (isNight ? "night" : "day"));
    }

    /* Nicht benoetigt - Interface verlangt aber Implementierung (Java 1.2). */
    public void updateAirbagData(AirbagData v, int f) {}
    public void updateTankInfo(TankInfo v, int f) {}
    public void updateDimmedHeadlight(boolean v, int f) {}
    public void updateAcousticParkingSystem(boolean v, int f) {}
    public void updateReverseGear(boolean v, int f) {}
    public void updateVehicleStandstill(boolean v, int f) {}
    public void updateCarVelocityThreshold(boolean v, int f) {}
    public void updateTVVelocityThreshold(boolean v, int f) {}
    public void updateHDDVelocityThreshold(boolean v, int f) {}
    public void updateBrowserSlideShowVelocityThreshold(boolean v, int f) {}
    public void updateBrowserBordBookVelocityThreshold(boolean v, int f) {}
    public void updateBrowserTravelAgentVelocityThreshold(boolean v, int f) {}
    public void updateBrowserWebVelocityThreshold(boolean v, int f) {}
    public void updateBWSVelocityThreshold(boolean v, int f) {}
    public void updateRadiotextVelocityThreshold(boolean v, int f) {}
    public void updateBTBondingVelocityThreshold(boolean v, int f) {}
    public void updateMessagingVelocityThreshold(boolean v, int f) {}
    public void updateDestinationInputVelocityThreshold(boolean v, int f) {}
    public void updateDSSSViewOption(CarViewOption v, int f) {}
    public void updateServiceKeyData(byte[] v, int f) {}
    public void updateServiceKeyViewOption(CarViewOption v, int f) {}
    public void updatePersonalizationStatus(boolean v, int f, int g) {}
    public void updateTLOViewOptions(TLOViewOptions v, int f) {}
    public void updateEmergencyAssistVolLowering(int v, int f) {}
    public void updateParkingBrake(boolean v, int f) {}
    public void updateAppConnectTrigger(int v, int f) {}
    public void updateSTPState(int v, int f) {}
    public void updateAutomaticGearShiftTransMode(int v, int f) {}
}
