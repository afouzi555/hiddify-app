import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:hiddify/core/app_info/app_info_provider.dart';
import 'package:hiddify/core/model/environment.dart';
import 'package:hiddify/core/model/region.dart';
import 'package:hiddify/core/preferences/actions_at_closing.dart';

import 'package:hiddify/core/preferences/preferences_provider.dart';
import 'package:hiddify/core/utils/preferences_utils.dart';
import 'package:hiddify/features/per_app_proxy/model/per_app_proxy_mode.dart';
import 'package:hiddify/features/window/notifier/window_notifier.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'general_preferences.g.dart';

bool _debugIntroPage = false;

abstract class Preferences {
  static final introCompleted = PreferencesNotifier.create(
    "intro_completed",
    false,
    overrideValue: _debugIntroPage && kDebugMode ? false : null,
  );

  // Null means that auto selection has not been performed yet.
  static final autoAppsSelectionRegion = PreferencesNotifier.create<Region?, String?>(
    "auto_apps_selection_region",
    null,
    mapFrom: (value) => value == null || value.isEmpty ? null : Region.values.byName(value),
    mapTo: (value) => value == null ? '' : value.name,
  );

  static final autoAppsSelectionUpdateInterval = PreferencesNotifier.create<double, double>(
    "auto_apps_selection_update_interval",
    1.0,
  );

  static final autoAppsSelectionLastUpdate = PreferencesNotifier.create<DateTime?, String?>(
    "auto_apps_selection_last_update",
    null,
    mapFrom: (value) => value == null ? null : DateTime.tryParse(value),
    mapTo: (value) => value?.toIso8601String(),
  );

  static final includeApps = PreferencesNotifier.create<List<String>, List<String>>(
    "per_app_proxy_include_list",
    <String>[],
  );

  static final excludeApps = PreferencesNotifier.create<List<String>, List<String>>(
    "per_app_proxy_exclude_list",
    <String>[],
  );

  static final windowMaximized = PreferencesNotifier.create<bool, bool>("window_maximized", false);

  static final windowPosition = PreferencesNotifier.create<Offset?, String?>(
    "window_position",
    null,
    mapFrom: (value) {
      if (value == null) return null;
      final list = value.split(',').map((e) => double.tryParse(e)).toList();
      return Offset(list[0]!, list[1]!);
    },
    mapTo: (value) {
      if (value == null) return null;
      return "${value.dx},${value.dy}";
    },
  );

  static final windowSize = PreferencesNotifier.create<Size, String>(
    "window_size",
    defaultWindowSize,
    mapFrom: (value) {
      final list = value.split(',').map((e) => double.tryParse(e)).toList();
      return Size(list[0]!, list[1]!);
    },
    mapTo: (value) => "${value.width},${value.height}",
  );

  static final silentStart = PreferencesNotifier.create<bool, bool>("silent_start", false);

  // BUG FIX (2026-09-04): default changed from PlatformUtils.isDesktop (Android/iOS stayed
  // memory-limited) to true on ALL platforms. Root cause chain, confirmed live on a OnePlus
  // Nord 5G: libbox.SetMemoryLimit(true) (platform/mobile default when this preference is
  // false) calls runtimeDebug.SetGCPercent(10) + runtimeDebug.SetMemoryLimit(30MB) --
  // deliberately aggressive settings meant to protect low-RAM devices from OOM. On this
  // device, VPN startup's own memory allocation reliably crosses that 30MB soft limit ~5-7s
  // in, forcing an emergency GC cycle. That GC collects the Go-side wrapper object holding
  // the platform interface's gomobile/JNI reference (refnum stayed the SAME single number,
  // 42, across every reproduction) before gomobile's own reference-counting bridge accounts
  // for calls still in flight against it -- a real, upstream gomobile GC-safety gap (same
  // failure signature -- go_seq_from_refnum / "Unknown reference: N" -- as the independently
  // filed SagerNet/sing-box#1895, "crashing/panic randomly in Tun mode", closed upstream as
  // "not planned"). Confirmed NOT caused by any of this session's own registration/timing
  // fixes: the crash site kept moving downstream as those fixes landed (GetInterfaces ->
  // OpenTun -> AutoDetectInterfaceControl, the last one firing 6ms after the VPN tunnel had
  // ALREADY established successfully) precisely because each fix removed one early call into
  // the platform-interface bridge, not because the underlying GC race was fixed. Disabling
  // the aggressive limit (runtimeDebug.SetMemoryLimit(math.MaxInt64), SetGCPercent(100)) is
  // the same escape hatch this preference already exists to provide -- flipping the default
  // trades a defensive OOM guard most modern Android devices don't need for a real, repeatable
  // native crash on ones with this particular GC-timing sensitivity. Still user-overridable
  // from Settings > General > "Memory limit" for anyone who genuinely needs the 30MB cap.
  static final disableMemoryLimit = PreferencesNotifier.create<bool, bool>(
    "disable_memory_limit",
    true,
  );

  static final perAppProxyMode = PreferencesNotifier.create<PerAppProxyMode, String>(
    "per_app_proxy_mode",
    PerAppProxyMode.off,
    mapFrom: PerAppProxyMode.values.byName,
    mapTo: (value) => value.name,
  );

  static final markNewProfileActive = PreferencesNotifier.create<bool, bool>("mark_new_profile_active", true);

  static final dynamicNotification = PreferencesNotifier.create<bool, bool>("dynamic_notification", true);

  static final autoCheckIp = PreferencesNotifier.create<bool, bool>("auto_check_ip", true);

  static final startedByUser = PreferencesNotifier.create<bool, bool>("started_by_user", false);

  static final storeReviewedByUser = PreferencesNotifier.create<bool, bool>("store_reviewed_by_user", false);

  static final actionAtClose = PreferencesNotifier.create<ActionsAtClosing, String>(
    "action_at_close",
    ActionsAtClosing.ask,
    mapFrom: ActionsAtClosing.values.byName,
    mapTo: (value) => value.name,
  );

  static final warpConsentGiven = PreferencesNotifier.create<bool, bool>("warp-consent-given", false);

  static final psiphonConsentGiven = PreferencesNotifier.create<bool, bool>("psiphon-consent-given", false);

  static final showRouteGeneralOptions = PreferencesNotifier.create<bool, bool>("show-route-general-options", true);
}

@Riverpod(keepAlive: true)
class DebugModeNotifier extends _$DebugModeNotifier {
  late final _pref = PreferencesEntry(
    preferences: ref.watch(sharedPreferencesProvider).requireValue,
    key: "debug_mode",
    defaultValue: ref.read(environmentProvider) == Environment.dev,
  );

  @override
  bool build() => _pref.read();

  Future<void> update(bool value) {
    state = value;
    return _pref.write(value);
  }
}
