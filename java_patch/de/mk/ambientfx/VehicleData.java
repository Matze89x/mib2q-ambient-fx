/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
 *
 * AmbientFX - Fahrzeugdaten-Quelle (Drehzahl, Geschwindigkeit, Gaspedal,
 * Schaltblitz, Gang, Bremse, Lenkwinkel ...).
 *
 * Nutzt exakt den Mechanismus der Stock-Car-App: eine AbstractCarComponent
 * registriert sich als OSGi-DSIListener und trackt den DSI-Proxy
 * "org.dsi.ifc.carvehiclestates.DSICarVehicleStates". Sobald der Proxy da
 * ist, abonniert die Basisklasse die Attribute aus getDSIAttributesSets():
 *   14 = DynamicVehicleInfoHighFrequent (rpm, speed, accel, shiftLight,
 *        wheelAngle seit 26.09.2026 ...)
 *   15 = DynamicVehicleInfoMidFrequent  (gear, recommendedGear, braking ...)
 *
 * Tag/Nacht kommt NICHT von hier (lowBeamHeadlightIsActive blieb im
 * Live-Test immer false), sondern von DaylightData - siehe dort.
 *
 * Seit 25.09.2026 zusaetzlich Attribut 6 (ATTR_KEYDATA) abonniert:
 * updateKeyData liefert KeyData.activeKey - welcher Fahrzeugschluessel
 * gerade benutzt wird. Fuer AmbientFx.saveState()/loadState() (je-Schluessel
 * statt nur ein globaler gemerkter Effekt) - siehe dort. LIVE bestaetigt
 * (26.09.2026): activeKey=2 wurde zuverlaessig geliefert und fuer den
 * Datei-Namen der Persistenz benutzt. Noch nicht getestet: ob ein
 * TATSAECHLICHER Schluesselwechsel (zweiter Schluessel am selben Fahrzeug)
 * den erwarteten anderen Wert liefert und den jeweils eigenen Stand laedt.
 *
 * Die Komponente wird von der AmbientFx-Engine bei Bedarf (erster reaktiver
 * Effekt) erzeugt und per init() gestartet. Alle Werte sind volatile und
 * werden von der Engine im eigenen Thread gelesen.
 *
 * Java 1.2 kompatibel.
 */
package de.mk.ambientfx;

import de.audi.app.car.common.app.ICarApplication;
import de.audi.app.car.common.comp.AbstractCarComponent;
import de.audi.app.car.common.comp.CarDSIAttributesSet;
import org.dsi.ifc.carvehiclestates.DSICarVehicleStatesListener;
import org.dsi.ifc.carvehiclestates.DynamicVehicleInfoHighFrequent;
import org.dsi.ifc.carvehiclestates.DynamicVehicleInfoHighFrequentViewOptions;
import org.dsi.ifc.carvehiclestates.DynamicVehicleInfoMidFrequent;
import org.dsi.ifc.carvehiclestates.DynamicVehicleInfoMidFrequentViewOptions;
import org.dsi.ifc.carvehiclestates.DynamicVehicleInfoSCR;
import org.dsi.ifc.carvehiclestates.KeyData;
import org.dsi.ifc.carvehiclestates.OilLevelData;
import org.dsi.ifc.carvehiclestates.SemiStaticDataViewOptions;
import org.dsi.ifc.carvehiclestates.SemiStaticVehicleData;
import org.dsi.ifc.carvehiclestates.VehicleInfoViewOptions;
import org.dsi.ifc.global.CarViewOption;

public class VehicleData extends AbstractCarComponent implements DSICarVehicleStatesListener {

    private static final String TAG = "VehicleData";

    /** Eigene Komponenten-ID (Stock-IDs liegen bei 1..~60; 900 ist frei). */
    public static final int COMPONENT_ID = 900;

    private static final int ATTR_HIGH_FREQUENT = 14;
    private static final int ATTR_MID_FREQUENT = 15;
    private static final int ATTR_KEY_DATA = 6;

    /* Werte 1:1 aus DSICarVehicleStates.GEARTRANSMISSIONMODE_* (javap gegen
     * vendor/lsd.jar) - hier dupliziert (wie DriveModeData.PROFILE_*), damit
     * Effekte nicht extra das grosse DSI-Interface importieren muessen. Seit
     * 27.09.2026 fuer die Gangwahlhebel-Stellung D/S genutzt (siehe
     * Momentum/MomentumSelect, D->S-Eintrittssequenz). */
    public static final int GEAR_TRANS_MODE_D = 4;
    public static final int GEAR_TRANS_MODE_S = 5;

    /* ---- letzte bekannte Werte (volatile: Schreiber = DSI-Thread, Leser = FX-Thread) ---- */
    public volatile boolean valid = false;
    public volatile long lastUpdateMs = 0L;

    public volatile int engineRpm = 0;
    public volatile float speedKmh = 0f;
    public volatile int speedUnit = 0;
    public volatile float longAccel = 0f;      /* m/s^2, positiv = beschleunigen */
    public volatile float latAccel = 0f;
    public volatile int acceleratorPct = 0;    /* 0..100 */
    public volatile int powermeter = 0;
    public volatile int shiftLight = 0;        /* 0 = aus, >0 = Schaltempfehlung/Blitz */
    public volatile float brakePressure = 0f;
    /** Lenkwinkel in Grad, Vorzeichen je nach Einschlagrichtung (per luka-collection
     *  gemessen: ca. +-520). Seit 26.09.2026 fuer den Effekt "Drive" - per javap
     *  gegen unser eigenes vendor/lsd.jar (MU1329) bestaetigt identisch vorhanden. */
    public volatile int wheelAngle = 0;

