import 'dart:async';
import 'dart:ui';

import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter/services.dart';
import 'menus/app_manager_menu.dart';
import 'menus/custom_helper_menu.dart';
import 'menus/home_menu.dart';
import 'menus/about_menu.dart';
import 'menus/tuning_menu.dart';
import 'menus/tweaks_menu.dart';
import 'menus/update_manager.dart';
import 'theme_manager.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const AozoraApp());
}

class AozoraApp extends StatefulWidget {
  const AozoraApp({super.key});

  @override
  State<AozoraApp> createState() => _AozoraAppState();
}

class _AozoraAppState extends State<AozoraApp> {
  ThemeMode _themeMode = ThemeMode.dark;

  @override
  void initState() {
    super.initState();
    _loadTheme();
  }

  Future<void> _loadTheme() async {
    final prefs = await SharedPreferences.getInstance();
    final themeIndex = prefs.getInt('themeMode') ?? 2; // default to dark
    if (mounted) {
      setState(() {
        _themeMode = ThemeMode.values[themeIndex];
      });
    }
  }

  void _setThemeMode(ThemeMode mode) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt('themeMode', mode.index);
    if (mounted) {
      setState(() {
        _themeMode = mode;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return AozoraThemeManager(
      themeMode: _themeMode,
      setThemeMode: _setThemeMode,
      child: DynamicColorBuilder(
        builder: (ColorScheme? lightDynamic, ColorScheme? darkDynamic) {
          return MaterialApp(
            title: 'Aozora Kernel Manager',
            debugShowCheckedModeBanner: false,
            themeMode: _themeMode,
            theme: ThemeData(
              colorScheme: lightDynamic ?? ColorScheme.fromSeed(seedColor: Colors.deepPurple),
              useMaterial3: true,
            ),
            darkTheme: ThemeData(
              colorScheme: darkDynamic ?? ColorScheme.fromSeed(seedColor: Colors.deepPurple, brightness: Brightness.dark),
              useMaterial3: true,
            ),
            home: const HomePage(),
          );
        },
      ),
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> with SingleTickerProviderStateMixin {
  static const platform = MethodChannel('com.xaozora.manager/daemon');

  int _selectedIndex = 0;
  bool _isBottomBarVisible = true;
  bool _isNavigating = false;

  bool _isAutdAvailable = false;
  bool _isAozoraModuleInstalled = false;

  // app manager reference
  final GlobalKey<AppManagerMenuState> _appManagerKey = GlobalKey();

  final Map<int, GlobalKey> _navKeys = {
    0: GlobalKey(), 1: GlobalKey(), 2: GlobalKey(),
    3: GlobalKey(), 4: GlobalKey(), 5: GlobalKey(),
  };

  late PageController _pageController;

  @override
  void initState() {
    super.initState();
    _checkAutdAvailability();
    _pageController = PageController(initialPage: _selectedIndex);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      UpdateManager.checkAndUpdate(context);
    });
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  Future<void> _checkAutdAvailability() async {
    try {
      final bool autdExists = await platform.invokeMethod('checkFileExists', {'path': '/system/bin/autd'});
      final bool moduleExists = await platform.invokeMethod('checkFileExists', {'path': r"$(grep -il 'id=.*aozora' /data/adb/modules/*/module.prop 2>/dev/null | head -n 1)"});
      if (mounted) {
        setState(() {
          _isAutdAvailable = autdExists;
          _isAozoraModuleInstalled = moduleExists;
          if (_selectedIndex >= _getNavItemCount()) {
            _selectedIndex = _getNavItemCount() - 1;
          }
        });
      }
    } catch (e) { /* ignore */ }
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    final pages = <Widget>[
      _buildPageWrapper(const HomeMenu()),
    ];
    
    int pageIndex = 0;
    int appsIndex = -1;
    final navs = <Widget>[];

    Widget createNav(int keyIndex, IconData icon, String label) {
      final widget = _buildNavItem(context, _navKeys[keyIndex]!, icon, label, pageIndex);
      pageIndex++;
      return widget;
    }

    navs.add(createNav(0, Icons.home_rounded, "Home"));

    if (_isAutdAvailable || _isAozoraModuleInstalled) {
      pages.add(_buildPageWrapper(const TuningMenu()));
      navs.add(const SizedBox(width: 8));
      navs.add(createNav(1, Icons.tune_rounded, "Tuning"));
    }

    pages.add(_buildPageWrapper(const TweaksMenu()));
    navs.add(const SizedBox(width: 8));
    navs.add(createNav(2, Icons.build_circle_outlined, "Tweaks"));

    pages.add(_buildPageWrapper(const CustomHelperMenu()));
    navs.add(const SizedBox(width: 8));
    navs.add(createNav(3, Icons.extension_rounded, "Helper"));

    if (_isAutdAvailable) {
      appsIndex = pages.length;
      pages.add(_buildPageWrapper(AppManagerMenu(key: _appManagerKey)));
      navs.add(const SizedBox(width: 8));
      navs.add(createNav(4, Icons.apps_rounded, "Apps"));
    }

    pages.add(_buildPageWrapper(const AboutMenu()));
    final aboutNavWidget = createNav(5, Icons.info_outline_rounded, "About");

    return Scaffold(
      extendBody: true,
      body: PageView(
        controller: _pageController,
        onPageChanged: (index) {
          if (!_isNavigating) {
            setState(() {
              _selectedIndex = index;
              _isBottomBarVisible = true;
            });
            _scrollToActiveNav(index);
          }
        },
        children: pages,
      ),
      floatingActionButton: (_isAutdAvailable && _selectedIndex == appsIndex)
          ? ClipRRect(
              borderRadius: BorderRadius.circular(18),
              child: BackdropFilter(
                filter: ImageFilter.blur(sigmaX: 20.0, sigmaY: 20.0),
                child: Container(
                  decoration: BoxDecoration(
                    color: colorScheme.primaryContainer.withOpacity(0.25),
                    borderRadius: BorderRadius.circular(18),
                    border: Border.all(
                      color: colorScheme.primary.withOpacity(0.4),
                      width: 1,
                    ),
                  ),
                  child: InkWell(
                    borderRadius: BorderRadius.circular(18),
                    onTap: () => _appManagerKey.currentState?.showAddAppSheet(),
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Icon(Icons.add, color: colorScheme.onPrimaryContainer),
                    ),
                  ),
                ),
              ),
            )
          : null,
      // floating nav bar
      bottomNavigationBar: AnimatedSlide(
        duration: const Duration(milliseconds: 200),
        offset: _isBottomBarVisible ? Offset.zero : const Offset(0, 1),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(40, 0, 40, 30),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(50),
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 20.0, sigmaY: 20.0),
              child: Container(
                height: 70,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(50),
                  border: Border.all(
                    color: colorScheme.outlineVariant.withOpacity(0.5),
                    width: 1.2,
                  ),
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [
                      Colors.transparent,
                      Colors.transparent,
                      Colors.transparent,
                      Colors.transparent,
                    ],
                    stops: const [0.0, 0.05, 0.95, 1.0],
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: colorScheme.shadow.withOpacity(0.3),
                      blurRadius: 30,
                      spreadRadius: -5,
                      offset: const Offset(0, 15),
                    ),
                  ],
                ),
              child: Row(
                children: [
                  Expanded(
                    child: SingleChildScrollView(
                      scrollDirection: Axis.horizontal,
                      padding: const EdgeInsets.only(left: 8, right: 4),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.start,
                        children: navs,
                      ),
                    ),
                  ),
                  Container(
                    width: 1,
                    height: 24,
                    color: colorScheme.outlineVariant.withOpacity(0.5),
                  ),
                  Padding(
                    padding: const EdgeInsets.only(left: 4, right: 8),
                    child: aboutNavWidget,
                  ),
                ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildPageWrapper(Widget child) {
    return NotificationListener<UserScrollNotification>(
      onNotification: (notification) {
        if (notification.metrics.axis == Axis.vertical) {
          if (notification.direction == ScrollDirection.forward) {
            if (!_isBottomBarVisible) setState(() => _isBottomBarVisible = true);
          } else if (notification.direction == ScrollDirection.reverse) {
            if (_isBottomBarVisible) setState(() => _isBottomBarVisible = false);
          }
        }
        return true;
      },
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(24, 60, 24, 120),
        child: child,
      ),
    );
  }

  GlobalKey? _getActiveNavKey(int index) {
    int currentIndex = 0;
    if (index == currentIndex++) return _navKeys[0];
    if (_isAutdAvailable || _isAozoraModuleInstalled) {
      if (index == currentIndex++) return _navKeys[1];
    }
    if (index == currentIndex++) return _navKeys[2];
    if (index == currentIndex++) return _navKeys[3];
    if (_isAutdAvailable) {
      if (index == currentIndex++) return _navKeys[4];
    }
    if (index == currentIndex++) return _navKeys[5];
    return null;
  }

  void _scrollToActiveNav(int index) {
    if (index >= _getNavItemCount()) return;
    final key = _getActiveNavKey(index);
    if (key == null) return;
    final context = key.currentContext;
    if (context != null) {
      Scrollable.ensureVisible(
        context,
        alignment: 0.5,
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeInOut,
      );
    }
  }

  Widget _buildNavItem(BuildContext context, GlobalKey key, IconData icon, String label, int index) {
    final isSelected = _selectedIndex == index;
    final colorScheme = Theme.of(context).colorScheme;

    return GestureDetector(
      key: key,
      onTap: () async {
        if (_selectedIndex == index) return;
        setState(() {
          _selectedIndex = index;
          _isNavigating = true;
        });
        await _pageController.animateToPage(index, duration: const Duration(milliseconds: 300), curve: Curves.easeInOut);
        if (mounted) {
          setState(() => _isNavigating = false);
          _scrollToActiveNav(index);
        }
      },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: EdgeInsets.symmetric(horizontal: isSelected ? 20 : 16, vertical: 12),
        decoration: isSelected
            ? BoxDecoration(
                color: colorScheme.primaryContainer.withOpacity(0.25),
                borderRadius: BorderRadius.circular(30),
                border: Border.all(
                  color: colorScheme.primary.withOpacity(0.4),
                  width: 1,
                ),
              )
            : BoxDecoration(
                color: Colors.transparent,
                borderRadius: BorderRadius.circular(30),
                border: Border.all(
                  color: Colors.transparent,
                  width: 1,
                ),
              ),
        child: Row(
          children: [
            Icon(
              icon,
              color: isSelected ? colorScheme.onPrimaryContainer : colorScheme.secondary,
            ),
            if (isSelected) ...[
              const SizedBox(width: 8),
              Text(
                label,
                style: TextStyle(
                  color: colorScheme.onPrimaryContainer,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  int _getNavItemCount() {
    int count = 4;
    if (_isAutdAvailable || _isAozoraModuleInstalled) count++;
    if (_isAutdAvailable) count++;
    return count;
  }
}
