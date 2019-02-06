# Pervasive Code's Java Concurrent Utilities

This library contains classes for running and timing concurrent tasks.

## Overview of included classes

Javadocs are available on `javadoc.io`:

[![Javadocs](https://www.javadoc.io/badge/com.pervasivecode/concurrent-utils.svg)](https://www.javadoc.io/doc/com.pervasivecode/concurrent-utils)

See the separate [OVERVIEW.md](OVERVIEW.md) file for a description of what interfaces and classes are included.
(Overview content is taken from class Javadoc comments, so there's no need to read both.)

## Including it in your project

Use groupId `com.pervasivecode`, name `concurrent-utils`, version `0.9` in your build tool of choice.


### Gradle Example

If you are using Gradle 4.x - 5.x, put this in your build.properties file:

```
// in your build.gradle's repositories {} block:
    mavenCentral();

// in your build.gradle's dependencies {} block:
    implementation 'com.pervasivecode:concurrent-utils:0.9'

    // or, if you prefer the separated group/name/version syntax:
    implementation group: 'com.pervasivecode', name: 'concurrent-utils', version: '0.9'
```


## How to use it in your code

See the [Example Code][] section in [OVERVIEW.md](OVERVIEW.md) for details.

## Contributing

See [DEVELOPERS.md](DEVELOPERS.md) and [GRADLE_INTRO.md](GRADLE_INTRO.md) if you want to build and hack on the code yourself.


## Copyright and License

Copyright © 2018 Jamie Flournoy.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.


[example code]: OVERVIEW.md#example-code
