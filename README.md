# ConceptNet Android

Native Android application built with Kotlin and Jetpack Compose.

## Features
- Live concept lookup using the public ConceptNet API
- Semantic relations and edge weights
- Related concept lookup
- Simple knowledge graph visualization
- Locally saved concepts
- Language code selection (for example: en, ta, fr)

## Build
Open this repository in Android Studio with JDK 17 and Android SDK Platform 35, then select **Build > Build APK(s)**.

Alternatively, GitHub Actions builds a debug APK on every push to `main`, and can also be started manually from **Actions > Build Android APK > Run workflow**.

## API and attribution
Data source: [ConceptNet](https://conceptnet.io/) and [ConceptNet API](https://api.conceptnet.io/).
ConceptNet data is licensed under [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). Retain attribution and comply with the share-alike terms when redistributing adapted data.
