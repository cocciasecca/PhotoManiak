# Google Cast

Available only in the **nonFOSS** edition. The FOSS APK excludes the Google SDK and all Cast actions.

## Use

1. Connect the phone and a Chromecast/Google Cast TV or display to the same Wi-Fi network. Guest/client-isolated networks and VPNs may prevent the TV from reaching the phone.
2. Tap **Cast** in the main screen or **More options → Cast** in the photo viewer and select the TV.
3. Open a photo or video. Swiping through the viewer sends the current item to the connected TV. Video playback on the phone stops while connected to Cast.
4. Use the Cast controls to pause/play, seek in supported videos, or **Stop casting**. The controls remain available when returning to the gallery.

Keep PhotoManiak open during casting. The app keeps the screen awake while connected. There is no dedicated background relay service; locking the phone, terminating the app, or switching networks may interrupt casting. Reconnect/open the item again if playback fails. This is Google Cast, not Miracast, AirPlay, or generic DLNA.

## Delivery and privacy

The TV receives a temporary local HTTP URL served by the phone on its active Wi-Fi/Ethernet IPv4 address. An unguessable 256-bit capability identifies only the currently selected item. The relay accepts only GET/HEAD/OPTIONS, supports byte ranges, and is not a general URL proxy or file server. Changing the item revokes its previous URL; stopping casting, signing out, or disposing the app's model closes the relay and cancels active requests.

The Infomaniak bearer token remains on the phone. Authenticated kDrive reads use the existing read-only client, including its origin restrictions and credential stripping for download redirects. The TV does not receive the token, and PhotoManiak creates no public kDrive links or uploads. Local media delivery uses HTTP on the LAN, so use a trusted network. The capability URL grants access to the displayed media for as long as it remains active.

Photos are decoded on the phone and sent as a JPEG fitting within 1280×720; the original file on kDrive is unchanged. Photo decoding/orientation support depends on Android/Coil and the source format. Animated images are shown as still pictures. Videos stream through the phone with Range headers for seeking; they are not fully downloaded in advance and are not transcoded. Container, codec, resolution, and seeking support depend on the receiver and kDrive's range responses.

## SDK and lifecycle

The app uses Google's official Cast sender framework and Default Media Receiver, with no custom receiver deployment or developer application ID. It does not resume a saved session after process death because the previous local URL is no longer valid. A suspended session closes its relay; a resumed session recreates it when a viewer item is opened.

This build includes the Google Play services Cast SDK. Cast requires working Google Play services on the phone; when unavailable, the Cast button explains the requirement and other gallery features remain usable. The native media-route dialog handles device discovery and selection. No Google sign-in is added to PhotoManiak.

Automated tests exercise relay HTTP behavior, range requests, URL revocation, token isolation, upstream failures, and the no-Play-services UI. Actual TV discovery, remote playback, and codec compatibility require a physical receiver test; emulator checks cannot establish those results.

References: [Android sender integration](https://developers.google.com/cast/docs/android_sender/integrate), [supported receiver media](https://developers.google.com/cast/docs/media), [Cast SDK release notes](https://developers.google.com/cast/docs/release-notes).
