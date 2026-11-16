/**
 * TypeScript Twin of Kayumars Rust `gesture_engine`.
 * Implements the 12 mandatory v1.0 gestures + per-icon dedicated gestures,
 * battery-first home-only multi-touch gating, and decoupled semantic action dispatch.
 */

export type GestureKind =
  | 'SingleTap'
  | 'DoubleTap'
  | 'LongPress'
  | 'LongPressDrag'
  | 'SwipeUp'
  | 'SwipeDown'
  | 'SwipeLeft'
  | 'SwipeRight'
  | 'PinchIn'
  | 'PinchOut'
  | 'TwoFingerSwipeUp'
  | 'TwoFingerSwipeDown';

export type SurfaceZone =
  | 'HomeScreen'
  | 'AppIcon'
  | 'AppDrawer'
  | 'SearchOverlay'
  | 'SettingsSheet';

export type GestureFsmState =
  | 'Idle'
  | 'PossibleTap'
  | 'AwaitDoubleTap'
  | 'LongPressActive'
  | 'Dragging'
  | 'Swiping'
  | 'MultiTouchTracking';

export type GestureProfilePreset =
  | 'BalancedDefault'
  | 'BatterySaver'
  | 'OneHanded'
  | 'PowerUser';

export type SemanticActionType =
  | 'None'
  | 'LaunchTarget'
  | 'LaunchShortcut'
  | 'MoveIcon'
  | 'LockScreen'
  | 'ExpandNotifications'
  | 'ExpandQuickSettings'
  | 'OpenAppDrawer'
  | 'OpenSearch'
  | 'NextPage'
  | 'PreviousPage'
  | 'OpenOverview'
  | 'ReturnHome'
  | 'OpenLauncherSettings'
  | 'OpenEditMenu';

export interface GestureRule {
  kind: GestureKind;
  label: string;
  faLabel: string;
  enabled: boolean;
  sensitivity: number; // 0.5 .. 2.0
  minDistanceDp: number;
  timeoutMs: number;
  action: SemanticActionType;
  isMultiTouch: boolean;
}

export interface IconGestureBinding {
  kind: GestureKind;
  action: SemanticActionType;
  shortcutId?: string;
  label: string;
  faLabel: string;
}

export interface DispatchedCommand {
  id: number;
  timestampMs: number;
  recognized: boolean;
  gesture: GestureKind | null;
  action: SemanticActionType;
  zone: SurfaceZone;
  fsmState: GestureFsmState;
  targetPackage?: string;
  shortcutId?: string;
  shortcutLabel?: string;
  dragDxDp?: number;
  dragDyDp?: number;
}

export const ALL_V1_GESTURES: GestureKind[] = [
  'SingleTap',
  'DoubleTap',
  'LongPress',
  'LongPressDrag',
  'SwipeUp',
  'SwipeDown',
  'SwipeLeft',
  'SwipeRight',
  'PinchIn',
  'PinchOut',
  'TwoFingerSwipeUp',
  'TwoFingerSwipeDown',
];

export const ACTION_LABELS: Record<SemanticActionType, { en: string; fa: string }> = {
  None: { en: 'Disabled', fa: 'غیرفعال' },
  LaunchTarget: { en: 'Launch App', fa: 'اجرای برنامه' },
  LaunchShortcut: { en: 'App Deep Shortcut', fa: 'میانبر اختصاصی برنامه' },
  MoveIcon: { en: 'Move Icon', fa: 'جابه‌جایی آیکون' },
  LockScreen: { en: 'Lock Screen', fa: 'قفل صفحه' },
  ExpandNotifications: { en: 'Notification Panel', fa: 'پنل اعلان‌ها' },
  ExpandQuickSettings: { en: 'Quick Settings', fa: 'تنظیمات سریع' },
  OpenAppDrawer: { en: 'Open App Drawer', fa: 'باز کردن کشوی برنامه‌ها' },
  OpenSearch: { en: 'Unified Search', fa: 'جستجوی سریع' },
  NextPage: { en: 'Next Page', fa: 'صفحه بعد' },
  PreviousPage: { en: 'Previous Page', fa: 'صفحه قبل' },
  OpenOverview: { en: 'Overview', fa: 'نمای کلی (Overview)' },
  ReturnHome: { en: 'Return to Home', fa: 'بازگشت به خانه' },
  OpenLauncherSettings: { en: 'Launcher Settings', fa: 'تنظیمات لانچر' },
  OpenEditMenu: { en: 'Edit / Context Menu', fa: 'منوی ویرایش' },
};

