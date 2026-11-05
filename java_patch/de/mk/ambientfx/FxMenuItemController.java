/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
 *
 * AmbientFX - Menue-Eintrag mit frei waehlbarem Text.
 *
 * Stock-MenuItems holen ihren Text ueber eine Text-ID aus der Sprachtabelle
 * (lang_data). Fuer unsere Effekte gibt es dort keine Eintraege. Deshalb:
 * Nach dem normalen Aufbau des Items (MenuItemBuilder erzeugt Label +
 * Radio-Button) suchen wir das Label-Kind und setzen den Text direkt als
 * String-Modell (LabelController.setText -> Model = String -> wird 1:1
 * angezeigt, unabhaengig von der Sprache).
 */
package de.mk.ambientfx;

import de.esolutions.hmi.widgets.audi.evo.widgets.LabelController;
import de.esolutions.hmi.widgets.audi.evo.widgets.menu.MenuItemController;
import java.util.List;

public class FxMenuItemController extends MenuItemController {

    private final String text;

    public FxMenuItemController(String text) {
        super();
        this.text = text;
    }

    protected void initializeWidget() {
        super.initializeWidget();
        applyText();
    }

    private void applyText() {
        try {
            List children = getChildren();
            for (int i = 0; i < children.size(); i++) {
                Object c = children.get(i);
                if (c instanceof LabelController) {
                    ((LabelController) c).setText(text);
                    return;
                }
            }
            Log.w("FxMenuItem", "no LabelController child for '" + text + "'");
        } catch (Throwable t) {
            Log.e("FxMenuItem", "applyText failed", t);
        }
    }

    public String getFxText() {
        return text;
    }
}
