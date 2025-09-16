# Flex Compositor
on Zonal - SA8295,Rcar

## Installation Steps
**1. Build & Install FlexCompositor APP**
* Different build variants are available for use with SA8295 or R-Car platforms
![Android Studio Build Variants](data/BuildVariants.png)

**2. Upload the initialization file**
In the case of an SA8295 device
* adb root
* adb push data/SA8295/flexCompositor.db /data/user/10/com.auo.flex_compositor/databases/flexCompositor.db 
* adb shell "chcon -R u:object_r:app_data_file:s0:c522,c768 /data/user/10/com.auo.flex_compositor/databases/"

<span style="color:red">The same applies to other devices; the data directory also contains a flexCompositor.db file for each of them. </span>


**3. Zonal System Architecture**
![Zonal System Architecture](data/System.png)
