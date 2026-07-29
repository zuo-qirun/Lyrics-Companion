# Third-party notices

## Amap-for-ESP32 Android forwarder

`LrcTimeline`, `NetEaseLyricClient`, media-session selection, playback-position interpolation and player recognition are adapted from the sibling `Amap-for-ESP32/android_forwarder` source tree.

Copyright (c) 2026 zuo-qirun. The copyright holder has authorized this adapted code to be distributed as part of Lyrics Companion under GPL-3.0.

## AMap Companion

The secondary-display architecture and position-joystick interaction are adapted from the author's `zuo-qirun/amap-companion`: enumerate displays, create a display-specific context, attach an overlay through that context's `WindowManager`, and continuously adjust its saved coordinates with a spring-back controller.

Copyright (c) zuo-qirun. The source project is published under GPL-3.0; the shared author has authorized this adaptation for Lyrics Companion.

## Refined Now Playing for Netease

The immersive cover-and-lyrics style is a native Android visual interpretation of `solstice23/refined-now-playing-netease`. No plugin JavaScript or runtime code is bundled.

Copyright (c) 2022 solstice23. The reference project is licensed under the MIT License.

## PiPWindow and Chromatic

The warm compact style visually references `Lukoning/PiPWindow`, a plugin for `std-microblock/chromatic`. Lyrics Companion uses its own Android Canvas renderer and does not include either plugin's source or plugin runtime.

PiPWindow is licensed under GPL-3.0. Project names and screenshots remain the property of their respective authors.

## NetEase Cloud Music service

Track search and lyric responses are obtained from public NetEase Cloud Music web endpoints. NetEase names, services, content and trademarks remain the property of their respective owners. Availability is not guaranteed.

## LyricProvider QRC/KRC codecs

The QQ Music QRC and KuGou KRC decoding/parsing implementations are adapted from `tomakino/LyricProvider` (`qrckit` and `krckit`).

Copyright (c) 2026 Proify, Tomakino. Licensed under the Apache License, Version 2.0.