    public volatile int currentGear = 0;
    public volatile int recommendedGear = 0;
    public volatile boolean driverBraking = false;
    public volatile boolean kickDown = false;
    public volatile int gearTransMode = 0;      /* P/R/N/D/S ... siehe GEAR_TRANS_MODE_* oben */
    public volatile int blinkingState = 0;      /* 0 keine, 1 links, 2 rechts, 3 beide */

    /** Welcher Fahrzeugschluessel aktiv ist (KeyData.activeKey) - -1 = noch
     *  kein Update erhalten. Fuer je-Schluessel-Persistenz, siehe AmbientFx. */
    public volatile int activeKey = -1;

    public VehicleData(ICarApplication app) {
        super(app, "App.Car.AmbientFx.Vehicle");
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
        return "AmbientFxVehicle";
    }

    public String getCurrentViewOptions() {
        return "n/a";
    }

    public CarDSIAttributesSet[] getDSIAttributesSets() {
        return new CarDSIAttributesSet[] {
            new CarDSIAttributesSet(0, new int[] { ATTR_HIGH_FREQUENT, ATTR_MID_FREQUENT, ATTR_KEY_DATA }, new int[0])
        };
    }

    public String getDSIListenerClassName() {
        return "org.dsi.ifc.carvehiclestates.DSICarVehicleStatesListener";
    }

    public String getDSIClassName() {
        return "org.dsi.ifc.carvehiclestates.DSICarVehicleStates";
    }

    public boolean isUsingDSI() {
        return true;
    }

    protected void dsiAvailable(boolean available) {
        super.dsiAvailable(available);
        Log.i(TAG, "DSICarVehicleStates available=" + available);
        if (!available) {
            valid = false;
        }
    }

    /* ---------------- DSICarVehicleStatesListener ---------------- */

    public void updateDynamicVehicleInfoHighFrequent(DynamicVehicleInfoHighFrequent d, int validFlag) {
        if (validFlag != 1 || d == null) {
            return;
        }
        engineRpm = d.engineSpeed;
        if (d.vehicleSpeed != null) {
            speedKmh = d.vehicleSpeed.speedValue;
            speedUnit = d.vehicleSpeed.speedUnit;
        }
        longAccel = d.longitudinalAcceleration;
        latAccel = d.lateralAcceleration;
        acceleratorPct = d.acceleratorPosition;
        powermeter = d.powermeter;
        shiftLight = d.shiftLight;
        brakePressure = d.brakePressure;
        wheelAngle = d.wheelAngle;
        lastUpdateMs = System.currentTimeMillis();
        valid = true;
    }

    public void updateDynamicVehicleInfoMidFrequent(DynamicVehicleInfoMidFrequent d, int validFlag) {
        if (validFlag != 1 || d == null) {
            return;
        }
        currentGear = d.currentGear;
        recommendedGear = d.recommendedGear;
        driverBraking = d.driverIsBraking;
        kickDown = d.acceleratorKickDown;
        gearTransMode = d.automaticGearShiftTransMode;
        blinkingState = d.blinkingState;
    }

    public void updateKeyData(KeyData v, int f) {
        if (f != 1 || v == null) {
            return;
        }
        activeKey = v.activeKey;
        Log.i(TAG, "updateKeyData: activeKey=" + activeKey);
    }

    /* Nicht benoetigt - Interface verlangt aber Implementierung (Java 1.2). */
    public void updateOilLevelViewOption(CarViewOption v, int f) {}
    public void updateOilLevelData(OilLevelData v, int f) {}
    public void updateVINViewOption(CarViewOption v, int f) {}
    public void updateVINData(String v, int f) {}
    public void updateKeyViewOption(CarViewOption v, int f) {}
    public void updateDrvSchoolSystem(boolean v, int f) {}
    public void updateVehicleInfoViewOptions(VehicleInfoViewOptions v, int f) {}
    public void updateDynamicVehicleInfoHighFrequentViewOptions(DynamicVehicleInfoHighFrequentViewOptions v, int f) {}
    public void updateDynamicVehicleInfoMidFrequentViewOptions(DynamicVehicleInfoMidFrequentViewOptions v, int f) {}
    public void updateSemiStaticVehicleDataViewOptions(SemiStaticDataViewOptions v, int f) {}
    public void updateSemiStaticVehicleData(SemiStaticVehicleData v, int f) {}
    public void updateDynamicVehicleInfoSCR(DynamicVehicleInfoSCR v, int f) {}

    /* ---------------- Hilfen fuer Effekte ---------------- */

    /** true wenn in den letzten 3 s Daten kamen. */
    public boolean isFresh(long nowMs) {
        return valid && (nowMs - lastUpdateMs) < 3000L;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer(120);
        sb.append("rpm=").append(engineRpm).append(" v=").append(speedKmh)
          .append(" acc=").append(acceleratorPct).append(" shift=").append(shiftLight)
          .append(" gear=").append(currentGear).append('/').append(recommendedGear)
          .append(" brake=").append(driverBraking).append(" wheel=").append(wheelAngle)
          .append(" key=").append(activeKey);
        return sb.toString();
    }
}
