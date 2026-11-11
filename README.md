# ConceptNet Android

Native Android app built with Kotlin and Jetpack Compose. The supported search languages are **English (en)** and **Tamil (ta)** only.

## Hybrid online/offline search

1. **Online:** Query the public ConceptNet 5 API when it is reachable.
2. **Local cache:** Store successful concept lookups and their semantic edges in an on-device SQLite database.
3. **Offline starter graph:** Include a small bilingual starter vocabulary for common concepts such as tree/மரம், water/நீர், house/வீடு, book/புத்தகம் and dog/நாய்.
4. **Fallback:** If the API is unavailable, search the SQLite cache and then the bundled starter graph.

The bundled starter graph is intentionally small. It is not a complete offline copy of ConceptNet. Full offline coverage would require distributing a much larger licensed dataset and building a compact indexed database.

## Build

Open in Android Studio with JDK 17 and Android SDK Platform 35, or use **Actions → Build Android APK**. GitHub Actions builds a debug APK on pushes to `main`.

## Data and attribution

- [ConceptNet](https://conceptnet.io/)
- [ConceptNet API documentation](https://github.com/commonsense/conceptnet5/wiki/API)
- [ConceptNet downloads](https://github.com/commonsense/conceptnet5/wiki/Downloads)
- [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/)

ConceptNet-derived data must retain attribution and comply with the applicable share-alike terms.
