# a-news-provider

![Android CI](https://github.com/rh-id/a-news-provider/actions/workflows/gradlew-build.yml/badge.svg)
![Release Build](https://github.com/rh-id/a-news-provider/actions/workflows/android-release.yml/badge.svg)
![Emulator Test](https://github.com/rh-id/a-news-provider/actions/workflows/android-emulator-test.yml/badge.svg)


A simple RSS feed android application.
This project is intended for demo app for [a-navigator](https://github.com/rh-id/a-navigator) and [a-provider](https://github.com/rh-id/a-provider) library usage.
The app still works as production even though it is demo app.

## Project Structure

The app uses a-navigator framework as navigator and StatefulView as base structure,
combined with a-provider library for service locator,
and finally RxAndroid to handle UI use cases.

