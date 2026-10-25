# iTV برای LG webOS

این پوشه نسخهٔ مستقل webOS را نگه می‌دارد. مرجع رفتار و ظاهر، کد فعلی Android TV در همین مخزن است. مقصد اصلی webOS TV 26 است؛ پخش سخت‌افزاری و رفتار سرویس در تلویزیون واقعی هنوز تأیید نهایی نشده‌اند.

## ساختار

- `app/`: رابط فارسی RTL، فونت Vazirmatn، خانه، کتابخانه، جست‌وجو، جزئیات فیلم/سریال، تنظیمات و پلیر HTML5 با HLS بومی تلویزیون.
- `service/`: سرویس Luna با شناسهٔ `app.itv.webos.service`، دریافت تلوبیون، نگهداری کتابخانه و تصاویر و سرور پنل محلی.
- `test/`: آزمون اعتبارسنجی نشانی، جفت‌سازی، مبدأ درخواست، تصاویر، نوسازی، پیشرفت، ترتیب قسمت‌ها و قواعد پخش.

دادهٔ واقعی روی تلویزیون در `/media/internal/app.itv.webos` ذخیره می‌شود. سرویس برای تمام مدت بازبودن اپ یک subscription فعال دارد؛ با بسته‌شدن آخرین نشست، سرور بسته می‌شود. هیچ سرویس لپ‌تاپی برای نسخهٔ نصب‌شده لازم نیست. پنل و فایل‌های آپلودشده از خود تلویزیون ارائه می‌شوند.

## آزمون و بسته‌بندی

از ریشهٔ پروژه:

```sh
node --test webos/test/*.test.js
bash scripts/release.sh --webos
```

تنها مسیر انتشار، `scripts/release.sh --webos` است. نسخه‌های `webos/package.json`، `app/appinfo.json` و `service/package.json` باید برابر باشند. در هر انتشار، `versionCode` و نسخهٔ webOS باید افزایش یابند. خروجی قبلی بازنویسی نمی‌شود. این حالت به نسخه و امضای APK اندروید دست نمی‌زند.

خروجی: `release/webos/app.itv.webos_<version>_all.ipk` شامل هر دو اپ و سرویس.

## نصب روی LG

Developer Mode و Key Server باید فعال باشند و دستگاه توسعه به IP تلویزیون روی پورت 9922 دسترسی داشته باشد. پس از ثبت کلید با ابزار رسمی LG:

```sh
ares-install -d lg-c5 release/webos/app.itv.webos_<version>_all.ipk
ares-launch -d lg-c5 app.itv.webos
```

در تنظیمات اپ، نشانی پنل و کد چهاررقمی نمایش داده می‌شود. گوشی و تلویزیون باید به یک شبکه دسترسی داشته باشند.

## پیش‌نمایش توسعه روی مک

```sh
npm --prefix webos run dev
```

فرمان، نشانی پیش‌نمایش دارای توکن موقت و PIN را چاپ می‌کند. این سرور فقط روی loopback گوش می‌دهد. از همان نشانی چاپ‌شده استفاده کنید. دادهٔ توسعه در پوشهٔ موقت سیستم است و با دادهٔ تلویزیون ارتباط ندارد. شبیه‌ساز رسمی LG به افزودن جداگانهٔ پوشه‌های app و service نیاز دارد؛ ذخیرهٔ شبیه‌ساز روی مک در پوشهٔ موقت سیستم انجام می‌شود.

## وضعیت بررسی — ۲۰۲۶-۱۰-۲۳

هشت تست محلی پاس شده‌اند. ساختار بسته با `ares-package --check` بررسی شده است. جفت‌سازی و نوسازی از رابط واقعی پنل مرورگر بررسی شده‌اند. اتصال Luna و دسترسی HTTP با ماژول رسمی `webos-service` شبیه‌ساز LG روی مک آزموده شده‌اند. دریافت زندهٔ برنامهٔ دختران، ۲۳ قسمت موجود، مرتب‌سازی و دریافت نشانی پخش موفق بوده است.

تلویزیون مقصد با نشانی اصلاح‌شدهٔ `192.168.10.49` شناسایی شد: LG OLED65C56LA، SDK 10.3.0 و firmware 33.30.80. نسخهٔ ۰.۳۳ روی آن نصب و اجرا شد، سرور پنل داخل تلویزیون پاسخ داد و کاربر تصویر و صدای پخش را روی خود تلویزیون تأیید کرد. برابری بصری آن نسخه مورد تأیید کاربر نبود. کیفیت‌ها، سرعت پخش، Magic Remote، عمر سرویس و ماندگاری پس از خاموش/روشن شدن هنوز به آزمون کامل روی دستگاه نیاز دارند. پیش‌نمایش مرورگر یا موفقیت بسته‌بندی، اثبات یکسان‌بودن کامل با اندروید یا موفقیت پخش روی LG نیست.

