<!-- fork-notice -->
> **AA Mazda Monitor** is a modified fork of [AA Torque](https://github.com/agronick/aa-torque) by Kyle Agronick, licensed under GPL-3.0 (see `LICENSE.md`). Modified in 2026 by Echo: direct ELM327 connection, Mazda SkyActiv-D data, renamed app. Icons are credited in the in-app Credits screen (CC BY 3.0, Noun Project).

![CI Status](https://github.com/agronick/aa-torque/actions/workflows/ci.yml/badge.svg?branch=master)
![downloads](https://img.shields.io/github/downloads/agronick/aa-torque/total.svg)


# Torque Plugin for Android Auto 

## Video Walkthrough
(Click to open in YouTube)
[![Click to open in YouTube](https://github.com/agronick/aa-torque/assets/2042303/b735fc1c-8732-4237-8a8d-a540a0d1f778
)](https://www.youtube.com/watch?v=gYJJ9M9a0m8)


This is a performance monitor based on [Chillout's Performance Monitor](https://github.com/jilleb/mqb-pm) which was based on Martoreto's aa-stats.
The app has been modified with a focus on making it more customizable with the ability
to obtain any PID from Torque. Torque pro is required to use this app.

The original app focused on VAG (Volkswagen Auto Group) cars. This version focuses on exposing
all data from Torque in a way that is as customizable as possible.

## Help and helping
- Visit [the dicussions page](https://github.com/agronick/aa-torque/discussions) to ask questions and get support
- English isn't your native language? [Help translate this app!](https://poeditor.com/join/project/yttme0y1VZ) Many of the translations are incomplete.
- Help spread the word. Make posts on forums and other social networks showing off your setup.
- This app is taking hundreds of hours of work. If you enjoy it consider donating on [Paypal](https://www.paypal.me/kagronick) or [Github Sponsors](https://github.com/agronick).

# Main features:
- Display data from Torque including custom PIDs
- Many themes, fonts, incons, and backgrounds to choose from
- Up to 10 dashboards
- Rotary dial support
- Supports custom expressions using [EvalEx](https://ezylang.github.io/EvalEx/references/functions.html)
- Backup, restore, and share your configuration

# Instructions:

## Prepare
- Install [Torque Pro](https://play.google.com/store/apps/details?id=org.prowl.torque&hl=en_US&gl=US) You will need this app and a cheap OBD2 Bluetooth module.

## Install
[Video guide for how to install](https://www.youtube.com/watch?v=DDxPrPzxZ3k)

- Download the latest [release](https://github.com/agronick/aa-torque/releases). Pre-releases can only be installed through Kingstaller.
- Make sure Android Auto is in developer mode: Open Android Auto (while not connected to the car), go to About. Tap the "About Android Auto" header 10 times until you see a toast message saying you're a developer. From the right top corner select developer options, scroll down and make sure you check "Unknown sources". This will allow programs from non-Playstore apps to be run on Android Auto. [See a picture guide how to enable Developer mode here](https://www.howtogeek.com/271132/how-to-enable-developer-settings-on-android-auto/).
- Install the APK that you downloaded in the _Prepare_ section using your normal package installer
- Open _AA Torque Settings_
- Select the options menu and select force update. Follow the process so your phone will reinstall
  it thinking it came from the Play Store.
- If this fails, you can try using [Kingstaller](https://github.com/fcaronte/KingInstaller/releases)
  so your phone thinks it came from the Play Store. Reference the Kingstaller README if you have
  issues installing.

Note: Do not open a bug for an installation issue. You need to bypass restrictions put in place by Google to get this app working on Android Auto. These instructions will work for most phones. I can not troubleshoot every phone. Pixel phones have special instructions on the [Kingstaller README](https://github.com/fcaronte/KingInstaller). 

The Kingstaller README has this to say about Pixel Phones:

> For this phone you need to reinstall\update the current Google PackageInstaller, you can download the same version from ApkMirror or similar site, or you can get the apk directly from your phone using a file manager with that feature (I'm use mixplorer) then install the apk as normal and now you can use kinginstaller to install the app and will work!

There seems to be issues with Android 14 as well. If you upgrade to Android 14 you may need to
uninstall and reinstall. Check the [discussions](https://github.com/agronick/aa-torque/discussions)
page for help installing.
If you have an AAWireless dongle, enabling developer mode in AAWireless can work around it.

## Using
- Open the AA Torque settings, grant all the rights it's requesting. If you don't do this, the app will NOT work.
- Hook your phone to your car's USB, start Android Auto on your unit.
- You can find the AA Torque in the menu on the lower right in Android Auto, the one with the dashboard clock on it.

# Screenshots:
Some screenshots of the app:

<img src="https://github.com/agronick/aa-torque/assets/2042303/e31b0598-25ec-4003-bcee-11e5e108d3a4" width="100%" alt="" />

<img src="https://github.com/agronick/aa-torque/assets/2042303/bf40ed97-adf3-4923-bbc5-63a618899173" width="100%" alt="" />

*Album art background with ray instead of needle*

<img src="https://github.com/agronick/aa-torque/assets/2042303/a99571f9-0bb3-43ec-89ce-938e41506e97" width="100%" alt="" />

*Chart view*

<img src="https://github.com/agronick/aa-torque/assets/2042303/229315c8-ad3b-42e6-86e6-e7fe7abb16a8" width="100%" alt="" />

<img src="https://github.com/agronick/aa-torque/assets/2042303/698c666b-5e3c-4611-80a5-8e767b04186a" width="100%" alt="" />



## Custom Expressions
AA Torque uses [EvalEx](https://ezylang.github.io/EvalEx) for custom expressions. You can find documentation [here](https://ezylang.github.io/EvalEx/references/functions.html).
