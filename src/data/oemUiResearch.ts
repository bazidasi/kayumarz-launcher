import { LauncherPreferences } from '../types/launcher';

export type OemPhilosophy = 'minimal' | 'modular' | 'ecosystem';
export type UiLayerKey =
  | 'all'
  | 'lock_aod'
  | 'home_folders'
  | 'drawer'
  | 'control_capsule'
  | 'theme_icons'
  | 'gestures_reach';

export type KayumarsAdoptionStatus = 'adopted' | 'adapted' | 'omittedForBudget';

export interface OemLayerSpec {
  lockAod: string;
  homeFolders: string;
  drawer: string;
  controlCapsule: string;
  themeIcons: string;
  gesturesReach: string;
}

export interface OemUiProfile {
  id: string;
  brand: string;
  uiName: string;
  baseOs: string;
  philosophy: OemPhilosophy;
  philosophyLabel: string;
  tagline: string;
  summary: string;
  architecturalStrength: string;
  kayumarsStatus: KayumarsAdoptionStatus;
  kayumarsStatusLabel: string;
  kayumarsTakeaway: string;
  omittedReason?: string;
  layers: OemLayerSpec;
  signatureFeatures: string[];
  simulatorPreset: {
    label: string;
    prefs: Partial<LauncherPreferences>;
    openEdge?: boolean;
    openFolder?: boolean;
  };
}

export const UI_LAYER_TABS: Array<{ key: UiLayerKey; label: string }> = [
  { key: 'all', label: 'همه لایه‌ها' },
  { key: 'lock_aod', label: 'صفحه قفل و AOD' },
  { key: 'home_folders', label: 'خانه، ویجت و پوشه' },
  { key: 'drawer', label: 'کشوی برنامه‌ها' },
  { key: 'control_capsule', label: 'کنترل‌سنتر و کپسول' },
  { key: 'theme_icons', label: 'موتور رنگ و آیکون' },
  { key: 'gestures_reach', label: 'ژست و یک‌دستی' },
];

