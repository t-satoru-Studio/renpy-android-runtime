# Copyright (C) 2026 t-satoru
# SPDX-License-Identifier: GPL-3.0-or-later
"""Tests for runtime/main_prelude.py. Run: python3 -m unittest discover tests"""

import os
import pathlib
import sys
import unittest

PRELUDE = pathlib.Path(__file__).resolve().parent.parent / "runtime" / "main_prelude.py"


def run_prelude(environ, argv):
    fake_sys = type("FakeSys", (), {"argv": list(argv)})()
    fake_os = type("FakeOs", (), {"environ": environ})()
    scope = {"os": fake_os, "sys": fake_sys}
    exec(compile(PRELUDE.read_text(), str(PRELUDE), "exec"), scope)
    return fake_sys.argv, scope


class MainPreludeTest(unittest.TestCase):

    def test_without_basedir_argv_is_untouched(self):
        argv, _ = run_prelude({}, ["main.py"])
        self.assertEqual(argv, ["main.py"])

    def test_basedir_and_savedir(self):
        argv, _ = run_prelude({"RAR_BASEDIR": "/sdcard/koi", "RAR_SAVEDIR": "/sdcard/koi/saves"},
                              ["/data/files/main.py"])
        self.assertEqual(argv, ["/data/files/main.py", "/sdcard/koi", "--savedir", "/sdcard/koi/saves"])

    def test_compile_flag(self):
        argv, _ = run_prelude({"RAR_BASEDIR": "/g", "RAR_COMPILE": "1"}, ["main.py"])
        self.assertEqual(argv, ["main.py", "/g", "--compile"])

    def test_leaves_no_helper_variables_behind(self):
        _, scope = run_prelude({"RAR_BASEDIR": "/g"}, ["main.py"])
        self.assertNotIn("_rar_args", scope)


if __name__ == "__main__":
    unittest.main()
