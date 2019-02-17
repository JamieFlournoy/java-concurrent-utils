# How to use Gradle with this project

This project uses the Gradle build system to automate builds. It currently uses Gradle version 5.2.1. If you have no idea what Gradle is or how to use it, you may want to refer to the [Gradle User Manual for version 5.2.1](https://docs.gradle.org/5.2.1/userguide/userguide.html).

`build.gradle` is the main Gradle config file for this project. Pretty much everything is in there; `settings.gradle` is the only other file.

See [Developer Instructions](DEVELOPERS.md) for how to build the code, once Gradle is working properly. The rest of this document explains how to get Gradle working.

## The Gradle Wrapper

The [Gradle "wrapper"](https://docs.gradle.org/5.2.1/userguide/gradle_wrapper.html) is a script that can more or less bootstrap a set of Gradle jars on a new machine. So it's not necessary to install Gradle on a host before running `./gradlew`, which is convenient.

Also, the Gradle wrapper pins the build to a specific Gradle version, which is a good thing since the `build.gradle` file has proven to be very version-sensitive vs. various "how to do ___ with Gradle" instructions designed for older versions of Gradle.

You can tell if Gradle is basically working on a given host by running `./gradlew tasks` from the root directory of this build.

## Upgrading Gradle

To upgrade Gradle to a new version, edit `./gradle/wrapper/gradle-wrapper.properties` and change the JAR file version. The next run of ./gradlew should download the new Gradle JARs and use them for that build, from then onward.

However, this doesn't replace the wrapper scripts themselves (`gradlew` and `gradle.bat`). To do that, run `./gradlew wrapper`, and that will regenerate the wrapper scripts.

## Starting over (if the wrapper scripts break)

If you mess up the wrapper scripts, install Gradle using the [normal installation instructions](https://gradle.org/install) and then run `gradle wrapper` in the root directory of this build. This should regenerate a working set of wrapper scripts.