export function createPresetRules(preset: GestureProfilePreset): Record<GestureKind, GestureRule> {
  const base: Record<GestureKind, GestureRule> = {
    SingleTap: {
      kind: 'SingleTap',
      label: 'Single Tap',
      faLabel: 'تک ضربه (Tap)',
      enabled: true,
      sensitivity: 1.0,
      minDistanceDp: 0,
      timeoutMs: 220,
      action: 'LaunchTarget',
      isMultiTouch: false,
    },
    DoubleTap: {
      kind: 'DoubleTap',
      label: 'Double Tap',
      faLabel: 'دوبار ضربه (Double Tap)',
      enabled: true,
      sensitivity: 1.0,
      minDistanceDp: 0,
      timeoutMs: 260,
      action: 'LockScreen',
      isMultiTouch: false,
    },
    LongPress: {
      kind: 'LongPress',
      label: 'Long Press',
      faLabel: 'نگه‌داشتن (Long Press)',
      enabled: true,
      sensitivity: 1.0,
      minDistanceDp: 0,
      timeoutMs: 420,
      action: 'OpenEditMenu',
      isMultiTouch: false,
    },
    LongPressDrag: {
      kind: 'LongPressDrag',
      label: 'Long Press + Drag',
      faLabel: 'نگه‌داشتن + کشیدن',
      enabled: true,
      sensitivity: 1.0,
      minDistanceDp: 14,
      timeoutMs: 420,
      action: 'MoveIcon',
      isMultiTouch: false,
    },
    SwipeUp: {
      kind: 'SwipeUp',
      label: 'Swipe Up',
      faLabel: 'کشیدن به بالا',
      enabled: true,
      sensitivity: 1.0,
      minDistanceDp: 48,
      timeoutMs: 550,
      action: 'OpenAppDrawer',
      isMultiTouch: false,
    },
    SwipeDown: {
      kind: 'SwipeDown',
      label: 'Swipe Down',
      faLabel: 'کشیدن به پایین',
      enabled: true,
      sensitivity: 1.0,
      minDistanceDp: 48,
      timeoutMs: 550,
      action: 'ExpandNotifications',
      isMultiTouch: false,
    },
    SwipeLeft: {
      kind: 'SwipeLeft',
      label: 'Swipe Left',
      faLabel: 'کشیدن به چپ',
      enabled: true,
      sensitivity: 1.0,
      minDistanceDp: 56,
      timeoutMs: 500,
      action: 'NextPage',
      isMultiTouch: false,
    },
    SwipeRight: {
      kind: 'SwipeRight',
      label: 'Swipe Right',
      faLabel: 'کشیدن به راست',
      enabled: true,
      sensitivity: 1.0,
      minDistanceDp: 56,
      timeoutMs: 500,
      action: 'PreviousPage',
      isMultiTouch: false,
    },
    PinchIn: {
      kind: 'PinchIn',
      label: 'Pinch In',
      faLabel: 'جمع کردن دو انگشت',
      enabled: true,
      sensitivity: 1.0,
      minDistanceDp: 44,
      timeoutMs: 650,
      action: 'OpenOverview',
      isMultiTouch: true,
    },
    PinchOut: {
      kind: 'PinchOut',
      label: 'Pinch Out',
      faLabel: 'باز کردن دو انگشت',
      enabled: true,
      sensitivity: 1.0,
      minDistanceDp: 44,
      timeoutMs: 650,
      action: 'ReturnHome',
      isMultiTouch: true,
    },
    TwoFingerSwipeUp: {
      kind: 'TwoFingerSwipeUp',
      label: 'Two-Finger Swipe Up',
      faLabel: 'کشیدن دو انگشتی به بالا',
      enabled: true,
      sensitivity: 1.0,
      minDistanceDp: 52,
      timeoutMs: 600,
      action: 'ExpandQuickSettings',
      isMultiTouch: true,
    },
    TwoFingerSwipeDown: {
      kind: 'TwoFingerSwipeDown',
      label: 'Two-Finger Swipe Down',
      faLabel: 'کشیدن دو انگشتی به پایین',
      enabled: true,
      sensitivity: 1.0,
      minDistanceDp: 52,
      timeoutMs: 600,
      action: 'OpenLauncherSettings',
      isMultiTouch: true,
    },
  };

  if (preset === 'BatterySaver') {
    base.PinchIn.enabled = false;
    base.PinchOut.enabled = false;
    base.TwoFingerSwipeUp.enabled = false;
    base.TwoFingerSwipeDown.enabled = false;
  } else if (preset === 'OneHanded') {
    base.SwipeDown.action = 'OpenSearch';
    base.SwipeDown.minDistanceDp = 38;
    base.SwipeDown.sensitivity = 1.25;
    base.SwipeUp.minDistanceDp = 38;
    base.SwipeUp.sensitivity = 1.25;
    base.TwoFingerSwipeDown.action = 'ExpandNotifications';
  } else if (preset === 'PowerUser') {
    base.SwipeDown.action = 'OpenSearch';
    base.DoubleTap.timeoutMs = 220;
  }

  return base;
}

