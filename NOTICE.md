# Third-party software

The APK built from this repository contains, unmodified:

| Component | From | License |
|---|---|---|
| Ren'Py 8.6.0.26111403 (Python code, `renpy/common`) | `renpy-8.6.0-sdk.tar.bz2` | MIT, with parts under LGPL-2.1+ and other licenses listed in Ren'Py's LICENSE.txt |
| `librenpython.so` (CPython 3.12, SDL3, SDL3_image, FFmpeg, FreeType, HarfBuzz, fribidi, libpng, libjpeg-turbo, libwebp, libavif, zlib and others, statically linked) | `renpy-8.6.0-rapt.zip` | each library's own license (PSF, zlib, LGPL-2.1+, FTL, MIT, BSD) |
| Python 3.12 standard library and bundled packages (e.g. truststore) | `renpy-8.6.0-sdk.tar.bz2` | PSF and the packages' licenses |
| RAPT Android Java (`org.renpy.android`), SDL3 Android Java (`org.libsdl.app`), pyjnius, jtar | `renpy-8.6.0-rapt.zip` | MIT, zlib, MIT, Apache-2.0 |

Exact archive checksums are in [upstream.lock.json](upstream.lock.json).

It also contains, in source form under [third_party/joiplay](third_party/joiplay), with the small changes listed in that directory's README:

| Component | From | License |
|---|---|---|
| joipad (virtual gamepad), Copyright (c) 2021 JoiPlay | <https://github.com/joiplay/joipad> | MIT |
| joiplay commons, Copyright (c) 2021 JoiPlay | <https://github.com/joiplay/commons> | MIT |
| sdp, Copyright (c) 2013 - 2015 Intuit Inc. | <https://github.com/joiplay/sdp> (fork of <https://github.com/intuit/sdp>) | MIT |

## Corresponding source

`librenpython.so` links LGPL libraries statically. Their source, and the build system that produced the binary, are published by Ren'Py:

- Ren'Py: <https://github.com/renpy/renpy/tree/8.6.0.26111403>
- renpy-build (toolchain, library versions, patches): <https://github.com/renpy/renpy-build/tree/renpy-8.6.0.26111403>

To replace an LGPL library, rebuild `librenpython.so` with renpy-build at that tag and run `tools/prepare.py` against the result in place of the RAPT archive.
