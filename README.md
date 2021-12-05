# a-news-provider

![Android CI](https://github.com/rh-id/a-news-provider/actions/workflows/gradlew-build.yml/badge.svg)
![Release Build](https://github.com/rh-id/a-news-provider/actions/workflows/android-release.yml/badge.svg)


A simple RSS feed android application.
This project is intended for demo app for [a-navigator](https://github.com/rh-id/a-navigator) and [a-provider](https://github.com/rh-id/a-provider) library usage.
The app still works as production even though it is demo app.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/m.co.rh.id.a_news_provider/)

## Project Structure

The app uses a-navigator framework as navigator and StatefulView as base structure,
combined with a-provider library for service locator,
and finally RxAndroid to handle UI use cases.
