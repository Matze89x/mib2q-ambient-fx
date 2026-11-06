# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md / License: CC BY-NC-SA 4.0, see LICENSE.md
#
# DE: Beschreibung aller einstellbaren Werte der Effekt-Vorlagen (Grenzen, Einheit, Beschriftung DE/EN). EINE Quelle fuer die erzeugten Java-Klassen
#     (Begrenzung beim Laden) und das Design Studio (Regler). Die Standardwerte kommen NICHT von hier, sondern aus den Original-Effekten.
# EN: Description of all adjustable values of the effect templates (limits, unit, label DE/EN). ONE source for the generated Java classes
#     (clamping on load) and the Design Studio (sliders). Default values do NOT come from here but from the original effects.
#
# Eintrag / entry:  NAME: (gruppe/group, min, max, step, einheit/unit, "DE", "EN")
#                   bool-Werte haben min=max=None / boolean values have min=max=None

META = {
    # ---- Farbweg / colour path ----
    "RAINBOW_SPAN_KMH": ("color", 20, 300, 1, "km/h", "Farbkreis komplett bei", "Colour ring complete at"),
    "SPEED_STEP_KMH": ("color", 1, 50, 1, "km/h", "Stufenbreite (nur ohne weiche Übergänge)", "Step width (only without smooth blending)"),
    "SMOOTH_COLORS": ("color", None, None, None, "", "Weiche Übergänge zwischen Werksfarben", "Smooth blending between factory colours"),
    "RING_MIN_SAT": ("color", 0, 1, 0.01, "", "Ring: Mindest-Sättigung einer Werksfarbe", "Ring: min. saturation of a factory colour"),
    "RING_MIN_VAL": ("color", 0, 1, 0.01, "", "Ring: Mindest-Helligkeit einer Werksfarbe", "Ring: min. brightness of a factory colour"),
    "TRY_BLENDED_COLORS": ("color", None, None, None, "", "Bremse/Notfall: zwei Werksfarben mischen", "Brake/fallback: blend two factory colours"),
    "TRY_FREE_COLOR": ("color", None, None, None, "", "Freies Farbstreaming (ohne Werksfarben-Snap)", "Free colour streaming (no factory-colour snap)"),
    "SNAP_FACTORY": ("color", None, None, None, "", "Auf Werksfarben einrasten", "Snap to factory colours"),
    "TARGET_LUMINANCE": ("color", 0, 1, 0.01, "", "Mindest-Helligkeit je Farbton (Ausgleich)", "Min. perceived brightness per hue (compensation)"),
    "MIN_SATURATION_FLOOR": ("color", 0, 1, 0.01, "", "Ausgleich: nie weniger Sättigung als", "Compensation: never less saturation than"),
    # ---- Tempo / speed ----
    "HUE_SPEED_FACTOR": ("speed", 0, 3, 0.01, "°/(km/h·s)", "Drehung pro km/h und Sekunde", "Rotation per km/h and second"),
    "IDLE_ROTATION_DEG_PER_SEC": ("speed", -360, 360, 1, "°/s", "Drehung im Stand", "Rotation at standstill"),
    "TC_SPEED_MS": ("time", 1, 5000, 10, "ms", "Tempo-Glättung", "Speed smoothing"),
    # ---- Helligkeit / brightness ----
    "TC_BRIGHT_UP": ("time", 10, 5000, 10, "ms", "Heller werden: Zeitkonstante", "Brightening time constant"),
    "TC_BRIGHT_DOWN": ("time", 10, 5000, 10, "ms", "Dunkler werden: Zeitkonstante", "Dimming time constant"),
    "NIGHT_ACCEL_SATURATE_PCT": ("night", 1, 100, 1, "%", "Nacht: volle Helligkeit ab Gas", "Night: full brightness from throttle"),
    "NIGHT_BRIGHT_FLOOR": ("night", 0, 1, 0.01, "", "Nacht: Mindesthelligkeit", "Night: minimum brightness"),
    "NIGHT_BRIGHTNESS": ("night", 0, 1, 0.01, "", "Nacht-Helligkeit", "Night brightness"),
    "NIGHT_FACTOR": ("night", 0, 1, 0.01, "", "Nacht: Faktor auf die Helligkeit", "Night: brightness factor"),
    # ---- D->S-Blitz / flash ----
    "FLASH_HOLD_MS": ("flash", 0, 10000, 50, "ms", "D→S-Blitz: Weiß halten", "D→S flash: hold white"),
    "FLASH_FADE_MS": ("flash", 1, 10000, 50, "ms", "D→S-Blitz: Überblenden", "D→S flash: fade back"),
    # ---- Momentum select: Fahrprofile / drive profiles ----
    "TC_HUE_DEFAULT": ("prof_auto", 1, 5000, 10, "ms", "Auto/Individual: Farbglättung", "Auto/Individual: colour smoothing"),
    "TC_BRIGHT_DEFAULT_UP": ("prof_auto", 10, 5000, 10, "ms", "Auto/Individual: heller werden", "Auto/Individual: brightening"),
    "TC_BRIGHT_DEFAULT_DOWN": ("prof_auto", 10, 5000, 10, "ms", "Auto/Individual: dunkler werden", "Auto/Individual: dimming"),
    "HUE_START_DEFAULT": ("prof_auto", -720, 720, 1, "°", "Auto/Individual: Start-Farbton", "Auto/Individual: start hue"),
    "HUE_SPAN_DEFAULT": ("prof_auto", -720, 720, 1, "°", "Auto/Individual: Farbton-Strecke", "Auto/Individual: hue span"),
    "TC_HUE_DYNAMIC": ("prof_dyn", 1, 5000, 10, "ms", "Dynamic: Farbglättung", "Dynamic: colour smoothing"),
    "TC_BRIGHT_DYNAMIC": ("prof_dyn", 10, 5000, 10, "ms", "Dynamic: Helligkeitsglättung", "Dynamic: brightness smoothing"),
    "HUE_START_DYNAMIC": ("prof_dyn", -720, 720, 1, "°", "Dynamic: Start-Farbton", "Dynamic: start hue"),
    "HUE_SPAN_DYNAMIC": ("prof_dyn", -720, 720, 1, "°", "Dynamic: Farbton-Strecke", "Dynamic: hue span"),
    "DYNAMIC_THROTTLE_PULL": ("prof_dyn", 0, 1, 0.05, "", "Dynamic: Gas zieht Richtung Rot", "Dynamic: throttle pulls towards red"),
    "TC_HUE_COMFORT_ECO": ("prof_com", 1, 5000, 10, "ms", "Comfort/Eco: Farbglättung", "Comfort/Eco: colour smoothing"),
    "TC_BRIGHT_COMFORT_ECO_UP": ("prof_com", 10, 5000, 10, "ms", "Comfort/Eco: heller werden", "Comfort/Eco: brightening"),
    "TC_BRIGHT_COMFORT_ECO_DOWN": ("prof_com", 10, 5000, 10, "ms", "Comfort/Eco: dunkler werden", "Comfort/Eco: dimming"),
    "HUE_START_COMFORT_ECO": ("prof_com", -720, 720, 1, "°", "Comfort/Eco: Start-Farbton", "Comfort/Eco: start hue"),
    "HUE_SPAN_COMFORT_ECO": ("prof_com", -720, 720, 1, "°", "Comfort/Eco: Farbton-Strecke", "Comfort/Eco: hue span"),
    "COMFORT_BRIGHT_FLOOR": ("prof_com", 0, 1, 0.01, "", "Comfort/Eco: Helligkeit ohne Gas", "Comfort/Eco: brightness at no throttle"),
    "COMFORT_BRIGHT_SPAN": ("prof_com", 0, 1, 0.01, "", "Comfort/Eco: Zuwachs bis Vollgas", "Comfort/Eco: increase up to full throttle"),
    # ---- Atmosphere ----
    "TEMP_COLD_C": ("temp", -40, 60, 1, "°C", "Kalt bei", "Cold at"),
    "TEMP_WARM_C": ("temp", -40, 60, 1, "°C", "Warm bei", "Warm at"),
    "HUE_COLD": ("temp", -720, 720, 1, "°", "Farbton kalt", "Hue cold"),
    "HUE_WARM": ("temp", -720, 720, 1, "°", "Farbton warm", "Hue warm"),
    "TEMP_FALLBACK_C": ("temp", -40, 60, 1, "°C", "Ersatz-Temperatur (ohne Messwert)", "Fallback temperature (no reading)"),
    "TC_TEMP_MS": ("temp", 1, 60000, 100, "ms", "Temperatur-Glättung", "Temperature smoothing"),
    "BREATHE_PERIOD_MS": ("breath", 2000, 60000, 100, "ms", "Atmen: Periode (mind. 2 s)", "Breathing: period (min. 2 s)"),
    "DAY_BREATHE_MIN": ("breath", 0, 1, 0.01, "", "Tag: Helligkeit min.", "Day: brightness min."),
    "DAY_BREATHE_MAX": ("breath", 0, 1, 0.01, "", "Tag: Helligkeit max.", "Day: brightness max."),
    "NIGHT_BREATHE_MIN": ("breath", 0, 1, 0.01, "", "Nacht: Helligkeit min.", "Night: brightness min."),
    "NIGHT_BREATHE_MAX": ("breath", 0, 1, 0.01, "", "Nacht: Helligkeit max.", "Night: brightness max."),
    "SHIMMER_PERIOD_MS": ("breath", 2000, 120000, 500, "ms", "Schimmern: Periode (mind. 2 s)", "Shimmer: period (min. 2 s)"),
    "SHIMMER_BRIGHTNESS_MIN": ("breath", 0, 1, 0.01, "", "Schimmern: Helligkeit min.", "Shimmer: brightness min."),
    # ---- Drive ----
    "TC_THROTTLE_UP_MS": ("time", 1, 5000, 10, "ms", "Gas: schnell hoch", "Throttle: rise time"),
    "TC_THROTTLE_DOWN_MS": ("time", 1, 5000, 10, "ms", "Gas: langsam runter", "Throttle: fall time"),
    "TC_STEER_MS": ("time", 1, 5000, 10, "ms", "Lenkwinkel-Glättung", "Steering smoothing"),
    "DRIVE_BASE_HUE": ("drive", -720, 720, 1, "°", "Farbton in Ruhe", "Hue at rest"),
    "DRIVE_THROTTLE_SWEEP": ("drive", -720, 720, 1, "°", "Farbton-Strecke bis Vollgas", "Hue sweep up to full throttle"),
    "DRIVE_SAT_REST": ("drive", 0, 1, 0.01, "", "Sättigung in Ruhe", "Saturation at rest"),
    "DRIVE_SPLIT_DEG": ("drive", -360, 360, 1, "°", "Zusatz-Farbton bei vollem Lenkeinschlag", "Extra hue at full steering lock"),
    "DRIVE_CONTOUR_BOOST": ("drive", 0, 1, 0.01, "", "Zusatz-Helligkeit bei Lenkeinschlag", "Extra brightness with steering"),
    "DRIVE_HUE_CAP": ("drive", 0, 720, 1, "°", "Sicherheits-Deckel Farbton (nicht anheben)", "Safety cap for hue (do not raise)"),
    "DRIVE_V_REST": ("drive", 0, 1, 0.01, "", "Helligkeit in Ruhe", "Brightness at rest"),
    "DRIVE_V_SWEEP": ("drive", 0, 1, 0.01, "", "Helligkeits-Zuwachs bis Vollgas", "Brightness increase up to full throttle"),
    "DRIVE_V_FLOOR": ("drive", 0, 1, 0.01, "", "Mindest-Helligkeit", "Minimum brightness"),
    "PEDAL_FULL": ("drive", 0.05, 1, 0.01, "", "Gaspedal gilt als voll bei (Anteil)", "Pedal counts as full at (fraction)"),
    "STEER_FULL_DEG": ("drive", 10, 720, 5, "°", "Voller Lenkeinschlag bei", "Full steering lock at"),
    # ---- Sende-Takt / send interval ----
    "PERIOD_MS": ("send", 50, 500, 10, "ms", "Sende-Takt", "Send interval"),
}

