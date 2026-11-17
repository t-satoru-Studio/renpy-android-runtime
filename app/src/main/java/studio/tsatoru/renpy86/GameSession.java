// Copyright (C) 2026 t-satoru
// SPDX-License-Identifier: GPL-3.0-or-later
package studio.tsatoru.renpy86;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Which game the {@code :game} process is running, shared between processes through a
 * small file. Python can be initialised only once per process, so a different game
 * needs a fresh process; the same game is just brought back to the front.
 */
final class GameSession {

    final int pid;
    final String folder;

    GameSession(int pid, String folder) {
        this.pid = pid;
        this.folder = folder;
    }

    static File file(File filesDir) {
        return new File(filesDir, "session.properties");
    }

    static GameSession read(File filesDir) {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(file(filesDir))) {
            p.load(in);
            return new GameSession(Integer.parseInt(p.getProperty("pid")), p.getProperty("folder"));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    void write(File filesDir) {
        Properties p = new Properties();
        p.setProperty("pid", Integer.toString(pid));
        p.setProperty("folder", folder);
        File target = file(filesDir);
        File tmp = new File(filesDir, target.getName() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            p.store(out, null);
        } catch (IOException e) {
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(target);
    }

    /** Removes the record, but only if it is still ours. */
    static void clear(File filesDir, int pid) {
        GameSession current = read(filesDir);
        if (current != null && current.pid == pid) {
            //noinspection ResultOfMethodCallIgnored
            file(filesDir).delete();
        }
    }
}
