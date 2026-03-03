import 'dart:async';
import 'dart:ui';

import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'menus/app_manager_menu.dart';
import 'menus/custom_helper_menu.dart';
import 'menus/home_menu.dart';
import 'menus/tuning_menu.dart';
import 'menus/tweaks_menu.dart';

void main() {
  runApp(const AozoraApp());
}

class AozoraApp extends StatelessWidget {
  const AozoraApp({super.key});

  @override
  Widget build(BuildContext context) {
    return DynamicColorBuilder(
      builder: (ColorScheme? lightDynamic, ColorScheme? darkDynamic) {
        return MaterialApp(
          title: 'Aozora Kernel Manager',
          debugShowCheckedModeBanner: false,
          themeMode: ThemeMode.dark,
          theme: ThemeData(
            colorScheme: lightDynamic ?? ColorScheme.fromSeed(seedColor: Colors.deepPurple),
            useMaterial3: true,
          ),
          darkTheme: ThemeData(
            colorScheme: darkDynamic ?? ColorScheme.fromSeed(
              seedColor: Colors.deepPurple,
              brightness: Brightness.dark,
            ),
            useMaterial3: true,
            scaffoldBackgroundColor: const Color(0xFF121212),
          ),
          home: const HomePage(),
        );
      },
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

  // app manager reference
  final GlobalKey<AppManagerMenuState> _appManagerKey = GlobalKey();

  final Map<int, GlobalKey> _navKeys = {
    0: GlobalKey(), 1: GlobalKey(), 2: GlobalKey(),
    3: GlobalKey(), 4: GlobalKey(),
  };

  late PageController _pageController;

  @override
  void initState() {
    super.initState();
    _checkAutdAvailability();
    _pageController = PageController(initialPage: _selectedIndex);
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  Future<void> _checkAutdAvailability() async {
    try {
      final bool exists = await platform.invokeMethod('checkFileExists', {'path': '/system/bin/autd'});
      if (mounted) {
        setState(() {
          _isAutdAvailable = exists;
        });
      }
    } catch (e) { /* ignore */ }
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

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
        children: [
          _buildPageWrapper(const HomeMenu()),
          _buildPageWrapper(const TuningMenu()),
          _buildPageWrapper(const TweaksMenu()),
          _buildPageWrapper(const CustomHelperMenu()),
          if (_isAutdAvailable) _buildPageWrapper(AppManagerMenu(key: _appManagerKey)),
        ],
      ),
      floatingActionButton: (_selectedIndex == 4 && _isAutdAvailable)
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
                    color: Colors.white.withOpacity(0.15),
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
                child: SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 8),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.start,
                    children: [
                      _buildNavItem(context, _navKeys[0]!, Icons.home_rounded, "Home", 0),
                      const SizedBox(width: 8),
                      _buildNavItem(context, _navKeys[1]!, Icons.tune_rounded, "Tuning", 1),
                      const SizedBox(width: 8),
                      _buildNavItem(context, _navKeys[2]!, Icons.build_circle_outlined, "Tweaks", 2),
                      const SizedBox(width: 8),
                      _buildNavItem(context, _navKeys[3]!, Icons.extension_rounded, "Helper", 3),
                      if (_isAutdAvailable) ...[
                        const SizedBox(width: 8),
                        _buildNavItem(context, _navKeys[4]!, Icons.apps_rounded, "Apps", 4),
                      ],
                    ],
                  ),
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

  void _scrollToActiveNav(int index) {
    if (index >= _getNavItemCount()) return;
    final key = _navKeys[index];
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
              color: isSelected ? colorScheme.onPrimaryContainer : Colors.grey,
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

  int _getNavItemCount() => _isAutdAvailable ? 5 : 4;
}
