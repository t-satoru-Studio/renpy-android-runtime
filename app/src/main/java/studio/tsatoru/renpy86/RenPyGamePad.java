// Copyright (C) 2026 t-satoru
// SPDX-License-Identifier: GPL-3.0-or-later
package studio.tsatoru.renpy86;

import android.view.KeyEvent;

import cyou.joiplay.commons.model.GamePad;
import cyou.joiplay.commons.model.Game;

/**
 * JoiPlay's virtual gamepad (third_party/joiplay/joipad) set up for Ren'Py. JoiPad's own
 * defaults are RPG Maker keys; these are keys that Ren'Py's default keymap
 * (renpy/common/00keymap.rpy) acts on. Each button has a main and an alternative key,
 * swapped by the gamepad's switch button. Players can remap them in the gamepad's
 * settings, which JoiPad saves as {@code <game folder>/gamepad.json}, the file JoiPlay
 * uses, so a layout made in JoiPlay carries over.
 */
final class RenPyGamePad {

    private RenPyGamePad() {
    }

    static GamePad defaults() {
        GamePad p = new GamePad();
        // Action buttons: A in the middle, X above, Y left, B right, Z and C below.
        p.aKeyCode = keys(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE);     // dismiss, select
        p.bKeyCode = keys(KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_ESCAPE);   // game menu, back
        p.xKeyCode = keys(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_LEFT); // skip while held
        p.yKeyCode = keys(KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_H);             // hide windows
        p.zKeyCode = keys(KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_UP); // rollback
        p.cKeyCode = keys(KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_TAB);         // toggle skip
        // Shoulder buttons and the small buttons beside them.
        p.lKeyCode = keys(KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_UP);     // rollback
        p.rKeyCode = keys(KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_PAGE_DOWN); // roll forward
        p.clKeyCode = keys(KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F1);              // help
        p.crKeyCode = keys(KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_S);                // screenshot
        return p;
    }

    static Game game(LaunchRequest request) {
        Game g = new Game();
        g.type = "renpy";
        g.id = request.id;
        g.title = request.title;
        g.folder = request.folder;
        g.execFile = "";
        return g;
    }

    private static Integer[] keys(int main, int alternative) {
        return new Integer[]{main, alternative};
    }
}
