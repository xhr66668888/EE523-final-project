# AeroScanner

## Name:

[Your Name]

# I. Introduction

AeroScanner is a Kotlin Android travel utility for airport-based trip planning. The app helps a traveler estimate whether a route is cost-effective by combining airport lookup, great-circle distance, ticket price conversion, baggage overage estimation, destination weather, carbon estimation, OCR scanning, location detection, and BLE luggage scale support.

The final version removes the earlier live flight-ticket search feature. Instead of depending on a paid or key-gated booking API, AeroScanner focuses on reliable trip analysis tools that match the course requirements: native Android APIs, external web APIs, camera-based OCR, geolocation, and BLE.

# II. Problem Statement

Travelers often compare flights only by the listed ticket price, but this hides important context: route distance, currency, unit system, baggage weight, airport location, destination weather, and the real cost per kilometer or mile. This is especially inconvenient for international students and frequent travelers who compare prices across currencies and routes.

The target audience is travelers who want a quick airport-aware trip analysis before buying or reviewing a ticket. AeroScanner solves this by turning IATA airport codes and a ticket price into an actionable route summary, with optional camera scanning and smart luggage scale integration.

# III. Design and Implementation

The app is implemented in Kotlin with Jetpack Compose and Material 3. It uses a tab-based structure:

- Planner: route, price, currency, unit, baggage, location, OCR, weather, cost, carbon, and segment analysis.
- History: saved trip analyses ranked by cost per kilometer.
- Scale: BLE luggage scale connection and live weight display.

Core implementation components:

- `AirportDatabase` loads a local airport cache from app assets and provides IATA airport lookup.
- `GeoTools` calculates great-circle distance between airport coordinates.
- `RouteTools` parses direct, multi-city, and open-jaw routes such as `PEK-ICN-SEA` or `PEK-ICN / GMP-HND`.
- `CostTools` calculates ticket cost, total trip cost, cost per distance unit, price level, baggage overage fees, and CO2e estimates.
- `TravelApiClient` communicates with two external APIs:
  - ExchangeRate-API open endpoint for daily exchange rates.
  - Open-Meteo forecast API for destination airport weather.
- `BoardingPassScanner` uses CameraX and ML Kit Text Recognition to scan boarding passes or receipts for airport codes and RMB/CNY prices.
- `NearestAirportFinder` uses Android fused location services to detect the nearest airport.
- `SmartScaleScanner` uses Android BLE APIs to connect to a Bluetooth weight scale service.
- `HistoryStore` persists trip results as JSON in app-local storage.

Challenges and solutions:

- The previous Skyscanner fare search required API-key configuration and was outside the improved scope, so it was removed to reduce fragility.
- Currency display needed a stable offline behavior, so the app fetches live CNY-based rates but falls back to approximate bundled rates when offline.
- BLE permission behavior differs before and after Android 12, so the Scale screen now requests the correct permission set for the device version.
- OCR can detect unrelated three-letter words, so scan results are filtered through the airport database when possible before auto-filling route fields.

# IV. Minimum UI Requirements

AeroScanner meets the minimum UI requirements in the following ways:

- Clear layout: bottom navigation separates Planner, History, and Scale workflows.
- Consistent design: the app uses shared color, spacing, typography, and radius tokens in `AeroscannerTheme`.
- Informative feedback: loading indicators are shown for airport database loading, exchange-rate refresh, location detection, weather fetch, route calculation, and BLE scanning. Error cards explain permission, input, network, and BLE problems.
- Responsive design: Compose layouts use full-width controls, scrollable content, weighted rows, and compact cards so the app adapts to different screen sizes.

# V. Additional Features

Features beyond the minimum requirements:

- Multiple external APIs: ExchangeRate-API for live currency conversion and Open-Meteo for destination weather.
- Currency switching: CNY, USD, EUR, GBP, and JPY display support.
- Unit switching: metric and imperial distance, weight, temperature, and wind speed display.
- Baggage estimator: optional bag weight and allowance inputs estimate overweight fees.
- CO2e estimate: route distance is converted into an estimated passenger-flight emissions value.
- Multi-city and open-jaw route parsing: supports real trip patterns beyond a simple origin-destination pair.
- OCR scanning: camera scanning can auto-fill airport codes and price.
- Nearest airport detection: geolocation can fill the origin airport.
- BLE luggage scale support: scans for a compatible Bluetooth weight scale and displays live weight.
- Animated feedback: result, error, scan, weather, and status sections use Compose visibility transitions.

# VI. Testing and Evaluation

Testing performed:

- Built the Android debug APK successfully with Gradle:
  - Command: `JAVA_HOME=/tmp/aeroscanner-jdk ANDROID_HOME=/home/hx666/Android/Sdk ANDROID_SDK_ROOT=/home/hx666/Android/Sdk PATH=/tmp/aeroscanner-jdk/bin:$PATH ./gradlew assembleDebug`
  - Result: `BUILD SUCCESSFUL`
- Verified that the removed flight-ticket search feature no longer appears in source strings or UI text.
- Verified that the app compiles after adding the exchange-rate API, weather API, unit conversion, currency display, baggage estimator, history updates, BLE permission flow, and OCR filtering.
- Ran Android lint with `./gradlew lintDebug`; blocking lint errors were fixed and lint completed successfully.
- Verified that the debug APK is generated at `AeroScanner/app/build/outputs/apk/debug/app-debug.apk`.

Remaining manual tests recommended before submission:

- Install the APK on an Android device.
- Test location permission and nearest-airport detection outdoors or with location services enabled.
- Test camera OCR on a real boarding pass or printed sample.
- Test BLE scan with a compatible Bluetooth weight scale.
- Record the required demo video after these device tests.

# VII. Conclusion

AeroScanner demonstrates the course topics through a cohesive travel assistant rather than a narrow ticket search tool. The final app integrates external APIs, camera OCR, geolocation, BLE, local data persistence, route parsing, and polished user feedback. Future improvements could include airline-specific baggage rules, map visualization, signed release builds, and cloud sync for saved trips.

# VIII. Figma

Figma link: [Add your Figma link before submission]

# IX. Demo Video

YouTube demo link: [Add your YouTube demo video link before submission]

# X. References

- Android Developers: CameraX documentation, https://developer.android.com/media/camera/camerax
- Android Developers: Bluetooth Low Energy documentation, https://developer.android.com/develop/connectivity/bluetooth/ble/ble-overview
- Android Developers: Location services documentation, https://developer.android.com/develop/sensors-and-location/location
- Google ML Kit: Text Recognition documentation, https://developers.google.com/ml-kit/vision/text-recognition/v2
- ExchangeRate-API Open Access Endpoint, https://www.exchangerate-api.com/docs/free
- Open-Meteo Forecast API, https://open-meteo.com/

# XI. Appendices

## Rubric Mapping

- Functionality and usability: includes at least five covered topics: external APIs, Camera, Geolocation, BLE, and local storage/native Android APIs.
- Code quality: feature code is separated into focused tools and clients: `TravelApiClient`, `TravelDisplay`, `CostTools`, `RouteTools`, `GeoTools`, `HistoryStore`, `BoardingPassScanner`, and `SmartScaleScanner`.
- Final report: all report template sections are included.
- Bonus: multiple external APIs, animated UI feedback, and innovative travel-specific features are included. A live demo should be performed with the instructor or TA if available.