export function createDefaultIconGestures(): Record<string, Partial<Record<GestureKind, IconGestureBinding>>> {
  return {
    'org.telegram.messenger': {
      SingleTap: {
        kind: 'SingleTap',
        action: 'LaunchTarget',
        shortcutId: 'tg_open',
        label: 'Open Telegram',
        faLabel: 'باز کردن تلگرام',
      },
      DoubleTap: {
        kind: 'DoubleTap',
        action: 'LaunchShortcut',
        shortcutId: 'tg_saved_messages',
        label: 'Saved Messages',
        faLabel: 'پیام‌های ذخیره‌شده (Saved Messages)',
      },
      SwipeUp: {
        kind: 'SwipeUp',
        action: 'LaunchShortcut',
        shortcutId: 'tg_new_message',
        label: 'New Message',
        faLabel: 'پیام جدید',
      },
      SwipeDown: {
        kind: 'SwipeDown',
        action: 'LaunchShortcut',
        shortcutId: 'tg_search',
        label: 'Search in Telegram',
        faLabel: 'جستجو در تلگرام',
      },
      SwipeLeft: {
        kind: 'SwipeLeft',
        action: 'LaunchShortcut',
        shortcutId: 'tg_contacts',
        label: 'Contacts',
        faLabel: 'مخاطبین تلگرام',
      },
      SwipeRight: {
        kind: 'SwipeRight',
        action: 'LaunchShortcut',
        shortcutId: 'tg_settings',
        label: 'Telegram Settings',
        faLabel: 'تنظیمات تلگرام',
      },
    },
    'com.android.camera': {
      DoubleTap: {
        kind: 'DoubleTap',
        action: 'LaunchShortcut',
        shortcutId: 'cam_selfie',
        label: 'Front Camera',
        faLabel: 'دوربین سلفی',
      },
      SwipeUp: {
        kind: 'SwipeUp',
        action: 'LaunchShortcut',
        shortcutId: 'cam_video',
        label: 'Record Video',
        faLabel: 'ضبط ویدیو سریع',
      },
    },
    'com.google.android.dialer': {
      SwipeUp: {
        kind: 'SwipeUp',
        action: 'LaunchShortcut',
        shortcutId: 'dial_voicemail',
        label: 'Favorite Contacts',
        faLabel: 'شماره‌های برگزیده',
      },
    },
  };
}

export function effectiveDistance(rule: GestureRule): number {
  const s = Math.max(0.4, Math.min(2.5, rule.sensitivity));
  return Math.max(4, rule.minDistanceDp / s);
}

export function resolveGestureAction(
  kind: GestureKind,
  zone: SurfaceZone,
  rules: Record<GestureKind, GestureRule>,
  iconGestures: Record<string, Partial<Record<GestureKind, IconGestureBinding>>>,
  targetPackage?: string
): { action: SemanticActionType; shortcutId?: string; shortcutLabel?: string } {
  if (kind.startsWith('TwoFinger') || kind.startsWith('Pinch')) {
    if (zone !== 'HomeScreen') {
      return { action: 'None' };
    }
  }

  const rule = rules[kind];
  if (!rule || !rule.enabled) {
    return { action: 'None' };
  }

  if (targetPackage) {
    const perApp = iconGestures[targetPackage]?.[kind];
    if (perApp) {
      return {
        action: perApp.action,
        shortcutId: perApp.shortcutId,
        shortcutLabel: perApp.label,
      };
    }
    if (kind === 'SingleTap') return { action: 'LaunchTarget' };
    if (kind === 'LongPress') return { action: 'OpenEditMenu' };
    if (kind === 'LongPressDrag') return { action: 'MoveIcon' };
  }

  return { action: rule.action };
}
