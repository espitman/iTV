# گراف کد iTV

Graphify از قبل روی سیستم نصب بود. استفاده در iTV از طریق دستورهای محلی `AGENTS.md` فعال شده و تنظیمات سراسری تغییر نکرده‌اند.

## ساخت و بازسازی

از ریشهٔ پروژه اجرا شود:

```sh
graphify extract . --code-only --no-cluster
graphify cluster-only . --no-label
```

این مسیر به تحلیل زبانی یا کلید API نیاز ندارد. خروجی‌ها در `graphify-out/` ساخته می‌شوند؛ `graph.html` نمایش تعاملی، `graph.json` دادهٔ گراف و `GRAPH_REPORT.md` گزارش ساختار است. استثناهای استخراج در `.graphifyignore` قرار دارند.

## پرس‌وجو

```sh
graphify query 'ImportCoordinator LibraryRepository TelewebionClient' --budget 800 --graph graphify-out/graph.json
graphify explain 'ImportCoordinator' --graph graphify-out/graph.json
graphify path 'ItvApplication' 'ImportCoordinator' --graph graphify-out/graph.json
```

گراف برای یافتن مسیر بررسی است؛ نتیجه‌های مهم باید با کد اصلی تطبیق داده شوند. پس از تغییر کد، گراف را بازسازی کنید. نبودن نماد یا یال، نبودن وابستگی را ثابت نمی‌کند. فایل‌های XML، Manifest، منابع، تنظیمات بیلد و روابط پویا پوشش کامل ندارند.

## بررسی اولیه — ۲۰۲۶-۰۹-۲۵

- استخراج کد: ۴۲ فایل؛ پس از خوشه‌بندی: ۵۹۹ گره، ۱۳۲۱ یال و ۲۳ گروه.
- فرمان‌های `query` و `explain` روی `ImportCoordinator` با موفقیت اجرا شدند.
- نتیجه با `app/src/main/java/app/itv/prototype/ItvApplication.kt` و `app/src/main/java/app/itv/prototype/data/ImportCoordinator.kt` تطبیق داده شد: `ItvApplication.onCreate`، هماهنگ‌کنندهٔ ورود را با `repository` و `telewebion` می‌سازد و آن را به `LanDashboard` می‌دهد؛ سازندهٔ `ImportCoordinator` نیز `LibraryRepository` و `TelewebionClient` را دریافت می‌کند.
- استخراج ۳۴ فایل غیرکد را کنار گذاشت؛ ۵۰ فایل بدون طبقه‌بندی، از جمله XML و فونت‌ها، نیز تحلیل نشدند. آمار گراف به معنای پوشش کامل پروژه نیست.

هیچ watcher یا ادغام با گراف سراسری برای این پروژه فعال نشده است.
