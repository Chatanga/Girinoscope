# Girinoscope

A simple graphical user interface for
[Girino, a Fast Arduino Oscilloscope](http://www.instructables.com/id/Girino-Fast-Arduino-Oscilloscope/).
This application is meant to be used with the Girino device you will have built by following the instructable made by
[Caffeinomane](https://www.instructables.com/member/Caffeinomane/).

![Screen capture](doc/capture.png)

## Usage

Since this little application is intimately bound to Girino,
the various settings provided by Girinoscope are not detailled here.
If you have already built your own Girino and studied the firmware,
you should already be familiar with them.

**Wait duration parameter**

The Arduino code provided in the
[Girino Instructable](http://www.instructables.com/id/Girino-Fast-Arduino-Oscilloscope/)
doesn't handle very well the `wait duration` parameter that you can set using the vertical green rule
(the horizontal orange rule is for the threshold to trigger the acquisition).
There is a small bug when changing the value through the serial interface
which can easily be solved by applying the following patch manually:

_Girino.h, line 41:_

``` c
// Replaced 3 by 4 since the wait duration range is [0, 1280[.
#define COMBUFFERSIZE   4   // Size of buffer for incoming numbers
```

_Girino.ino, line 224:_

``` c
// Added a necessary x2 factor since we read 16 bits now.
delay(COMMANDDELAY * 2);
```

_Girino.ino, line 229:_

``` c
// Replaced 'uint8' by 'uint16' for the same reason.
uint16_t newT = atoi( commandBuffer );
```

With this simple correction, you should now be able to change the `wait duration` without problem.
However, remember that this duration is the time spent
(or, more exactly, the number of data samples measured) by Girino _after_ the trigger.
Per instance, a `wait duration` of `580` gives you `1280 - 580` data measures before the trigger and `580` after.
If this trigger occurs too early, you will not see much from the past.
In fact, since Girino resets its data buffer on each acquisition, you will mostly get zeros.
The screen captures in the `doc` folder show some cases of such missing data
(since the observed signals are periodic, Girino can’t catch more than a period before a trigger occurs).

**Prescaler parameter**

The prescaler parameter also suffers some limitations signaled by the UI using the following colors:

- black if it should work without problem,
- orange if it won’t work without optimizing the Girino code,
- red if it won’t work at all (at least, it never did at home).

The code optimization consists in applying the [advices](doc/girino_optimization.md) given by
[womai](http://www.instructables.com/member/womai/) in the
[Girino Instructable](http://www.instructables.com/id/Girino-Fast-Arduino-Oscilloscope/).

**A bit of warning**

The Girinoscope is not an oscilloscope.
An Arduino (that is an ATmega128) is pushed to its limits with the firmware provided by the instructable.
It spends all its cycles capturing a signal and, once a frame is captured,
simply sends it on the serial line without being able to do anything else.
That’s why you can’t use a Girino device to continously acquire and display a signal.
This system can only capture a sequence of discontinuous short frames of the signal.
That’s also the reason why there is no way to disable the trigger mechanism.

## Installation

### From the Snap Store

**Warning:** This Snap has [some issues](doc/snap_workaround.md).

[![Get it from the Snap Store](https://snapcraft.io/static/images/badges/en/snap-store-black.svg)](https://snapcraft.io/girinoscope)

### From GitHub

Just copy the [latest release](https://github.com/Chatanga/Girinoscope/releases) somewhere
and launch the application by a simple double-click on the JAR
(provided the JAR has been given execution permissions).
In case your system doesn’t know how to handle a JAR,
you can launch it through a more explicit `java -jar Girinoscope-*-dist.jar`
Obviously, since this is a Java application, you need a [JRE 1.8 or higher](https://www.java.com/fr).

### From source

You just need a [JDK 1.8 or higher](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
and [Maven 3.3.9 or higher](https://maven.apache.org/).
Once these tools installed, a simple `mvn clean package` at the root of this project will do the job.
On success, you can run the application by issuing a `mvn exec:java`.
Of course, you can also launch the application without Maven using a `java -jar target/Girinoscope-*-dist.jar`.

## Troubleshooting

**A serial port is detected but cannot be opened.**

On Linux (Ubuntu), you need to be a member of the `dialout` group
(a `sudo usermod -a -G dialout $USER` should do the trick).
Note that on other distributions, the group(s) involved could be different.
In addition, if you have installed the Girinoscope from the Snap Store, there are [additional steps](doc/snap_workaround.md) to follow.