# Gruppen-Reihenfolge und Titel / group order and titles
GROUPS = [
    ("color", "Farbe", "Colour"),
    ("speed", "Tempo", "Speed"),
    ("drive", "Gaspedal und Lenkung", "Throttle and steering"),
    ("temp", "Außentemperatur", "Outside temperature"),
    ("breath", "Atmen und Schimmern", "Breathing and shimmer"),
    ("night", "Nacht", "Night"),
    ("time", "Glättung (Zeitkonstanten)", "Smoothing (time constants)"),
    ("flash", "D→S-Blitz (Gangwahlhebel)", "D→S flash (gear lever)"),
    ("prof_auto", "Fahrprofil Auto / Individual", "Drive profile Auto / Individual"),
    ("prof_dyn", "Fahrprofil Dynamic", "Drive profile Dynamic"),
    ("prof_com", "Fahrprofil Comfort / Eco", "Drive profile Comfort / Eco"),
    ("send", "Takt", "Timing"),
]

# Zusaetzliche Werte, die im Original-Code als Zahl mitten im Ablauf stehen und hier herausgeloest werden: (Datei, alter Text, neuer Text, [(Name, Typ, Standard)])
LITERALS = [
    ("Momentum", "float alphaV = dt / (600f + dt);", "float alphaV = dt / (TC_SPEED_MS + dt);", [("TC_SPEED_MS", "float", "600f")]),
    ("MomentumSelect", "targetBrightness = 0.03f + 0.97f * (accelPct / 100f);",
     "targetBrightness = COMFORT_BRIGHT_FLOOR + COMFORT_BRIGHT_SPAN * (accelPct / 100f);",
     [("COMFORT_BRIGHT_FLOOR", "float", "0.03f"), ("COMFORT_BRIGHT_SPAN", "float", "0.97f")]),
    ("Drive", "Math.abs(wheelAngle) / 180f", "Math.abs(wheelAngle) / STEER_FULL_DEG", [("STEER_FULL_DEG", "float", "180f")]),
    ("Drive", "float brightness = daylight ? cv : cv * 0.5f;", "float brightness = daylight ? cv : cv * NIGHT_FACTOR;", [("NIGHT_FACTOR", "float", "0.5f")]),
    ("Atmosphere", "        if (factoryPalette != null && factoryPalette.length > 0) {\n            out.set(nearest(idealHue, factoryPalette));",
     "        if (SNAP_FACTORY && factoryPalette != null && factoryPalette.length > 0) {\n            out.set(nearest(idealHue, factoryPalette));",
     [("SNAP_FACTORY", "boolean", "true")]),
    ("Drive", "        if (factoryPalette != null && factoryPalette.length > 0) {\n            out.set(nearest(idealHue, factoryPalette));",
     "        if (SNAP_FACTORY && factoryPalette != null && factoryPalette.length > 0) {\n            out.set(nearest(idealHue, factoryPalette));",
     [("SNAP_FACTORY", "boolean", "true")]),
]

