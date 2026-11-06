/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
 *
 * AmbientFX - Aussentemperatur-Quelle (fuer den Effekt "Atmosphere").
 *
 * Exakt derselbe Mechanismus wie VehicleData/DaylightData/DriveModeData
 * (eine AbstractCarComponent, die sich als OSGi-DSIListener registriert),
 * nur gegen "org.dsi.ifc.carkombi.DSICarKombi" (der Bordcomputer-Dienst,
 * der auch Verbrauch/Reichweite/HUD/Digitaltacho bedient), Attribut 65
 * (ATTR_BCOUTSIDETEMPERATURE), Methode
 * updateBCOutsideTemperature(CarBCTemperature temp, int validFlag). Per
 * javap gegen vendor/lsd.jar bestaetigt: dieselbe Temperatur, die im
 * Kombi-Display als kleines Thermometer-Icon angezeigt wird.
 *
 * ACHTUNG: anders als DaylightData/DriveModeData ist dieses Attribut noch
 * NICHT live im Fahrzeug getestet - nur per Decompile verifiziert
 * (Interface/Attribut/Methode existieren nachweislich, aber ob es auf
 * diesem Fahrzeug tatsaechlich Updates liefert, ist noch offen). Effekte,
 * die das nutzen, muessen "valid" pruefen und mit vernuenftigem Fallback
 * (z.B. Zimmertemperatur) umgehen koennen, siehe Atmosphere.java.
 *
 * temperatureUnit folgt demselben Muster wie VehicleData.speedUnit: 0 =
 * bereits Celsius, 1 = Fahrenheit (wird hier direkt nach Celsius
 * umgerechnet, damit Effekte nur noch mit Celsius rechnen muessen).
 *
 * Das struct-Feld "state" (in CarBCTemperature) wird seit 26.09.2026 zur
 * Gueltigkeitspruefung mit herangezogen (zusaetzlich zu validFlag, nicht
 * statt dessen). Bedeutung laut Lukas AmbientProbe.java (luka-collection,
 * dort fuer das strukturell gleiche CarViewOption.state dokumentiert -
 * gleicher int-Wertebereich, gleiche Kombi aus DSI-Attribut-Semantik):
 * 0 = NOT_EXISTING (Sensor nicht verbaut), 1 = NOT_ACCESSIBLE (kurzzeitig
 * nicht erreichbar, z.B. "---"-Anzeige im Kombi), 2 = NORMAL_OPERATION
 * (Wert gueltig). NICHT live auf diesem Fahrzeug bestaetigt (wie der ganze
 * Sensor), aber strenger als vorher nur validFlag zu pruefen - schuetzt
 * Atmosphere.java vor einem "gueltigen" validFlag mit trotzdem noch
 * unbrauchbarem/kurzzeitigem Wert.
 *
 * Die vielen Leermethoden unten sind Pflicht (Java 1.2, Interface verlangt
 * alle ~130 Methoden von DSICarKombiListener - der Bordcomputer deckt sehr
 * viel ab, Verbrauch/Reichweite/HUD/Digitalanzeigen/Kompass...), uns
 * interessiert nur updateBCOutsideTemperature.
 *
 * Java 1.2 kompatibel.
 */
package de.mk.ambientfx;

