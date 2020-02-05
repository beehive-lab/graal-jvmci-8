# Welcome to graal-jvmci-8

This is a fork of https://github.com/graalvm/graal-jvmci-8.

## Building JVMCI JDK 8

To create a JVMCI-enabled JDK 8, make sure you have the forked version of [`mx`](https://github.com/beehive-lab/mx/tree/tornado) on your system and that you are checked out on the `tornado` branch.
Also point the `JAVA_HOME` env variable to a valid installation of a JDK 8.
Then run the following commands:

```
git clone https://github.com/beehive-lab/graal-jvmci-8/tree/master
cd graal-jvmci-8
mx build
```

The build step above should work on all [supported JDK 8 build platforms](https://wiki.openjdk.java.net/display/Build/Supported+Build+Platforms).
It should also work on other platforms (such as Oracle Linux, CentOS and Fedora as described [here](http://mail.openjdk.java.net/pipermail/graal-dev/2015-December/004050.html)).