export const OEM_UI_PROFILES: OemUiProfile[] = [
  {
    id: 'one-ui-7',
    brand: 'Samsung',
    uiName: 'One UI 7 + Good Lock',
    baseOs: 'Android 15',
    philosophy: 'modular',
    philosophyLabel: 'ماژولار و آزاد',
    tagline: 'ارگونومی ثلث پایینی صفحه + آزادی مطلق با ماژول‌های Good Lock',
    summary:
      'ساختار دو‌ناحیه‌ای (ناحیه دید در بالا، ناحیه تعامل زیر شست در پایین) به‌همراه Home Up برای چیدمان آزاد بدون شبکه (DIY Home) و نوار زنده Now Bar روی لاک‌اسکرین.',
    architecturalStrength: 'تفکیک کامل کنترل‌سنتر، نوار کناری Edge Panel و شخصی‌سازی عمیق ماژولار بدون تحمیل شلوغی به کاربر عادی.',
    kayumarsStatus: 'adopted',
    kayumarsStatusLabel: 'پیاده‌سازی‌شده در کیومرث',
    kayumarsTakeaway: 'پنل لبه شست (Edge Quick Strip)، نوار جستجوی پایین صفحه و پروفایل ژست یک‌دستی در کیومرث پیاده‌سازی شد.',
    layers: {
      lockAod: 'LockStar و Wonderland: جابه‌جایی آزاد ساعت و ویجت‌ها، انیمیشن پیوسته از AOD به لاک‌اسکرین و نوار زنده Now Bar در پایین.',
      homeFolders: 'Home Up DIY Home: قراردادن آزاد آیکون و استیکر خارج از گرید با قابلیت چرخش، مخفی‌سازی کامل داک و شیشه‌مات Theme Park.',
      drawer: 'صفحه افقی یا اسکرول عمودی الفبایی با نوار جستجوی پایین صفحه برای دسترسی فوری با یک دست.',
      controlCapsule: 'پنل سریع بازطراحی‌شده با اسلایدرهای قرصی شکل، حالت تفکیک یا یکپارچه و Now Bar برای پخش‌کننده و تایمر.',
      themeIcons: 'پالت رنگی والپیپر + Theme Park برای ساخت آیکون‌پک اختصاصی و کنترل شفافیت و بلور ویجت‌ها.',
      gesturesReach: 'طراحی ثلث پایینی، Edge Panel کناری و One Hand Operation+ با ژست‌های لبه‌ای قابل تنظیم.',
    },
    signatureFeatures: ['DIY Home Free Grid', 'Now Bar', 'Edge Panel', 'Theme Park Blur'],
    simulatorPreset: {
      label: 'تست سبک One UI + Edge Panel در کیومرث',
      prefs: {
        themeMode: 'dark',
        clockStyle: 'editorial',
        iconTintStyle: 'frosted_glass',
        iconShape: 'squircle',
        edgePanelEnabled: true,
        liveCapsuleEnabled: true,
        drawerBlur: true,
      },
      openEdge: true,
    },
  },
  {
    id: 'hyperos-2',
    brand: 'Xiaomi',
    uiName: 'HyperOS 2',
    baseOs: 'Android 15',
    philosophy: 'ecosystem',
    philosophyLabel: 'پرآپشن و اکوسیستم',
    tagline: 'لاک‌اسکرین مجله‌ای با عمق تصویر + کپسول زنده Super Island',
    summary:
      'تمرکز بر انیمیشن‌های فیزیکی قابل قطع، قرار گرفتن ساعت پشت سوژه عکس (Depth Effect)، پوشه‌های بزرگ روی خانه و کنترل‌سنتر دوتکه با بلور تک‌گذر.',
    architecturalStrength: 'کپسول چندفعالیتی Super Island و قالب‌های ساعت مجله‌ای بدون نیاز به لانچر جانبی.',
    kayumarsStatus: 'adopted',
    kayumarsStatusLabel: 'پیاده‌سازی‌شده در کیومرث',
    kayumarsTakeaway: 'قالب ساعت مجله‌ای دوتکه (Magazine Clock)، پوشه بزرگ ۴تایی روی خانه و کپسول آفلاین به کیومرث اضافه شد.',
    layers: {
      lockAod: 'سه سبک Classic، Rhombus و Magazine با Depth Effect (رفتن اعداد ساعت پشت سوژه عکس) و والپیپر پویا.',
      homeFolders: 'پوشه‌های بزرگ ۳×۳ با اجرای مستقیم آیکون‌ها، پشته ویجت (Widget Stack) و تغییر اندازه آزاد شبکه.',
      drawer: 'حالت کلاسیک بدون کشو یا کشوی دسته‌بندی‌شده با جستجوی پایین و فیلتر رنگی آیکون‌ها.',
      controlCapsule: 'کنترل‌سنتر دوتکه (چپ اعلان، راست کنترل شیشه‌ای) + Super Island با پشتیبانی هم‌زمان از دو فعالیت زنده.',
      themeIcons: 'فروشگاه تم جامع + فیلتر رنگ آیکون و شفافیت پویا روی ویجت‌ها و کنترل‌سنتر.',
      gesturesReach: 'انیمیشن‌های غیرخطی قطع‌پذیر و کشیدن از گوشه بالا برای کنترل‌سنتر و میانبرهای سریع.',
    },
    signatureFeatures: ['Magazine Depth Clock', 'Super Island', 'Large Folders 3×3', 'Split Control Center'],
    simulatorPreset: {
      label: 'تست ساعت مجله‌ای و پوشه بزرگ HyperOS',
      prefs: {
        themeMode: 'system',
        clockStyle: 'magazine',
        iconTintStyle: 'full_color',
        iconShape: 'rounded-square',
        liveCapsuleEnabled: true,
        drawerBlur: true,
      },
      openFolder: true,
    },
  },
  {
    id: 'ios-18',
    brand: 'Apple',
    uiName: 'iOS 18',
    baseOs: 'iOS 18',
    philosophy: 'ecosystem',
    philosophyLabel: 'پرآپشن و اکوسیستم',
    tagline: 'موتور تینت سراسری آیکون‌ها + کنترل‌سنتر چندصفحه‌ای + Dynamic Island',
    summary:
      'برای نخستین بار امکان چیدمان آزاد در شبکه خانه، تیره کردن و رنگ‌آمیزی یکپارچه تمامی آیکون‌ها متناسب با والپیپر (Dark / Tinted) و کنترل‌سنتر ماژولار با API توسعه‌دهندگان را معرفی کرد.',
    architecturalStrength: 'یکپارچگی الگوریتمی موتور رنگ آیکون‌ها و هماهنگی کامل فریم به فریم بین ویجت، داینامیک آیلند و قفل صفحه.',
    kayumarsStatus: 'adapted',
    kayumarsStatusLabel: 'تطبیق سبک‌وزن',
    kayumarsTakeaway: 'موتور تینت رنگ یکپارچه آیکون‌ها (Monet / iOS Tint) با ColorMatrix صفر-سربار به کیومرث منتقل شد.',
    omittedReason: 'API اکستنشن‌های زنده پس‌زمینه برای حفظ صفر مصرف رم/باتری و صفر سرویس پس‌زمینه حذف شد.',
    layers: {
      lockAod: 'ساعت‌های با فونت و عمق قابل تنظیم، دو دکمه پایین صفحه قابل جایگزینی و AOD کم‌نور با نمایش والپیپر.',
      homeFolders: 'قرارگیری آزاد آیکون در شبکه، تغییر اندازه بزرگ/کوچک و پشته هوشمند ویجت‌ها (Smart Stack).',
      drawer: 'App Library در انتهای صفحات با دسته‌بندی خودکار ۴تایی و فهرست الفبایی در جستجو.',
      controlCapsule: 'کنترل‌سنتر چندصفحه‌ای با تغییر سایز کنترل‌ها (۱×۱ تا ۴×۲) و Dynamic Island تعاملی بالای صفحه.',
      themeIcons: 'تغییر اندازه بدون لیبل (Large Icons) + حالت Dark خودکار و موتور Tinted برای یکدست‌سازی رنگ آیکون‌ها.',
      gesturesReach: 'سوایپ پایین از میانه برای Spotlight، سوایپ چپ/راست داینامیک آیلند و Reachability پایین‌آورنده صفحه.',
    },
    signatureFeatures: ['Global Tinted Icons', 'Dynamic Island', 'Multi-Page Control Center', 'Label-Free Large Icons'],
    simulatorPreset: {
      label: 'تست تینت سراسری iOS 18 روی کیومرث',
      prefs: {
        themeMode: 'dark',
        clockStyle: 'editorial',
        iconTintStyle: 'monet_tint',
        iconShape: 'rounded-square',
        liveCapsuleEnabled: true,
      },
    },
  },
  {
    id: 'nothing-os-3',
    brand: 'Nothing',
    uiName: 'Nothing OS 3.0',
    baseOs: 'Android 15',
    philosophy: 'minimal',
    philosophyLabel: 'مینیمال و استاک',
    tagline: 'مینیمالیسم صنعتی دات‌ماتریکس + کشوی هوشمند خودکار + حذف شلوغی رنگی',
    summary:
      'پاک‌سازی کامل آشفتگی رنگی آیکون‌ها با پک تک‌رنگ سفید/مشکی، ویجت‌های دات‌ماتریکس و نقطه‌های قرمز تاکید، و دسته‌بندی هوشمند کشوی برنامه‌ها با سنجاق کردن اپ‌های مهم.',
    architecturalStrength: 'کاهش بار شناختی (Cognitive Load) با کنتراست بالا و ویجت‌های کاربردی ۱×۱ و ۲×۲.',
    kayumarsStatus: 'adopted',
    kayumarsStatusLabel: 'پیاده‌سازی‌شده در کیومرث',
    kayumarsTakeaway: 'ساعت دات‌ماتریکس صنعتی و فیلتر آیکون تک‌رنگ Nothing Mono دقیقاً در کیومرث شبیه‌سازی و بومی‌سازی شد.',
    layers: {
      lockAod: 'گالری ویجت‌های ۱×۱ و ۲×۱ زیر ساعت دات‌ماتریکس و انیمیشن باز شدن قفل بدون شلوغی.',
      homeFolders: 'پوشه‌های دایره‌ای یا شبکه‌ای، آیکون‌های دایره‌ای تک‌رنگ و Shared Widgets تعاملی بین مخاطبان.',
      drawer: 'AI Smart Drawer دسته‌بندی خودکار برنامه‌ها + سنجاق اپ‌های پرتکرار در ردیف بالای کشو.',
      controlCapsule: 'کاشی‌های Quick Settings با اندازه متغیر (۱×۱، ۲×۱، ۲×۲) و پنجره‌های شناور با تغییر سایز از گوشه‌ها.',
      themeIcons: 'Nothing Monochrome Icon Pack و تم دوگانه تیره/سفید با نقطه قرمز به عنوان تنها رنگ تاکید.',
      gesturesReach: 'ژست‌های مستقیم لانچر، دبل‌تپ برای قفل و منوی میانبر سریع روی ویجت‌ها.',
    },
    signatureFeatures: ['Dot-Matrix Typography', 'Monochrome Icon Pack', 'AI Smart Drawer', 'Resizable QS Tiles'],
    simulatorPreset: {
      label: 'تست ظاهر دات‌ماتریکس Nothing OS در کیومرث',
      prefs: {
        themeMode: 'amoled',
        clockStyle: 'dot_matrix',
        iconTintStyle: 'monochrome_dot',
        iconShape: 'circle',
        liveCapsuleEnabled: false,
      },
    },
  },
  {
    id: 'pixel-ui',
    brand: 'Google',
    uiName: 'Pixel UI (Material You)',
    baseOs: 'Android 15',
    philosophy: 'minimal',
    philosophyLabel: 'مینیمال و استاک',
    tagline: 'استخراج پالت Monet از والپیپر + کشوی الفبایی فوری + Private Space',
    summary:
      'الگوی خالص اندروید با فوکوس بر پیش‌بینی اپ‌های بعدی، نوار جستجوی یکپارچه در پایین صفحه، کارت زمینه‌ای At a Glance و فضای خصوصی ایزوله‌شده در انتهای کشو.',
    architecturalStrength: 'هماهنگی خودکار رنگ تمام ویجت‌ها و آیکون‌های سیستمی از یک تصویر پس‌زمینه بدون نیاز به تنظیم دستی کاربر.',
    kayumarsStatus: 'adopted',
    kayumarsStatusLabel: 'هسته بومی کیومرث',
    kayumarsTakeaway: 'نوار جستجوی یکپارچه در پایین صفحه خانه، پنجره برگشت پیش‌بینی‌شونده (Predictive Back) و رنگ Monet.',
    omittedReason: 'آیکون‌های شرطی وابسته به پیکسل لانچر و فضای Private Space نیازمند مجوز پروفایل کاربر در اندروید ۱۵ است.',
    layers: {
      lockAod: '۹ قالب ساعت متریال یو با کنتراست تطبیقی، میانبرهای قفل‌صفحه و خلاصه At a Glance.',
      homeFolders: 'شبکه منظم ۴×۵ یا ۵×۵ بدون امکان مخفی‌سازی نوار جستجو؛ تاکید بر سادگی و پایداری ۶۰/۱۲۰ هرتز.',
      drawer: 'ردیف ۵ اپ پیشنهادی هوش مصنوعی در بالا، اسکرول الفبایی عمودی و قفل Private Space در پایین.',
      controlCapsule: 'تنظیمات سریع قرصی دو-ستونه با نمایشگر مصرف و رنگ‌دهی کامل Monet.',
      themeIcons: 'Themed Icons بتا هماهنگ با ۴ پالت رنگی استخراج‌شده از والپیپر (Material You / Monet).',
      gesturesReach: 'Predictive Back با کوچک شدن تدریجی صفحه جاری و نمایش صفحه مقصد قبل از رها کردن انگشت.',
    },
    signatureFeatures: ['Monet Color Engine', 'Predictive Back', 'Bottom Universal Search', 'At a Glance'],
    simulatorPreset: {
      label: 'تست تم Monet و جستجوی پایین Pixel UI',
      prefs: {
        themeMode: 'dark',
        clockStyle: 'editorial',
        iconTintStyle: 'monet_tint',
        iconShape: 'circle',
      },
    },
  },
  {
    id: 'coloros-oxygenos-15',
    brand: 'OPPO / OnePlus',
    uiName: 'ColorOS 15 / OxygenOS 15',
    baseOs: 'Android 15',
    philosophy: 'modular',
    philosophyLabel: 'ماژولار و آزاد',
    tagline: 'انیمیشن‌های موازی Luminous + پوشه بزرگ تعاملی + Fluid Cloud',
    summary:
      'یکی از روان‌ترین موتورهای رندر اندروید با قابلیت قطع و آغاز هم‌زمان چند انیمیشن، تغییر شکل پوشه ۱×۱ به ۲×۲ روی خانه، و کپسول اعلانات Fluid Cloud.',
    architecturalStrength: 'موتور انیمیشن موازی که حتی هنگام کلیک پشت‌سرهم روی ۵ اپ مختلف، هیچ لگ یا توقف فریم ایجاد نمی‌کند.',
    kayumarsStatus: 'adopted',
    kayumarsStatusLabel: 'پیاده‌سازی‌شده در کیومرث',
    kayumarsTakeaway: 'پوشه بزرگ تعاملی صفحه خانه با اجرای مستقیم آیکون و انیمیشن‌های فنری قطع‌پذیر (`SpringPreset`) در کیومرث پیاده‌سازی شد.',
    layers: {
      lockAod: 'تم‌های Flux با زوم پیوسته ۳مرحله‌ای از AOD به لاک‌اسکرین و از لاک‌اسکرین به پس‌زمینه صفحه خانه.',
      homeFolders: 'پوشه‌های بزرگ قابل کشیدن بین ۱×۲ تا ۲×۲ با اجرای تک‌ضربه‌ای اپ‌های داخل + پنل کناری Shelf.',
      drawer: 'پشتیبانی کامل از Icon Packهای شخص ثالث به‌صورت بومی، کشوی دسته‌بندی‌شده و اسکرول الفبایی.',
      controlCapsule: 'انتخاب بین پنل دوتکه و کلاسیک با بلور عمیق + Fluid Cloud برای وضعیت پخش، ضبط و ناوبری.',
      themeIcons: 'سفارشی‌سازی شکل، اندازه، برچسب و فاصله آیکون‌ها به‌همراه Flux Themes و رنگ والپیپر.',
      gesturesReach: 'Icon Pull-Down Gesture: کشیدن به پایین کنار صفحه باعث جمع شدن آیکون‌ها زیر انگشت شست می‌شود.',
    },
    signatureFeatures: ['Parallel Animations', 'Expandable Large Folders', 'Fluid Cloud', 'Native Icon Pack Support'],
    simulatorPreset: {
      label: 'تست پوشه بزرگ تعاملی ColorOS در کیومرث',
      prefs: {
        themeMode: 'system',
        clockStyle: 'editorial',
        iconTintStyle: 'full_color',
        iconShape: 'squircle',
        liveCapsuleEnabled: true,
        drawerBlur: true,
      },
      openFolder: true,
    },
  },
  {
    id: 'originos-5',
    brand: 'vivo',
    uiName: 'OriginOS 5',
    baseOs: 'Android 15',
    philosophy: 'modular',
    philosophyLabel: 'ماژولار و آزاد',
    tagline: 'کامپوننت‌های اتمی (Atomic Components) + فیزیک شیشه مات',
    summary:
      'تبدیل مرز بین آیکون و ویجت: هر آیکون می‌تواند بدون باز کردن برنامه به یک کارت سرویس تعاملی کوچک تبدیل شود و کنترل‌سنتر با شیشه مات عمیق واکنش نشان دهد.',
    architecturalStrength: 'تعامل مستقیم روی صفحه اصلی (Micro-widgets) بدون ورود مکرر به اپلیکیشن‌ها.',
    kayumarsStatus: 'adapted',
    kayumarsStatusLabel: 'الهام در کارت موزیک و ساعت',
    kayumarsTakeaway: 'ساختار کامپوننت اتمی در ویجت موزیک و کارت وضعیت آفلاین صفحه خانه کیومرث اعمال شده است.',
    layers: {
      lockAod: 'والپیپرهای تعاملی زنده و تغییر فونت و ضخامت ساعت لاک‌اسکرین با پیش‌نمایش شیشه‌ای.',
      homeFolders: 'Atomic Components (تغییر سایز لحظه‌ای کارت)، پوشه‌های تعاملی و گرید متراکم بدون حاشیه.',
      drawer: 'صفحه کشو با پین کردن کارت‌های پرکاربرد و جستجوی یکپارچه در کل سیستم و تنظیمات.',
      controlCapsule: 'شیشه مات لایه‌ای و کپسول وضعیت اعلانات زنده در اطراف حفره دوربین.',
      themeIcons: 'تغییر گردی گوشه آیکون‌ها، پالت رنگ سیستمی و حالت تک‌رنگ نرم.',
      gesturesReach: 'سوایپ‌های گوشه‌ای و پنجره‌های شناور کوچک با جابه‌جایی روان بین دو اپ.',
    },
    signatureFeatures: ['Atomic Components', 'Frosted Physics Blur', 'Origin Design Grid', 'Mini Windows'],
    simulatorPreset: {
      label: 'تست آیکون شیشه‌ای مات OriginOS در کیومرث',
      prefs: {
        themeMode: 'dark',
        clockStyle: 'magazine',
        iconTintStyle: 'frosted_glass',
        iconShape: 'squircle',
        drawerBlur: true,
      },
    },
  },
  {
    id: 'harmonyos-magicos',
    brand: 'Huawei / Honor',
    uiName: 'HarmonyOS / MagicOS 9',
    baseOs: 'HarmonyOS / Android 15',
    philosophy: 'ecosystem',
    philosophyLabel: 'پرآپشن و اکوسیستم',
    tagline: 'پشته ویجت‌های سرویس + پوشه‌های هوشمند + Magic Portal',
    summary:
      'تمرکز بر کشیدن آیکون به بالا برای ظاهر شدن کارت سرویس (Snippets)، چیدن ویجت‌ها روی هم برای صرفه‌جویی در فضا و کشیدن محتوا به لبه صفحه با Magic Portal.',
    architecturalStrength: 'مدیریت فضا با Widget Stacking و پوشه‌های بزرگ قابل تغییر اندازه در چند نسبت (۱×۲، ۲×۲، ۳×۱).',
    kayumarsStatus: 'adapted',
    kayumarsStatusLabel: 'الهام در پوشه و منوی آیکون',
    kayumarsTakeaway: 'ایده پوشه‌های ۴تایی مستقیم و منوی سریع نگه‌داشتن روی آیکون بدون شلوغی سرویس‌های پس‌زمینه.',
    omittedReason: 'هاب Super Device و سرویس‌های کلود بین‌دستگاهی به دلیل سیاست آفلاین مطلق کیومرث پیاده‌سازی نمی‌شوند.',
    layers: {
      lockAod: 'لاک‌اسکرین‌های هنری با سبک مجله‌ای و برش خودکار سوژه + AODهای مینیمال تعاملی.',
      homeFolders: 'Service Widgets قابل روی‌هم‌گذاری (Stacking) + پوشه‌های هوشمند بزرگ با اسکرول داخلی.',
      drawer: 'حالت بدون کشو یا کشوی الفبایی سریع با پیشنهاد اپ‌های مرتبط با زمینه کاربر.',
      controlCapsule: 'کنترل پنل با هاب Super Device و Magic Capsule زنده برای تایمر، تماس و ضبط صدا.',
      themeIcons: 'تم‌های گرافیکی جامع، فونت‌های متغیر و آیکون‌های سه‌بعدی یا فلت یکپارچه.',
      gesturesReach: 'کشیدن هر آیکون به بالا برای نمایش کارت اطلاعات فوری بدون باز کردن کامل برنامه.',
    },
    signatureFeatures: ['Service Widget Stacking', 'Smart Folders', 'Magic Capsule', 'Swipe-Up Icon Cards'],
    simulatorPreset: {
      label: 'تست پوشه بزرگ و کپسول MagicOS در کیومرث',
      prefs: {
        themeMode: 'system',
        clockStyle: 'editorial',
        iconTintStyle: 'full_color',
        iconShape: 'rounded-square',
        liveCapsuleEnabled: true,
      },
      openFolder: true,
    },
  },
  {
    id: 'hello-ui',
    brand: 'Motorola',
    uiName: 'Hello UI',
    baseOs: 'Android 15',
    philosophy: 'minimal',
    philosophyLabel: 'مینیمال و استاک',
    tagline: 'سبکی اندروید خالص + انتخاب فونت و شکل آیکون + ژست‌های سریع Moto',
    summary:
      'نزدیک‌ترین تجربه به اندروید خالص اما با پنل Personalize متمرکز: تغییر فونت کل سیستم، انتخاب ۴ شکل آیکون، تغییر گرید خانه و نگهداری مصرف رم در پایین‌ترین سطح ممکن.',
    architecturalStrength: 'پوسته‌ای بسیار سبک بدون دوباره‌کاری سرویس‌ها با دسترسی مستقیم به شخصی‌سازی فونت و گرید.',
    kayumarsStatus: 'adopted',
    kayumarsStatusLabel: 'همسو با فلسفه کیومرث',
    kayumarsTakeaway: 'تغییر شکل آیکون (دایره، اسکویرکل، سیستمی)، انتخاب تم بدون نیاز به نصب اپ جانبی و حفظ رم زیر ۵۰ مگابایت.',
    layers: {
      lockAod: 'Peek Display تعاملی برای خواندن اعلانات بدون روشن کردن کامل صفحه و ساعت‌های شخصی‌سازی‌شده.',
      homeFolders: 'شبکه تمیز استاک با انتخاب ۴×۴ تا ۵×۶ و پوشه‌های استاندارد متریال.',
      drawer: 'کشوی عمودی شفاف با جستجوی فوری برنامه‌ها و فهرست پرکاربردها در بالا.',
      controlCapsule: 'تنظیمات سریع استاندارد یا کلاسیک با رنگ‌بندی Monet و دکمه‌های بزرگ خوانا.',
      themeIcons: 'پنل Personalize یکپارچه برای تغییر فونت، شکل آیکون، رنگ تاکید و حالت تیره/روشن.',
      gesturesReach: 'ژست‌های سخت‌افزاری Chop (چراغ‌قوه)، Twist (دوربین) و دو بار ضربه در پشت گوشی.',
    },
    signatureFeatures: ['Ultra-Light Stock Shell', 'System Font Switcher', 'Peek Display', 'Chop & Twist Gestures'],
    simulatorPreset: {
      label: 'تست سبک سبک‌وزن Hello UI در کیومرث',
      prefs: {
        themeMode: 'dark',
        clockStyle: 'editorial',
        iconTintStyle: 'full_color',
        iconShape: 'circle',
        edgePanelEnabled: false,
        liveCapsuleEnabled: false,
      },
    },
  },
  {
    id: 'xperia-ui',
    brand: 'Sony',
    uiName: 'Xperia UI',
    baseOs: 'Android 15',
    philosophy: 'minimal',
    philosophyLabel: 'مینیمال و استاک',
    tagline: 'ساختار خالص AOSP + نوار لمسی شست Side Sense برای گوشی‌های بلند',
    summary:
      'یکی از سریع‌ترین پوسته‌های اندرویدی با تمرکز بر بهره‌وری تک‌دستی روی نمایشگرهای کشیده: نوار Side Sense نامحسوس در لبه صفحه که با یک لمس منوی شست و اپ‌های جفت را باز می‌کند.',
    architecturalStrength: 'ارگونومی یک‌دستی بدون تغییر چیدمان صفحه اصلی؛ نوار Side Sense همیشه در دسترس انگشت شست است.',
    kayumarsStatus: 'adopted',
    kayumarsStatusLabel: 'الگوی مستقیم Edge Strip',
    kayumarsTakeaway: 'نوار لمسی کناری Side Sense / Edge Quick Strip مستقیماً در صفحه خانه کیومرث (وب و اندروید) پیاده شد.',
    layers: {
      lockAod: 'صفحه قفل ساده AOSP با نمایش دقیق جزئیات دوربین/پخش‌کننده و بدون عناصر سنگین.',
      homeFolders: 'چیدمان تمیز استاک بدون تغییر اجباری ویجت‌ها و رفرش‌ریت ثابت ۱۲۰ هرتز.',
      drawer: 'کشوی عمودی استاندارد بدون دسته‌بندی اجباری، باز شدن سریع با کمترین تاخیر ورودی.',
      controlCapsule: 'تنظیمات سریع خالص اندروید با پایداری بالا و حداقل مصرف رم پس‌زمینه.',
      themeIcons: 'آیکون‌های استاندارد تطبیقی (Adaptive Icons) با احترام کامل به پک آیکون سیستم.',
      gesturesReach: 'Side Sense: نوار لمسی بسیار باریک در لبه راست/چپ صفحه برای دسترسی فوری شست به اپ‌ها و ابزارهای کلیدی.',
    },
    signatureFeatures: ['Side Sense Edge Bar', 'App Pair Multi-Window', 'Near-AOSP Latency', '120Hz Consistency'],
    simulatorPreset: {
      label: 'تست نوار شست Side Sense اکسپریا در کیومرث',
      prefs: {
        themeMode: 'amoled',
        clockStyle: 'editorial',
        iconTintStyle: 'full_color',
        iconShape: 'system',
        edgePanelEnabled: true,
        liveCapsuleEnabled: false,
      },
      openEdge: true,
    },
  },
];