import de.audi.app.car.common.app.ICarApplication;
import de.audi.app.car.common.comp.AbstractCarComponent;
import de.audi.app.car.common.comp.CarDSIAttributesSet;
import org.dsi.ifc.carkombi.BCAverageRecoveredEnergy;
import org.dsi.ifc.carkombi.BCComfortPowerConsumption;
import org.dsi.ifc.carkombi.BCCycleGeneralData;
import org.dsi.ifc.carkombi.BCIndications;
import org.dsi.ifc.carkombi.BCLongTermGeneralData;
import org.dsi.ifc.carkombi.BCMaxValues;
import org.dsi.ifc.carkombi.BCMenueConfiguration;
import org.dsi.ifc.carkombi.BCRefuelVolume;
import org.dsi.ifc.carkombi.BCResetTimeStamp;
import org.dsi.ifc.carkombi.BCShortTermGeneralData;
import org.dsi.ifc.carkombi.BCSpeedWarningSettings;
import org.dsi.ifc.carkombi.BCStatisticsAC;
import org.dsi.ifc.carkombi.BCStatisticsConfig;
import org.dsi.ifc.carkombi.BCStatisticsDistanceEU;
import org.dsi.ifc.carkombi.BCStatisticsRE;
import org.dsi.ifc.carkombi.BCStatisticsZE;
import org.dsi.ifc.carkombi.BCTankLevel;
import org.dsi.ifc.carkombi.BCVehicleStateUpdateInfoAH;
import org.dsi.ifc.carkombi.BCViewOptions;
import org.dsi.ifc.carkombi.BCZeroEmissionAbsoluteDistance;
import org.dsi.ifc.carkombi.BCZeroEmissionAbsoluteTime;
import org.dsi.ifc.carkombi.BCZeroEmissionRelative;
import org.dsi.ifc.carkombi.DCAdditionalInfo;
import org.dsi.ifc.carkombi.DCAdditionalInstrument;
import org.dsi.ifc.carkombi.DCAdditionalInstrument2;
import org.dsi.ifc.carkombi.DCDisplayDependency;
import org.dsi.ifc.carkombi.DCDisplayPresetsListRecord;
import org.dsi.ifc.carkombi.DCDisplayViewConfiguration;
import org.dsi.ifc.carkombi.DCDisplayedAdditionalInfos;
import org.dsi.ifc.carkombi.DCElementContentSelectionListRA1;
import org.dsi.ifc.carkombi.DCElementContentSelectionListRA2;
import org.dsi.ifc.carkombi.DCElementContentSelectionListUpdateInfo;
import org.dsi.ifc.carkombi.DCMainItems;
import org.dsi.ifc.carkombi.DCViewOptions;
import org.dsi.ifc.carkombi.DSICarKombiListener;
import org.dsi.ifc.carkombi.HUDCollectiveTopics;
import org.dsi.ifc.carkombi.HUDContent;
import org.dsi.ifc.carkombi.HUDSectionConfigListRecord;
import org.dsi.ifc.carkombi.HUDViewOptions;
import org.dsi.ifc.carkombi.ListDynValues;
import org.dsi.ifc.carkombi.ListWarningIDsDynValues;
import org.dsi.ifc.carkombi.SIADistanceData;
import org.dsi.ifc.carkombi.SIAHistoryListRecord;
import org.dsi.ifc.carkombi.SIAOilInspection;
import org.dsi.ifc.carkombi.SIAServiceData;
import org.dsi.ifc.carkombi.SIAViewOptions;
import org.dsi.ifc.global.CarArrayListUpdateInfo;
import org.dsi.ifc.global.CarBCConsumption;
import org.dsi.ifc.global.CarBCCurrentRange;
import org.dsi.ifc.global.CarBCDistance;
import org.dsi.ifc.global.CarBCTemperature;
import org.dsi.ifc.global.CarBCTime;

public class OutsideTempData extends AbstractCarComponent implements DSICarKombiListener {

    private static final String TAG = "OutsideTempData";

    /** Eigene Komponenten-ID (900 VehicleData, 901 DaylightData, 903
     *  DriveModeData; 902 bewusst frei - KeyProbe-Entwurf im Git-Stash). */
    public static final int COMPONENT_ID = 904;

    /** org.dsi.ifc.carkombi.DSICarKombi.ATTR_BCOUTSIDETEMPERATURE. */
    private static final int ATTR_BC_OUTSIDE_TEMPERATURE = 65;

    /** CarBCTemperature.state == NORMAL_OPERATION (siehe Klassenkommentar). */
    private static final int STATE_NORMAL_OPERATION = 2;

    /* Push-basiert (aendert sich selten) - wie DaylightData/DriveModeData
     * kein Freshness-Timeout noetig. */
    public volatile boolean valid = false;
    public volatile float celsius = 0f;

    public OutsideTempData(ICarApplication app) {
        super(app, "App.Car.AmbientFx.OutsideTemp");
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
        return "AmbientFxOutsideTemp";
    }

    public String getCurrentViewOptions() {
        return "n/a";
    }

    public CarDSIAttributesSet[] getDSIAttributesSets() {
        return new CarDSIAttributesSet[] {
            new CarDSIAttributesSet(0, new int[] { ATTR_BC_OUTSIDE_TEMPERATURE }, new int[0])
        };
    }

    public String getDSIListenerClassName() {
        return "org.dsi.ifc.carkombi.DSICarKombiListener";
    }

