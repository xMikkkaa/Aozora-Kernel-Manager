import 'package:flutter/material.dart';

class AozoraThemeManager extends InheritedWidget {
  const AozoraThemeManager({
    super.key,
    required this.themeMode,
    required this.setThemeMode,
    required super.child,
  });

  final ThemeMode themeMode;
  final void Function(ThemeMode) setThemeMode;

  static AozoraThemeManager? of(BuildContext context) {
    return context.dependOnInheritedWidgetOfExactType<AozoraThemeManager>();
  }

  @override
  bool updateShouldNotify(AozoraThemeManager oldWidget) {
    return themeMode != oldWidget.themeMode;
  }
}