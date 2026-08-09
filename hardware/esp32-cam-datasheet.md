![Image](md2\esp32-cam-datasheet_artifacts\image_000000_9337169ab5e6466261c91e017eab54cee6bca4bb613eaf02dea855a6b6101265.png)

## ESP32-CAM Module

![Image](md2\esp32-cam-datasheet_artifacts\image_000001_15a24ba4160a15b4d7d33b9ccb1aec2e09ccc0a1daceb990cf8fe4ab91630937.png)

Copyright © 2017 Shenzhen Ai-Thinker Technology Co., Ltd All Rights Reserved Page 1 of 4 Overview The ESP32-CAM has a very competitive small-size camera module that can operate independently as a minimum system with a footprint of only 27*40.5*4.5mm and a deep sleep current of up to 6mA. ESP-32CAM can be widely used in various IoT applications. It is suitable for home smart devices, industrial wireless control, wireless monitoring, QR wireless identification, wireless positioning system signals and other IoT applications. It is an ideal solution for IoT applications. ESP-32CAM adopts DIP package and can be directly inserted into the backplane to realize rapid production of products, providing customers with high-reliability connection mode, which is convenient for application in various IoT hardware terminals. - The smallest 802.11b/g/n Wi-Fi BT SoC Module - Low power 32-bit CPU,can also serve the application processor - Up to 160MHz clock speed ˈ Summary computing power up to 600 DMIPS - Built-in 520 KB SRAM, external 4MPSRAM - Supports UART/SPI/I2C/PWM/ADC/DAC - Support OV2640 and OV7670 cameras,Built-in Flash lamp. - Support image WiFI upload - Support TF card - Supports multiple sleep modes. - Embedded Lwip and FreeRTOS - Supports STA/AP/STA+AP operation mode - Support Smart Config/AirKiss technology - Support for serial port local and remote firmware upgrades (FOTA) Features Ai-Thinke

![Image](md2\esp32-cam-datasheet_artifacts\image_000002_827043b871f6c1c435a4573560bcdc2125f9bbdba5521a66cae432618590b8e4.png)

![Image](md2\esp32-cam-datasheet_artifacts\image_000003_4b3474d8efde2a682eaeafd68b8fee32098cfa1d499e62b59b9fc8a9809a15bf.png)

![Image](md2\esp32-cam-datasheet_artifacts\image_000004_9661d55b3cdbe6349f8e1484294a11d130809f5dbfc3a51c1d937625ec456e44.png)

## Product Specifications

| Module Model          | ESP32-CAM                                                                                                                                                                                                      |
|-----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Package               | DIP-16                                                                                                                                                                                                         |
| Size                  | 27*40.5*4.5 ˄ ±0.2 ˅ mm                                                                                                                                                                                        |
| SPI Flash             | Default 32Mbit                                                                                                                                                                                                 |
| RAM                   | 520KB SRAM +4M PSRAM                                                                                                                                                                                           |
| Bluetooth             | Bluetooth 4.2 BR/EDR and BLE standards                                                                                                                                                                         |
| Wi-Fi                 | 802.11 b/g/n/                                                                                                                                                                                                  |
| Support interface     | UART ǃ SPI ǃ I2C ǃ PWM                                                                                                                                                                                         |
| Support TF card       | Maximum support 4G                                                                                                                                                                                             |
| IO port               | 9                                                                                                                                                                                                              |
| UART Baudrate         | Default 115200 bps                                                                                                                                                                                             |
| Image Output Format   | JPEG( OV2640 support only ),BMP,GRAYSCALE                                                                                                                                                                      |
| Spectrum Range        | 2412 ~2484MHz                                                                                                                                                                                                  |
| Antenna               | Onboard PCB antenna, gain 2dBi                                                                                                                                                                                 |
| Transmit Power        | 802.11b: 17±2 dBm (@11Mbps) 802.11g: 14±2 dBm (@54Mbps) 802.11n: 13±2 dBm (@MCS7)                                                                                                                              |
| Receiving Sensitivity | CCK, 1 Mbps : -90dBm CCK, 11 Mbps: -85dBm 6 Mbps (1/2 BPSK): -88dBm 54 Mbps (3/4 64-QAM): -70dBm MCS7 (65 Mbps, 72.2 Mbps): -67dBm                                                                             |
| Power Dissipation     | Turn on the flash lamp and turn on the brightness to the maximum:310mA@5V Deep-sleep: Minimum power consumption can be achieved 6mA@5V Moderm-sleep: Minimum up to 20mA@5V Light-sleep: Minimum up to 6.7mA@5V |
| Security              | WPA/WPA2/WPA2-Enterprise/WPS                                                                                                                                                                                   |
| Power Supply Range    | 5V                                                                                                                                                                                                             |
| Operating Temperature | -20 ć ~ 85 ć                                                                                                                                                                                                   |
| Storage Environment   | -40 ć ~ 90 ć , < 90%RH                                                                                                                                                                                         |

Copyright © 2017 Shenzhen Ai-Thinker Technology Co., Ltd All Rights Reserved Page 2 of 4 Ai-Thinke Copyright © 2017 Shenzhen Ai-Thinker Technology Co., Ltd All Rights Reserved Page 3 of 4 Internal Pin Connect ESP32-CAM module picture output format rate Ai-Thinke

![Image](md2\esp32-cam-datasheet_artifacts\image_000005_9661d55b3cdbe6349f8e1484294a11d130809f5dbfc3a51c1d937625ec456e44.png)

| Weight   | 10g   |
|----------|-------|

| Format Size   |   QQVGA |   QVGA | VGA   | SVGA   |
|---------------|---------|--------|-------|--------|
| JPEG          |       6 |      7 | 7     | 8      |
| BMP 9         |         |      9 | -     | -      |
| GRAYSCALE     |       9 |      8 | -     | -      |

| CAM       | ESP32   | SD               | ESP32   |
|-----------|---------|------------------|---------|
| D0        | PIN5    | CLK              | PIN14   |
| D1        | PIN18   | CMD              | PIN15   |
| D2        | PIN19   | DATA0            | PIN2    |
| D3        | PIN21   | DATA1/Flash lamp | PIN4    |
| D4        | PIN36   | DATA2            | PIN12   |
| D5        | PIN39   | DATA3            | PIN13   |
| D6        | PIN34   |                  |         |
| D7        | PIN35   |                  |         |
| XCLK      | PIN0    |                  |         |
| PCLK      | PIN22   |                  |         |
| VSYNC     | PIN25   |                  |         |
| HREF      | PIN23   |                  |         |
| SDA       | PIN26   |                  |         |
| SCL       | PIN27   |                  |         |
| POWER PIN | PIN32   |                  |         |

![Image](md2\esp32-cam-datasheet_artifacts\image_000006_102a9bf4aa4a394b27e88fccdbb38a6a2762a3c675361efeba3a4324bbaba21c.png)

![Image](md2\esp32-cam-datasheet_artifacts\image_000007_c25393c47d01033ff12b69b8a7e6a18cc3c7df9119693f7747f95f0bc25a4849.png)

Address: 7/F, Fengze Building B, Huafeng Industrial Park 2th, Hangkong street,Xixiang Raod, Baoan, Shenzhen

Copyright © 2017 Shenzhen Ai-Thinker Technology Co., Ltd All Rights Reserved Page 4 of 4 Contact US Shenzhen Ai-Thinker Technology Co., Ltd China Website:www.ai-thinker.com Tel ˖ 0755-29162996 E-mail:support@aithinker.com Minimum system diagram Ai-Thinke