# Bridgecast

تطبيق أندرويد (Kotlin + Compose) لبث الفيديو إلى أجهزة **DLNA** و**Chromecast**.

## الميزات الحالية
- اكتشاف أجهزة DLNA عبر SSDP والتحكم بها (SOAP / AVTransport)
- Chromecast عبر Google Cast SDK (Default Media Receiver)
- خادم محلي لبث الملفات مع Range وCORS وترويسات DLNA
- روابط مباشرة: mp4 / HLS / DASH

## البناء
افتح المجلد في Android Studio، أو ارفعه إلى GitHub وسيبني **GitHub Actions** ملف APK تلقائياً (Actions ← Build APK ← Artifacts).

## خارطة الطريق
- [ ] Foreground Service لإبقاء الخادم يعمل
- [ ] متصفح مدمج يلتقط روابط الفيديو (WebView)
- [ ] Proxy يمرر Referer/Cookies
- [ ] تحويل الصيغ (FFmpegKit) وترجمات SRT→WebVTT
- [ ] Roku / Samsung / LG / AirPlay
- [ ] عميل تصفح DLNA (ContentDirectory) ونظام إضافات JS (QuickJS)

> المحتوى المحمي بـ DRM (مثل Netflix) غير مدعوم.
