# Model Creator CE Launcher 1.3.6

Cross-platform Java launcher/updater for Model Creator Community Edition.

## Features

- Checks the latest GitHub Release from `kristitrnka/ModelCreator-CE`
- Shows Installed / Latest versions before launching
- Offers **Install**, **Not now**, or **Ignore this version**
- Downloads and verifies an optional `.sha256` asset
- Keeps the installed editor under `~/.modelcreator-ce`
- Contains a bundled fallback editor JAR
- Uses Intel Java 8 through Rosetta on Apple Silicon when available

## Build

1. Install JDK 8 or newer.
2. Put the editor JAR at `payload/editor.jar`.
3. Run:

```sh
./build.sh
```

Windows:

```bat
build.bat
```