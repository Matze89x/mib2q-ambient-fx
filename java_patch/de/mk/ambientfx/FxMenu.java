/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
 *
 * AmbientFX - baut die zusaetzlichen Menue-Eintraege fuer den Screen
 * CARCARSETTINGSINTLIGHTMAIN (600028).
 *
 * Wird aus dem gepatchten CarScreenBag5.cARCARSETTINGSINTLIGHTMAIN aufgerufen.
 * Die Items sind exakt wie die Stock-Profil-Eintraege konfiguriert (Radio-
 * Button-Typ 256, Range2D-Modell 601073, Event 600040), nur mit widgetID
 * >= FxCatalog.FIRST_WIDGET_ID und eigenem Text.
 */
package de.mk.ambientfx;

import de.audi.atip.hmi.view.HMIView;
import de.esolutions.hmi.widgets.audi.evo.high.widgets.CompositeRendererHigh;
import de.esolutions.hmi.widgets.audi.evo.widgets.menu.MenuController;
import de.esolutions.hmi.widgets.audi.evo.widgets.menu.MenuItemController;

public final class FxMenu {

    /** Range2D-Modell der Profil-Liste (X = Profil / Effekt, Y = Helligkeit). */
    public static final int MODEL_PROFILES_2D = 601073;
    /** Event beim Druecken eines Eintrags (wie Stock). */
    public static final int EVENT_SELECT = 600040;
    /** Platzhalter-Text-ID (wird durch FxMenuItemController ueberschrieben). */
    private static final int PLACEHOLDER_LABEL_ID = 601979;

    private FxMenu() {}

    /**
     * Erzeugt alle Effekt-Eintraege. Bitmaps/Infoline-IDs sind 1:1 vom
     * Stock-Eintrag "Maritim-Blau" (widgetID 4) uebernommen.
     */
    public static MenuItemController[] createMenuItems() {
        try {
            return createMenuItemsUnsafe();
        } catch (Throwable t) {
            /* Im Fehlerfall: Stock-Menue ohne Effekte statt kaputtem Screen */
            Log.e("FxMenu", "createMenuItems failed - fx entries disabled", t);
            return new MenuItemController[0];
        }
    }

    private static MenuItemController[] createMenuItemsUnsafe() {
        int n = FxCatalog.count();
        MenuItemController[] items = new MenuItemController[n];
        for (int i = 0; i < n; i++) {
            FxMenuItemController mi = new FxMenuItemController(FxCatalog.label(i));
            CompositeRendererHigh r = new CompositeRendererHigh(mi);
            mi.setRenderer(r);
            mi.setBitmaps(new int[] { 600556, 600044, 600556, 600556, 600556, 600556, 600556, 600556 });
            mi.setModelID(MODEL_PROFILES_2D);
            mi.setEvent(EVENT_SELECT);
            mi.setLabelId(PLACEHOLDER_LABEL_ID);
            mi.setInfolineTextIdsDisabled(new int[] { 605316, 605316, 603625, 605503, 603647, 606079, 603628, 605165 });
            mi.setGlassplateInsetsBottom(6);
            mi.setGlassplateInsetsTop(7);
            mi.setInternalID(FxCatalog.FIRST_INTERNAL_ID + i);
            mi.setType(256);
            mi.setWidgetID(FxCatalog.FIRST_WIDGET_ID + i);
            items[i] = mi;
        }
        Log.i("FxMenu", "created " + n + " fx menu items");
        return items;
    }

    /** Haengt die Items an das Menue an (nach den Stock-Eintraegen). */
    public static void addToMenu(MenuController menu, MenuItemController[] items) {
        for (int i = 0; i < items.length; i++) {
            menu.add(items[i]);
        }
    }

    /**
     * Erweitert die View-Liste des Range2D-Modells um die neuen Items, damit
     * sie beim Verbinden des Screens das Modell (Auswahl-Status) bekommen.
     */
    public static HMIView[] appendViews(HMIView[] stock, MenuItemController[] items) {
        HMIView[] out = new HMIView[stock.length + items.length];
        System.arraycopy(stock, 0, out, 0, stock.length);
        for (int i = 0; i < items.length; i++) {
            out[stock.length + i] = items[i];
        }
        return out;
    }
}