# Anbindung an Fahrzeugdaten (ueber TplEnv, gleiche Logik wie im Original): (Datei, alter Text, neuer Text)
HOOKS = [
    ("Momentum", "        DriveModeData dm = AmbientFx.getInstance().getDriveMode();\n        int profile = (dm != null && dm.valid) ? dm.profile : DriveModeData.PROFILE_UNKNOWN;",
     "        int profile = TplEnv.profile();"),
    ("MomentumSelect", "        DriveModeData dm = AmbientFx.getInstance().getDriveMode();\n        int profile = (dm != null && dm.valid) ? dm.profile : DriveModeData.PROFILE_UNKNOWN;",
     "        int profile = TplEnv.profile();"),
    ("Atmosphere", "        OutsideTempData t = AmbientFx.getInstance().getOutsideTemp();\n        float targetTempC = (t != null && t.valid) ? t.celsius : TEMP_FALLBACK_C;",
     "        float targetTempC = TplEnv.tempC(TEMP_FALLBACK_C);"),
]

# Nach dem Laden der Werte (Sicherheits-Korrekturen, gleiche in der JS-Engine): (Datei, Java-Code)
POST = {
    "Atmosphere": "if (TEMP_WARM_C - TEMP_COLD_C < 1f) {\n            TEMP_WARM_C = TEMP_COLD_C + 1f;\n        }",
}

EFFECTS = [
    ("Momentum", "momentum", "Momentum"),
    ("MomentumSelect", "momentumselect", "Momentum select"),
    ("Atmosphere", "atmosphere", "Atmosphere"),
    ("Scroll", "scroll", "Scroll"),
    ("Drive", "drive", "Drive"),
]
