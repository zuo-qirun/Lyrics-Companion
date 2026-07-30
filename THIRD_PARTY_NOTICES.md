# Third-party notices

## sherpa-onnx

The optional offline Chinese-English real-time caption engine includes the `sherpa-onnx` C API header and its Android ONNX Runtime shared libraries. The model is downloaded only after user action and is stored in the application's private files directory.

Copyright (c) 2023 Xiaomi Corporation. sherpa-onnx and the selected `csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20` model are licensed under Apache License 2.0. License: https://www.apache.org/licenses/LICENSE-2.0

## Amap-for-ESP32 Android forwarder

`LrcTimeline`, `NetEaseLyricClient`, media-session selection, playback-position interpolation and player recognition are adapted from the sibling `Amap-for-ESP32/android_forwarder` source tree.

Copyright (c) 2026 zuo-qirun. The copyright holder has authorized this adapted code to be distributed as part of Lyrics Companion under GPL-3.0.

## AMap Companion

The secondary-display architecture and position-joystick interaction are adapted from the author's `zuo-qirun/amap-companion`: enumerate displays, create a display-specific context, attach an overlay through that context's `WindowManager`, and continuously adjust its saved coordinates with a spring-back controller.

Copyright (c) zuo-qirun. The source project is published under GPL-3.0; the shared author has authorized this adaptation for Lyrics Companion.

## Refined Now Playing for Netease

The immersive cover-and-lyrics style is a native Android interpretation of `solstice23/refined-now-playing-netease`. The curved lyric transform geometry is adapted from `src/lyrics.js`; no plugin JavaScript or runtime code is bundled.

Copyright (c) 2022 solstice23. The reference project is licensed under the MIT License.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## PiPWindow and Chromatic

The warm compact style visually references `Lukoning/PiPWindow`, a plugin for `std-microblock/chromatic`. Lyrics Companion uses its own Android Canvas renderer and does not include either plugin's source or plugin runtime.

PiPWindow is licensed under GPL-3.0. Project names and screenshots remain the property of their respective authors.

## NetEase Cloud Music service

Track search and lyric responses are obtained from public NetEase Cloud Music web endpoints. NetEase names, services, content and trademarks remain the property of their respective owners. Availability is not guaranteed.

## Soda Music service

Track search, word-timed lyrics and translations are obtained from Soda Music web endpoints without accessing the user's Soda Music account or cookies. The request and response handling in Lyrics Companion is an independent implementation based on observed network data; no Soda Music application code is bundled. Soda Music names, services, content and trademarks remain the property of their respective owners. Availability is not guaranteed.

## LyricProvider QRC/KRC codecs

The QQ Music QRC and KuGou KRC decoding/parsing implementations are adapted from `tomakino/LyricProvider` (`qrckit` and `krckit`).

Copyright (c) 2026 Proify, Tomakino. Licensed under the Apache License, Version 2.0.
