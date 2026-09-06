# Locate Cardboard — Android Widget

A personal Android companion for [Locate Cardboard](https://cardboardfetch.jrobin17.chatgpt.site).

The resizable home-screen widget displays one chosen Magic: The Gathering printing, its card image, and available normal, foil, or etched USD market estimates. It retrieves live card data through the existing Locate Cardboard site and only caches the image used by each active widget.

## What is included

- Search with Scryfall autocomplete.
- A printing selector for every English printing returned by Scryfall.
- Normal, foil, and etched price estimates when available.
- A card-focused, black-and-orange junkyard widget.
- Manual price and image refresh.
- Responsive compact and full widget layouts.
- A Change control that reopens the card-selection screen.
- A tap on the card that opens the full Locate Cardboard website.
- Separate saved selections for multiple widget instances.

## Build requirements

- Android Studio with Android SDK 35 installed.
- JDK 17.
- Android Gradle Plugin 8.13.0 and Gradle 8.13. Android Studio downloads these during the first project sync when needed.

Open this folder as a project in Android Studio, allow Gradle sync to finish, and choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**. The debug APK will be created at:

`app/build/outputs/apk/debug/app-debug.apk`

The project uses only Android platform APIs at runtime. There are no third-party app libraries.

## Automated APK build

The project also includes a GitHub Actions build at `.github/workflows/build-apk.yml`.
After the project is placed in a GitHub repository, open **Actions**, select
**Build installable APK**, and choose **Run workflow**. When the build finishes,
download the `locate-cardboard-android-apk` artifact and unzip it to get
`Locate-Cardboard-Widget.apk`.

The workflow builds the same personal-use debug APK as Android Studio. It does
not publish the app, request store access, or volunteer the widget for a career
in software distribution.

## Install for personal use

Transfer the APK to the Android phone, allow installation from the chosen file app when Android asks, and open the APK. The Play Store is not required for a personal sideload.

After installation:

1. Long-press an empty area of the home screen.
2. Choose **Widgets**.
3. Find **Locate Cardboard**.
4. Place and resize the widget.
5. Search for a card and choose its printing.
6. Tap **Pin cardboard**.

The launcher may word those steps differently. Android manufacturers saw a perfectly serviceable sequence and naturally gave it regional dialects.

## Data and storage

Card data, images, and daily market estimates come from Scryfall through the public Locate Cardboard site. The app stores the selected printing details and one scaled JPEG per active widget in the app's private storage. Removing a widget removes its saved selection and cached image.

The app requests only internet access. It does not require an account, analytics, location, contacts, file access, or the increasingly ambitious collection of permissions some flashlight apps consider necessary.
