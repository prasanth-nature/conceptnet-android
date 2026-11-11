# ConceptNet Android

Native Android application built with Kotlin and Jetpack Compose. Search languages are English (en) and Tamil (ta).

## Complete offline English-Tamil graph

The repository builds a filtered SQLite database from the official ConceptNet 5.7.0 assertion dump.

The filter retains every assertion for which **both endpoints** are English or Tamil concepts. It preserves all four endpoint-language combinations:

- English → English
- Tamil → Tamil
- English → Tamil
- Tamil → English

The build completed with these verified counts:

| Metric | Count |
|---|---:|
| Source assertions scanned | 34,074,917 |
| Retained assertions | 3,435,272 |
| English → English | 3,423,004 |
| Tamil → Tamil | 376 |
| English → Tamil | 2,719 |
| Tamil → English | 9,173 |
| Distinct concept nodes | 1,798,193 |

**Important coverage note:** ConceptNet 5.7.0 contains very limited Tamil assertions. The database includes all matching Tamil assertions present in this release; it does not invent or translate missing Tamil knowledge. Most retained assertions are English-English.

Each retained assertion preserves its original relation URI, start and end concept URIs, weight, and complete source metadata JSON (including source/license details).

## Download the database

The compressed SQLite database is published as a GitHub Release:

- [ConceptNet English-Tamil 5.7.0 database release](https://github.com/prasanth-nature/conceptnet-android/releases/tag/conceptnet-en-ta-5.7.0)
- [Download compressed SQLite database](https://github.com/prasanth-nature/conceptnet-android/releases/download/conceptnet-en-ta-5.7.0/conceptnet-en-ta-5.7.0.sqlite.gz)
- [SHA-256 checksum](https://github.com/prasanth-nature/conceptnet-android/releases/download/conceptnet-en-ta-5.7.0/conceptnet-en-ta-5.7.0.sqlite.gz.sha256)

The compressed database is approximately 301 MiB; the installed SQLite file is approximately 2.0 GiB. Allow at least 2.5 GB of free internal storage during download and extraction. The app downloads and installs this database from its About tab. Once installed, local graph searches do not require the ConceptNet API.

## Rebuild the database

Run the GitHub Actions workflow **Build English-Tamil ConceptNet database**, or locally:

```bash
curl -fL "https://s3.amazonaws.com/conceptnet/downloads/2019/edges/conceptnet-assertions-5.7.0.csv.gz" -o conceptnet-assertions-5.7.0.csv.gz
python3 tools/build_bilingual_db.py --input conceptnet-assertions-5.7.0.csv.gz --output conceptnet-en-ta-5.7.0.sqlite
```

## Build the Android APK

Open in Android Studio with JDK 17 and Android SDK Platform 35, or use **Actions → Build Android APK**. GitHub Actions builds a debug APK on pushes to `main`.

## Sources and licensing

- [ConceptNet 5.7.0 downloads and TSV format](https://github.com/commonsense/conceptnet5/wiki/Downloads)
- [ConceptNet source project](https://github.com/commonsense/conceptnet5)
- [ConceptNet copying and sharing guidance](https://github.com/commonsense/conceptnet5/wiki/Copying-and-sharing-ConceptNet)
- [Creative Commons Attribution-ShareAlike 4.0](https://creativecommons.org/licenses/by-sa/4.0/)

ConceptNet is distributed under CC BY-SA 4.0, with some assertions carrying other licenses. The original per-assertion metadata is retained in the database.
