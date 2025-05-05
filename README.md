# Flex Compositor

## Element
**1. Virutal Display**
* Customizable size.
* Set the app to be launched in this virtual display.
- virtualDisplay=virtual-1,1920x1080,\\
        touch(virtual-1),\\
        app(org.cid.example/org.qtproject.qt.android.bindings.QtActivity)

**2. Display**
* Physically connected display.
- dpDisplay0=dp-0,1920x1080 *dp[i] -> In Android OS, the value of [i] corresponds to the display ID.

**3. VideoStream**
* Server-Encoder / Client-Decoder.
* Customizable size.
* IP and port need to be configured.
* During the mapping phase, it will be determined whether it is a Server-Encoder or a Client-Decoder.
- videoStream=stream-1,1920x1080,\\
        server(192.168.1.3,50000)

## Example
* Created a virtual display and displayed its content on the local screen dp-0 and simultaneously transmitted through a video stream(Server-Encoder).

```
;====SA8295 flexCompositor.ini===
[screen]
;screenName=interface-number,resolution,attrib(value…)
;    interface : virtual/stream/hdmi/dp/lvds/vnc
;    attribute enumeration
;        touch(interface-num,x,y,width,height)
;            interface: virTouch,streamTouch,hidTouch
;        app(pathname)
virtualDisplay=virtual-1,1920x1080,\
        touch(virtual-1),\
        app(org.cid.example/org.qtproject.qt.android.bindings.QtActivity)
dpDisplay0=dp-0,1920x1080
videoStream=stream-1,1920x1080,\
        server(192.168.1.3,50000)


[mapping]
;screen routing map
;source(x,y,width,height)->sink(x,y,width,height)
virtualDisplay(0,0,1920,1080)->dpDisplay0(0,0,1920,1080)
virtualDisplay(0,0,1920,1080)->videoStream(0,0,1920,1080)
```

* Receive the video stream(Client-Decoder) content and display it on hdmi-0.

```
;====RCar flexCompositor.ini===
[screen]
;screenName=interface-number,resolution,attrib(value…)
;    interface : virtual/stream/hdmi/dp/lvds/vnc
;    attribute enumeration
;        touch(interface-num,x,y,width,height)
;            interface: virTouch,streamTouch,hidTouch
;        app(pathname)
dpDisplay=hdmi-0,1920x1080,\
        touch(hid-1)
videoStream=stream-1,1920x1080,\
        server(192.168.1.3,50000)


[mapping]
;screen routing map
;source(x,y,width,height)->sink(x,y,width,height)
videoStream(0,0,1920,1080)->dpDisplay(0,0,1920,1080)
```