## مراجع پلتفرم

- [موتور وب و نسخه‌های webOS](https://webostv.developer.lge.com/develop/specifications/web-api-and-web-engine)
- [سرویس JavaScript](https://webostv.developer.lge.com/develop/guides/js-service-basics)
- [چرخهٔ سرویس و subscription](https://webostv.developer.lge.com/develop/guides/js-service-faq)

فونت همراه تحت OFL است؛ متن مجوز در `app/fonts/OFL.txt` موجود است. QR encoder از فایل موجود همین پروژه منتقل شده است.

## اصلاح طراحی در ۰.۳۴

ابعاد CSS در `app/design-tokens.css` و آیکن‌های `app/icons.js` با `tools/sync-design.py` از `app/src/main/res/values/dimens.xml` و vectorهای اندروید تولید می‌شوند. مبنا ۱۹۲۰×۱۰۸۰ با تبدیل هر dp به دو پیکسل است.

| بخش | مرجع اندروید | انتقال به webOS |
|---|---|---|
| خانه | `activity_main.xml`، `view_home_header.xml` | هدر ۹۶، متن هدر در y=۲۳۶، شروع کتابخانه در y=۴۹۲، پوستر ۱۷۶×۲۶۴ |
| جزئیات | `activity_series.xml` | حذف منوی خانه، تصویر ۱۱۶۰×۵۴۰ در چپ، متن ۸۰۰ پیکسلی در راست از y=۵۶، قسمت‌ها با فاصلهٔ ۸۸ از پایین |
| پلیر | `activity_player.xml` | کنترل‌های آیکنی ۷۲×۷۲، پنل نوار زمان، توقف در چپ، قسمت بعد در راست، منوی سرعت پایین راست |
| تنظیمات | `activity_settings.xml` | پنل ۱۲۰۰ پیکسلی، QR، چهار خانهٔ PIN، رنگ‌ها و فاصله‌های منبع |

گرادیان‌های چندایستگاهی از `HeroBottomFadeView.kt` و `HeroRightFadeView` منتقل شده‌اند. تولید منابع به مسیر انتشار اضافه شده است. شبیه‌ساز Android به علت فضای ناکافی دیسک اجرا نشد؛ مقایسهٔ تصویری مستقیم با خروجی زندهٔ Android هنوز انجام نشده و تطابق پیکسل‌به‌پیکسل ادعا نمی‌شود.

نسخهٔ ۰.۳۴ روی همان LG نصب و اجرا شد. سرویس دستگاه نسخهٔ ۰.۳۴ را گزارش داد و تصویر صفحهٔ جزئیات در بازرس زنده بررسی شد: منوی خانه حذف شده، بلوک متن به بالای راست منتقل شده و ردیف قسمت‌ها در پایین قرار دارد. کاربر بازبودن نسخهٔ جدید روی تلویزیون را تأیید کرد. تأیید نهایی برابری بصری همهٔ صفحه‌ها هنوز ثبت نشده است.

## اصلاح رفتار بک در ۰.۳۵

مرجع رفتار `PlayerActivity.handleBack` در اندروید است: بک روی پنجرهٔ ادامه، ادامهٔ پخش را انتخاب می‌کند؛ منوی باز ابتدا بسته می‌شود؛ فوکوس روی کارت بعد، پخش خودکار را لغو می‌کند؛ کنترل‌های نمایان ابتدا مخفی می‌شوند؛ بک با کنترل‌های مخفی از پلیر خارج می‌شود. رویداد 461 در keydown و keyup مصرف می‌شود و تکرار ناشی از نگه‌داشتن کلید، ناوبری دوم ایجاد نمی‌کند.

خروج از پلیر دیگر منتظر شبکه نمی‌ماند: ویدیو فوراً آزاد و صفحهٔ قبل نمایش داده می‌شود؛ ذخیرهٔ پیشرفت با مهلت چهار ثانیه‌ای در پس‌زمینه تکمیل می‌شود. مخفی‌شدن خودکار کنترل‌ها در حالت توقف پخش نیز انجام می‌شود. مجموع ۱۴ تست، شامل شش تست رویداد واقعی handler بک، پاس شده‌اند. بستهٔ ۰.۳۵ ساخته شده است؛ اولین تلاش نصب به علت timeout اتصال Developer Mode انجام نشد.

پس از برقراری مجدد اتصال، نسخهٔ ۰.۳۵ با موفقیت روی LG نصب و اجرا شد؛ پاسخ `/api/status` روی دستگاه، نسخهٔ `0.35.0` و `running: true` را تأیید کرد. آزمون ریموت فیزیکی پس از نصب هنوز تأیید کاربر را ندارد.

## اصلاح پلیر در ۰.۳۶ — ۲۰۲۶-۱۰-۲۴

نمایش دائمی رد تیتراژ از تداخل CSS بود: `display: inline-flex` دکمه بر استایل پیش‌فرض ویژگی `hidden` غلبه می‌کرد. قانون `[hidden] { display: none !important }` اضافه شد و وجود آن در stylesheet واقعی مرورگر بررسی شد. تیتراژ پایانی نیز فقط با مدت معتبر و در بازهٔ `[last_titraje_start, duration)` فعال است؛ هنگام توقف، نمایش کنترل‌ها، پنجرهٔ باز، بارگذاری و قفل قسمت بعد مخفی می‌ماند.

چپ/راست با کنترل مخفی مانند `quickSeek` اندروید فوراً ۱۰ ثانیه جابه‌جا می‌کند و کنترل‌ها را باز نمی‌کند. گام نگه‌داشتن پس از دو ثانیه ۲۰ ثانیه است. بازخورد مجموع جابه‌جایی در سمت مربوط نمایش داده و پس از ۱٫۵ ثانیه حذف می‌شود. با کنترل نمایان، چپ/راست روی دکمه‌ها فقط فوکوس را تغییر می‌دهد؛ روی نوار زمان، گام دقیق ۱۰ ثانیه جای رفتار درصدی مرورگر را می‌گیرد. فوکوس و قفل کارت بعد نیز قبل از جابه‌جایی زمان رسیدگی می‌شوند.

پیش از شروع هر قسمت، manifest خوانده می‌شود و variant دارای بیشترین ارتفاع تصویر و سپس bitrate انتخاب می‌شود. پخش مستقیماً با نشانی همان کیفیت آغاز می‌شود. منوی کیفیت حالت «بالاترین کیفیت (خودکار)» را نشان می‌دهد؛ انتخاب کیفیت پایین‌تر حذف شده است تا درخواست همیشگی کاربر رعایت شود. اگر manifest اصلی کیفیت معتبر ندهد، خطا نشان داده می‌شود و به حالت تطبیقی با کیفیت نامعلوم سقوط نمی‌کند.

۲۱ تست پاس شده و بستهٔ `app.itv.webos_0.36.0_all.ipk` از مسیر رسمی انتشار ساخته شده است. بررسی زندهٔ منبع و اتصال Developer Mode در این نوبت timeout شدند؛ نصب و آزمون روی LG برای ۰.۳۶ هنوز تأیید نشده است.

با آدرس جدید `192.168.10.100`، نسخهٔ ۰.۳۶ روی LG نصب و اجرا شد. پاسخ `/api/status` دستگاه، `version: 0.36.0` و `running: true` را تأیید کرد. رفتار ریموت و پخش این نسخه هنوز تأیید نهایی کاربر را ندارد.

### 0.37.0 — remote access during slow playback startup
Buffering now owns a focused Back button above the transport; LG directional keys and Enter navigate its actions. Back exits buffering on the first press without waiting for network/progress requests. A 30-second startup/buffering watchdog exposes Retry and Back, and late stream responses cannot restart a failed player. Regression tests cover a permanently pending network request and LG key-code navigation between error actions. Native slow-network playback still needs confirmation on the TV.

### 0.38.0 — native seek recovery
Buffering recovery now handles canplay, seeked and actual time advancement as well as playing. Seek completion requests playback when playback was intended; paused playback stays paused. Repeated waiting events do not reset the watchdog, and its timeout no longer pauses the decoder or rejects later native playback recovery. LG key code 39 also selects forward seeking when key is Unidentified. Added regression checks for key-code direction, recovery and in-flight seek gating. TV package installation does not by itself verify slow-network native seeking.

### 0.39.0 — continuous ten-second seeking
Per the user's Android behavior clarification, held direction keys always add ten seconds per repeat, including while a prior seek is buffering. Feedback stays above the buffering overlay. Added a regression test for four consecutive repeats while loading. Includes the 0.38 native seek recovery changes. All 27 automated tests pass; real LG slow-network seek recovery requires device observation.

### Simulator font override
The LG 25 simulator injects `* { font-family: "LG Display-Regular"; font-weight: 400 !important; }`. Body-only font inheritance therefore did not reach descendants. The app now declares Vazirmatn on descendants and preserves its explicit regular/bold weights against that injection. Verified visually in the simulator after CSS reload; packaged TTF files match the Android resources byte for byte. This CSS change has not yet been released to the physical TV.

### Unreleased simulator playback and home corrections
Added bundled hls.js 1.6.13 (Apache-2.0 license included) for environments without native HLS. Native TV playback retains its native route. Buffering shows only a circular spinner; hardware Back remains active. Continue Watching now follows Android HomeCatalog's series filtering/order and selects only one episode for each eligible series. Simulator service uses an assigned port to avoid collisions between development sessions. Duplicate simulator app entries were removed through its UI; keep one canonical app path and do not register aliases again.


### 0.41.0 — Personal library and WebDAV
In the paired management panel, choose «افزودن عنوان شخصی / WebDAV». Define a film (one file) or a series with season/episode numbers and direct HTTP(S) file links. Poster and backdrop support HTTPS URLs or local image uploads. Add, remove, renumber and edit episodes; stable IDs preserve watch progress when editing. Titles appear in the existing home, film/series, detail and search views. Existing resume, seek, speed and watched controls apply.

Optional Basic authentication is stored on the TV and omitted from library API responses and playback URLs. Blank credentials during edit preserve previous values; the explicit clear option removes them. Each file has a connection/Range test. Playback streams through an authenticated TV-local endpoint using byte ranges and backpressure; it never copies the whole video to TV storage. Remote servers must permit direct GET access; cross-origin redirects do not receive credentials. HTTP is supported for local phone servers; HTTPS certificates must be trusted. Digest-only authentication, WebDAV directory browsing, HLS playlists and transcoding are not included. Container/codec support remains that of the TV. Phone/server must remain reachable during playback.

Validation: automated create/edit/delete/restart, per-season numbering, invalid URL/duplicate detection, credential redaction/auth errors, missing file, redirect rejection, Range streaming and progress preservation. Paired panel creation and connection test plus actual MP4 playback/seek verified in the browser using a local generated video fixture. The user's phone/server and TV playback of that phone's actual files still require confirmation.

نسخهٔ ۰.۴۴: در پنل، بخش «ورود اطلاعات از فیلیمو» لینک صفحهٔ فیلم یا یکی از قسمت‌های سریال را می‌گیرد. اطلاعات عمومی، پوستر، هدر، عوامل و همهٔ فصل‌ها و قسمت‌های منتشرشده وارد می‌شوند. پخش همچنان فقط از فایل شخصی WebDAV است. لینک قسمت‌ها می‌تواند خالی بماند و بعداً در ویرایش وارد شود. ورود دوبارهٔ همان سریال عنوان تکراری نمی‌سازد. نوسازی، قسمت‌های جدید را اضافه می‌کند و شناسهٔ قسمت، لینک فایل، وضعیت تماشا، اطلاعات ورود و تصاویر سفارشی را حفظ می‌کند. کتابخانه هر شش ساعت هنگام دسترسی، نوسازی پس‌زمینه را آغاز می‌کند؛ دکمهٔ نوسازی بررسی فوری انجام می‌دهد. عنوان‌های قبلی در خطای شبکه حذف نمی‌شوند. تغییر API عمومی فیلیمو ممکن است به اصلاح استخراج‌کننده نیاز داشته باشد.

### Filmnet metadata import
The paired panel now accepts Filmnet content links in «ورود اطلاعات از فیلم‌نت». The importer reads public page metadata, imports seasons and active episodes with their own images, cover/poster, summary, genres, year and cast, and leaves playback file URLs for the user to supply through WebDAV. Refresh and the existing six-hour library check use the original provider. Stable episode IDs retain local file links, credentials and watch state; importing the same title again updates its existing library entry. Filmnet parsing failures keep the existing library intact.

Validated with the public Yal content page (one season, three episodes), importer and API tests covering multiple seasons, malformed metadata, numbering, movies, duplicate imports and preservation of local playback data. This uses public metadata only and does not obtain playback files from Filmnet.

The panel now uses one shared link field for Telewebion, Filimo and Filmnet. Both preview and add detect the provider from a validated exact hostname. Imported Filimo/Filmnet titles open the WebDAV file editor; Telewebion retains its existing catalog/playback import. Unsupported or malformed links are rejected before fetching remote data.

Sheyda program links (`https://www.sheyda.com/p/<uid>`) are also auto-detected. Public metadata queries create their own anonymous device session through the site's normal getDid endpoint; no browser login or subscription credentials are used. Every season is fetched before updating the library. Episode numbers come from the episode title rather than playlist order, and released specials retain their own labels (internal display number zero) without shifting regular episode numbers. Existing file links and progress remain tied to stable source episode IDs. Verified against Bamdad Khomar: two seasons, 28 released entries including one special.
