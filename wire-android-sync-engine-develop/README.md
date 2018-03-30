# Wire

[![Wire logo](https://github.com/wireapp/wire/blob/master/assets/header-small.png?raw=true)](https://wire.com/jobs/)

This repository is part of the source code of Wire. You can find more information at [wire.com](https://wire.com) or by contacting opensource@wire.com.

You can find the published source code at [github.com/wireapp](https://github.com/wireapp). 

For licensing information, see the attached LICENSE file and the list of third-party licenses at [wire.com/legal/licenses/](https://wire.com/legal/licenses/).

# ZMessaging

This repository contains sync library used by [Wire for Android](https://github.com/wireapp/wire-android).

## Dependencies

- [SBT](http://www.scala-sbt.org/)
- Android SDK

## Building

Build aar libraries and publish them to local Maven repository:

```
sbt publishM2
```
