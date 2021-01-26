# ISD-R Access Provider
## Introduction

You just want to study how eSIM works with consumer-model described GSMA SGP.21 and SGP.22, but cannot find a good way to get a plug-in (4FF/nano) eSIM card, right? I also sent queries to some eSIM service providres and got the same responses that such a product is not available right now (maybe in the future also). Though I wanted to prepare a development/study environment like the following figure 1, I gave it up for now. Then, I came up with an easy, not cool but possible compromise solution in the figure 2 below.

<img src="https://user-images.githubusercontent.com/44401044/105844907-4c93ba00-601d-11eb-8b8d-b81e9e95e14c.png" width="800">

This application contains a tiny content provider for communicating with ISD-R in eSIM soldered on Android device. Do you have a Google Pixel 4? If you do not need to have it for daily use, you can make it rooted and flash the recent version of Google/AOSP user-debug software image in order to utilize it as a bit bigger eSIM-like device. See your phone again. It is not a phone but an eSIM-like something to you, okay?

## Usage

It is a simple content provider interface through adb command. Here is an example of a pair of command and response. The ES10x command sent via this interface is ProfileInfoListRequest 'BF2D' and the requested data objects are profileState '9F70', profileName '92', profileClass '95' and notificationConfigurationInfo 'B6'.

```
$ adb shell content query --uri content://com.github.cheeriotb.isdrap.provider/store/BF2D075C059F709295B6
Row: 0 response=BF2D7DA07BE31D9F7001009214416E726974737520546573742050726F66696C65950100E35A9F700100920349494A950102B64C302480020410811E534D2D56342D3033332D412D47544D2E50522E474F2D4553494D2E434F4D302480020780811E534D2D56342D3033332D412D47544D2E50522E474F2D4553494D2E434F4D9000
```

It is a bit hard to interpret ProfileInfoListResponse responded from ISD-R manually, so please refer to the following table. You or your application software just has to run the adb command with your DER-encoded ES10x command data, then receive and interpret response from ISD-R in accordance with the GSMA standard specification. The APDU header part is added by the content provider and ISD-R is also automatically selected internally.

|Level 1|Level 2|Level 3|Level 4|
|:---|:---|:---|:---|
|ProfileInfo|profileState|disabled (0)||
||*profileName*|Anritsu Test Profile||
||*profileClass*|test (0)||
|*ProfileInfo*|*profileState*|disabled (0)||
||*profileName*|IIJ||
||*profileClass*|operational (2)||
||*notificationConfigurationInfo*|*profileManagementOperation*|notificationDelete (3)|
|||*notificationAddress*|SM-V4-033-A-GTM.PR.GO-ESIM.COM|
||*notificationConfigurationInfo*|*profileManagementOperation*|notificationInstall (0)|
|||*notificationAddress*|SM-V4-033-A-GTM.PR.GO-ESIM.COM|

# Dependency

It is necessary to push an APK and a permission file to the system partition, so your eSIM-capable Android phone must be rooted. And also, any kind of LPA implementation should not be in the system because only one application can have a right to talk with ISD-R in the system. This application should have the right. In order to satisfy that, it is the easiest way to flash a recent version of Google/AOSP user-debug binary by using Android Flash Tool. See the next section.

Note that this application is well adapted to Google Pixel 4 that I have. My assumption is that we can use this for newer products like Google Pixel 5 without any code change, but not tested.

# Setup

First, you have to prepare a rooted Android device. If your phone is a Google's phone product, please try Android Flash Tool (https://flash.android.com/) for flashing a recent version of Google/AOSP user-debug build.

<img src="https://user-images.githubusercontent.com/44401044/105844927-55848b80-601d-11eb-97c3-aed50fe61fdd.png" width="400">

Then, please push an APK and the permission file [com.github.cheeriotb.isdrap.xml](app/src/misc/com.github.cheeriotb.isdrap.xml) to the system partition of your rooted Android device. Do not forget to make the folder 'IsdrAccessProvider' under /system/priv-app in advance.

```
$ adb root
$ adb remount
$ adb push app-debug.apk /system/priv-app/IsdrAccessProvider
$ adb push com.github.cheeriotb.isdrap.xml /system/etc/permissions
$ adb reboot
```

# Licence

This software is released under the MIT License, see LICENSE.

# Author

Cheerio (cheerio.the.bear@gmail.com)

# References

* GSMA
    * SGP.22 v2.2.2
* GlobalPlatform
    * Card Specification v2.3.1
