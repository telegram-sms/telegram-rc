# Telegram Remote Control

![Min Android Version](https://img.shields.io/badge/Min%20Android%20Version-8.0-orange.svg?style=flat-square)
[![License](https://img.shields.io/badge/License-BSD%203--Clause-blue.svg?style=flat-square)](https://github.com/telegram-sms/telegram-rc/blob/master/LICENSE)
[![GitHub Releases](https://img.shields.io/github/downloads/telegram-sms/telegram-rc/latest/app-release.apk?style=flat-square)](https://github.com/telegram-sms/telegram-rc/releases/latest)

A robot running on your Android device.

**The precompiled version of this version does not support non-64-bit instruction set devices.**

Please visit [https://reall.uk](https://reall.uk) to submit and discuss issues regarding this project.

You can follow the Telegram channel [Telegram SMS 更新日志](https://t.me/tg_sms_changelog) for the latest news. (Simplified Chinese only)

[Download](https://github.com/telegram-sms/telegram-rc/releases)

The program's wireless hotspot feature relies on [VPNHotspot](https://github.com/Mygod/VPNHotspot/) and requires dependent software to be installed for use.

## Features

This application allows you to remotely control your Android device via Telegram bot commands. Key features include:

- Send and receive SMS messages via Telegram
- Receive and view spam SMS messages
- Send USSD codes and receive responses
- Control mobile data, WiFi, and tethering
- Switch between SIM cards for dual-SIM devices
- Monitor beacon regions for automatic actions
- View device information such as battery level and network status
- Manage phone calls and call logs
- Control device settings via Shizuku integration
- Scan QR codes and barcodes
- Database management for contacts and organizations

## Requirements

- Android 8.0 or higher
- [Shizuku](https://github.com/RikkaApps/Shizuku) for system-level operations
- Telegram account with a configured bot

## Shizuku Integration

This application extensively uses Shizuku to access system-level APIs without requiring root access. Features that depend on Shizuku include:

- Telephony operations (sending SMS, querying call logs)
- Network management (data connection, mobile network settings)
- Battery and system information retrieval
- VPN and hotspot control

For Shizuku setup instructions, please refer to the [Shizuku documentation](https://shizuku.rikka.app/guide/setup/).

## Risk warning | 风险警告 | 風險警告 | リスク警告

**This is a sub-version of Telegram SMS that has more experimental features. No warranty expressed or implied. Use at your own risk. You have been warned.**

**这是Telegram SMS的子版本，具有更多实验功能。没有明示或暗示的担保，使用风险自负。**

**這是Telegram SMS的子版本，具有更多實驗功能。沒有明示或暗示的擔保，使用風險自負。**

**これは、より実験的な機能を備えたTelegram SMSのサブバージョンです。明示的または暗示的な保証はありません，ご自身の責任で使用してください。**

## License

CodeauxLib is licensed under [BSD 3-Clause License](https://github.com/telegram-sms/telegram-sms/blob/master/codeauxlib-license/LICENSE).

Copyright of the artwork belongs to [@walliant](https://www.pixiv.net/member.php?id=5600144).Licensed under [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/).

Download resource file: [mega.nz](https://mega.nz/#F!TmwQSYjD!XN-uVfciajwy3okjIdpCAQ)

artwork Use free fonts licensed by the whole society: [站酷庆科黄油体](https://www.zcool.com.cn/work/ZMTg5MDEyMDQ=.html)

## Acknowledgements

This APP uses the following open source libraries:

- [okhttp](https://github.com/square/okhttp)
- [Gson](https://github.com/google/gson)
- [CodeauxLib](https://gist.github.com/SumiMakito/59992fd15a865c3b9709b8e2c3bc9cf1)
- [MMKV](https://github.com/Tencent/MMKV)
- [code-scanner](https://github.com/yuriy-budiyev/code-scanner)
- [AwesomeQRCode](https://github.com/SumiMakito/AwesomeQRCode)

The creation of this APP would not be possible without help from the following people:

- [@SumiMakito](https://github.com/SumiMakito) ([Donate](https://paypal.me/makito))
- [@zsxsoft](https://github.com/zsxsoft)
- [@walliant](https://www.pixiv.net/member.php?id=5600144) ([weibo](https://www.weibo.com/p/1005053186671274))

I would also like to thank the following people for their hard work to localise this project:

- English
  - [@tangbao](https://github.com/tangbao)
  - [@jixunmoe](https://github.com/jixunmoe) ([Donate](https://paypal.me/jixun))

This APP uses the following public DNS service:

- [1.1.1.1](https://1.1.1.1/)

## Buy me a cup of coffee to help maintain this project further?

- [via Github](https://get.telegram-sms.com/donate/github)
- [via Bitcoin](bitcoin:17wmCCzy7hSSENnRBfUBMUSi7kdHYePrae) (**17wmCCzy7hSSENnRBfUBMUSi7kdHYePrae**)

Your donation will make me work better for this project.