    public String getDSIClassName() {
        return "org.dsi.ifc.carkombi.DSICarKombi";
    }

    public boolean isUsingDSI() {
        return true;
    }

    protected void dsiAvailable(boolean available) {
        super.dsiAvailable(available);
        Log.i(TAG, "DSICarKombi available=" + available);
        if (!available) {
            valid = false;
        }
    }

    /* ---------------- DSICarKombiListener ---------------- */

    public void updateBCOutsideTemperature(CarBCTemperature temp, int validFlag) {
        if (validFlag != 1 || temp == null) {
            return;
        }
        if (temp.state != STATE_NORMAL_OPERATION) {
            valid = false;
            Log.i(TAG, "updateBCOutsideTemperature: state=" + temp.state + " (nicht NORMAL_OPERATION) - ignoriert");
            return;
        }
        float c = temp.temperatureValue;
        if (temp.temperatureUnit == 1) {
            c = (c - 32f) / 1.8f;
        }
        celsius = c;
        valid = true;
        Log.i(TAG, "updateBCOutsideTemperature: " + c + " C (state=" + temp.state + ")");
    }

    /* Nicht benoetigt - Interface verlangt aber Implementierung (Java 1.2). */
    public void updateSIAViewOptions(SIAViewOptions a, int b) {}
    public void updateSIAServiceData(SIAServiceData a, int b) {}
    public void updateSIAOilInspection(SIAOilInspection a, int b) {}
    public void indicateEndOfSIAReset(boolean a) {}
    public void updateSIAHistoryListUpdateInfo(CarArrayListUpdateInfo a, int[] b, int c) {}
    public void responseSIAHistoryList(CarArrayListUpdateInfo a, SIAHistoryListRecord[] b) {}
    public void updateSIAHistoryListTotalNumberOfElements(int a, int b) {}
    public void updateSIADistanceOilUser(SIADistanceData a, int b) {}
    public void updateSIADistanceAirFilterUser(SIADistanceData a, int b) {}
    public void updateSIADistanceOilFilterUser(SIADistanceData a, int b) {}
    public void updateSIAInspectionDistanceUser(SIADistanceData a, int b) {}
    public void updateSIADailyAverageMileage(int a, int b, int c) {}
    public void updateBCViewOptions(BCViewOptions a, int b) {}
    public void updateBCIndications(BCIndications a, int b) {}
    public void updateBCCurrentConsumption1(CarBCConsumption a, int b) {}
    public void updateBCCurrentConsumption2(CarBCConsumption a, int b) {}
    public void updateBCCurrentRange1(CarBCCurrentRange a, int b) {}
    public void updateBCCurrentRange2(CarBCCurrentRange a, int b) {}
    public void updateBCTotalDistance(CarBCDistance a, int b) {}
    public void updateBCShortTermAverageConsumption1(CarBCConsumption a, int b) {}
    public void updateBCShortTermAverageConsumption2(CarBCConsumption a, int b) {}
    public void updateBCShortTermGeneral(BCShortTermGeneralData a, int b) {}
    public void updateBCLongTermAverageConsumption1(CarBCConsumption a, int b) {}
    public void updateBCLongTermAverageConsumption2(CarBCConsumption a, int b) {}
    public void updateBCLongTermGeneral(BCLongTermGeneralData a, int b) {}
    public void updateBCCycleAverageConsumption1(CarBCConsumption a, int b) {}
    public void updateBCCycleAverageConsumption2(CarBCConsumption a, int b) {}
    public void updateBCCycleGeneral(BCCycleGeneralData a, int b) {}
    public void updateBCVZADisplay(boolean a, int b) {}
    public void updateBCLifeTipsDisplay(boolean a, int b) {}
    public void updateBCConsumerDisplay(boolean a, int b) {}
    public void updateBCTankLevel1(BCTankLevel a, int b) {}
    public void updateBCTankLevel2(BCTankLevel a, int b) {}
    public void updateBCRefuelVolume1(BCRefuelVolume a, int b) {}
    public void updateBCRefuelVolume2(BCRefuelVolume a, int b) {}
    public void updateBCMenue1Config(BCMenueConfiguration a, int b) {}
    public void updateBCMenue2Config(BCMenueConfiguration a, int b) {}
    public void updateBCMenue3Config(BCMenueConfiguration a, int b) {}
    public void updateBCOilTemperature(boolean a, int b) {}
    public void updateBCDigitalSpeed(boolean a, int b) {}
    public void updateBCStopwatch(boolean a, int b) {}
    public void updateBCVzaMFA(boolean a, int b) {}
    public void updateBCSpeedWarning(BCSpeedWarningSettings a, int b) {}
    public void updateBCGearRecommendation(boolean a, int b) {}
    public void updateBCRearSeatbeltWarning(boolean a, int b) {}
    public void indicateEndOfBCMenuReset(boolean a) {}
    public void updateBCVehicleStateListTotalNumberOfElements(int a, int b) {}
    public void responseVehicleStateUpdateInfo(BCVehicleStateUpdateInfoAH a, int[] b) {}
    public void responseVehicleStateListWarningIDdynValueAlternativeText(BCVehicleStateUpdateInfoAH a, int b, int c, String d, String e) {}
    public void responseVehicleStateListWarningIDdynValue(BCVehicleStateUpdateInfoAH a, ListWarningIDsDynValues[] b) {}
    public void responseVehicleStateListAlternativeText(BCVehicleStateUpdateInfoAH a, int b, String c) {}
    public void responseVehicleStateListdynValue(BCVehicleStateUpdateInfoAH a, ListDynValues[] b) {}
    public void responseVehicleStateListPos(BCVehicleStateUpdateInfoAH a, int[] b) {}
    public void acknowledgeBcSetFactoryDefault(boolean a) {}
    public void acknowledgeHUDSetFactoryDefault(boolean a) {}
    public void acknowledgeDCSetFactoryDefault(boolean a) {}
    public void acknowledgeBcStatisticsReset(int a) {}
    public void updateBCStatisticsDistanceAC1(BCStatisticsAC a, int b) {}
    public void updateBCStatisticsDistanceAC2(BCStatisticsAC a, int b) {}
    public void updateBCStatisticsDistanceRE(BCStatisticsRE a, int b) {}
    public void updateBCStatisticsDistanceZE(BCStatisticsZE a, BCZeroEmissionAbsoluteDistance b, int c) {}
    public void updateBCStatisticsDistanceCurrentIntervalAC1(CarBCConsumption a, int b) {}
    public void updateBCStatisticsDistanceCurrentIntervalAC2(CarBCConsumption a, int b) {}
    public void updateBCStatisticsDistanceCurrentIntervalRE(BCAverageRecoveredEnergy a, int b) {}
    public void updateBCStatisticsDistanceCurrentIntervalZE(BCZeroEmissionRelative a, BCZeroEmissionAbsoluteDistance b, int c) {}
    public void updateBCStatisticsTimeAC1(BCStatisticsAC a, int b) {}
    public void updateBCStatisticsTimeAC2(BCStatisticsAC a, int b) {}
    public void updateBCStatisticsTimeRE(BCStatisticsRE a, int b) {}
    public void updateBCStatisticsTimeZE(BCStatisticsZE a, BCZeroEmissionAbsoluteTime b, int c) {}
    public void updateBCStatisticsTimeCurrentPeriodAC1(CarBCConsumption a, int b) {}
    public void updateBCStatisticsTimeCurrentPeriodAC2(CarBCConsumption a, int b) {}
    public void updateBCStatisticsTimeCurrentPeriodRE(BCAverageRecoveredEnergy a, int b) {}
    public void updateBCStatisticsTimeCurrentPeriodZE(BCZeroEmissionRelative a, BCZeroEmissionAbsoluteTime b, int c) {}
    public void updateBCStatisticsConfig(BCStatisticsConfig a, int b) {}
    public void updateBCStatisticDistanceEUkm(BCStatisticsDistanceEU a, int b) {}
    public void updateBCStatisticDistanceEUmls(BCStatisticsDistanceEU a, int b) {}
    public void updateBCOilTemperatureValue(CarBCTemperature a, int b) {}
    public void updateBCCoolantTemperature(CarBCTemperature a, int b) {}
    public void updateBCComfortPowerConsumption(BCComfortPowerConsumption a, int b) {}
    public void updateBCTotalCurrentRange(CarBCDistance a, int b) {}
    public void updateBCZeroEmissionDistanceST(CarBCDistance a, int b) {}
    public void updateBCZeroEmissionDistanceLT(CarBCDistance a, int b) {}
    public void updateBCZeroEmissionDistanceCY(CarBCDistance a, int b) {}
    public void updateBCZeroEmissionTimeST(CarBCTime a, int b) {}
    public void updateBCZeroEmissionTimeLT(CarBCTime a, int b) {}
    public void updateBCZeroEmissionTimeCY(CarBCTime a, int b) {}
    public void updateBCMaxValues(BCMaxValues a, int b) {}
    public void updateBCResetTimeStampST(BCResetTimeStamp a, int b) {}
    public void updateBCResetTimeStampLT(BCResetTimeStamp a, int b) {}
    public void updateBCResetTimeStampCY(BCResetTimeStamp a, int b) {}
    public void updateBCAstaMFA(boolean a, int b) {}
    public void updateHUDViewOptions(HUDViewOptions a, int b) {}
    public void updateHUDHeightAdjustment(byte a, int b) {}
    public void updateHUDBrightness(byte a, int b) {}
    public void updateHUDColour(int a, int b, int c) {}
    public void updateHUDContent(HUDContent a, int b) {}
    public void updateHUDInfo(boolean a, int b) {}
    public void updateHUDSystemOnOff(boolean a, int b) {}
    public void updateHUDRotationAdjustment(int a, int b) {}
    public void updateHUDPresets(int a, int b, int c) {}
    public void updateHUDCollectiveTopics(HUDCollectiveTopics a, int b) {}
    public void updateHUDAutoSkinSwitch(boolean a, int b) {}
    public void responseHUDSectionConfigList(CarArrayListUpdateInfo a, HUDSectionConfigListRecord[] b) {}
    public void updateHUDSectionConfigListUpdateInfo(CarArrayListUpdateInfo a, int[] b, int c) {}
    public void updateHUDSectionConfigListTotalNumberOfElements(int a, int b) {}
    public void updateDCViewOptions(DCViewOptions a, int b) {}
    public void updateDCBrightness(int a, int b) {}
    public void updateDCVolume(int a, int b) {}
    public void updateDCElementContentSelectionListTotalNumberOfElements(int a, int b) {}
    public void updateDCDisplay1Setup(DCMainItems a, DCDisplayedAdditionalInfos b, DCAdditionalInfo c, DCAdditionalInfo d, int e) {}
    public void updateDCDisplay2Setup(DCMainItems a, DCDisplayedAdditionalInfos b, DCAdditionalInfo c, DCAdditionalInfo d, int e) {}
    public void updateDCDisplay3Setup(DCMainItems a, DCDisplayedAdditionalInfos b, DCAdditionalInfo c, DCAdditionalInfo d, int e) {}
    public void updateDCDisplay1MainSelection(DCMainItems a, int b) {}
    public void updateDCDisplay2MainSelection(DCMainItems a, int b) {}
    public void updateDCDisplay3MainSelection(DCMainItems a, int b) {}
    public void responseDCElementContentSelectionListRAF(DCElementContentSelectionListUpdateInfo a, int[] b) {}
    public void responseDCElementContentSelectionListRA1(DCElementContentSelectionListUpdateInfo a, DCElementContentSelectionListRA1[] b) {}
    public void responseDCElementContentSelectionListRA2(DCElementContentSelectionListUpdateInfo a, DCElementContentSelectionListRA2[] b) {}
    public void updateDCElementContentSelectionListUpdateInfo(DCElementContentSelectionListUpdateInfo a, int[] b, int c) {}
    public void updateDCAdditionalInstrumentSetup(DCAdditionalInstrument a, int b) {}
    public void updateDCAdditionalInstrument2Setup(DCAdditionalInstrument2 a, int b) {}
    public void updateDCDisplayPresetsListTotalNumberOfElements(int a, int b) {}
    public void responseDCDisplayPresetsList(CarArrayListUpdateInfo a, DCDisplayPresetsListRecord[] b) {}
    public void updateDCDisplayPresetsListUpdateInfo(CarArrayListUpdateInfo a, DCDisplayPresetsListRecord[] b, int c) {}
    public void updateDCDisplayDependencySetup(DCDisplayDependency a, int b) {}
    public void updateDCActiveDisplayPreset(int a, int b) {}
    public void updateDCDisplayViewConfiguration(DCDisplayViewConfiguration a, int b) {}
    public void updateCompassInfo(int a, int b, int c) {}
    public void updateHUDLicense(boolean a, int b) {}
    public void updateDCLEDConfiguration(boolean a, int b) {}
}
