import 'dart:async';
import 'dart:math' as math;
import 'dart:ui';

import 'package:installed_apps/app_info.dart';
import 'package:installed_apps/installed_apps.dart';
import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';

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
            scaffoldBackgroundColor: const Color(0xFF121212), // Surface Dark Solid
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

class ConfiguredApp {
  final AppInfo app;
  final String mode; // 'p', 'g', 'g2'
  ConfiguredApp(this.app, this.mode);
}

class _HomePageState extends State<HomePage> with SingleTickerProviderStateMixin {
  static const platform = MethodChannel('com.xaozora.manager/daemon');

  // System Data State
  Map<String, String> _systemInfo = {
    'model': 'Loading...',
    'device': '-',
    'android': '-',
    'selinux': '-',
    'soc': '-',
    'ram': '-',
    'kernel': '-',
    'uptime': '-',
    'battery': '-',
    'resolution': '-',
    'governor': '-',
    'root_manager': 'Checking...',
    'root_version': '...',
  };

  int _selectedIndex = 0;
  bool _isBottomBarVisible = true;

  // Tuning Profiles Data
  final List<Map<String, dynamic>> _profiles = [
    {'id': 'powersave', 'name': 'Power Save', 'icon': Icons.battery_saver},
    {'id': 'balance', 'name': 'Balance', 'icon': Icons.balance},
    {'id': 'gaming', 'name': 'Gaming', 'icon': Icons.sports_esports},
    {'id': 'gaming2', 'name': 'Gaming 2', 'icon': Icons.videogame_asset},
    {'id': 'performance', 'name': 'Performance', 'icon': Icons.rocket_launch},
    {'id': 'cachecleaner', 'name': 'Cache Cleaner', 'icon': Icons.cleaning_services},
  ];
  Map<String, bool> _profileAvailability = {};
  String? _processingProfile;
  bool _isDaemonRunning = false;
  Timer? _tuningTimer;
  String? _activeProfileId;

  // Daemon Method State
  String _daemonMethod = 'Checking Daemon...';
  Timer? _daemonMethodTimer;
  bool _isAutdAvailable = false;

  // Tweaks State
  Timer? _ramTimer;
  Map<String, int> _ramStats = {'total': 0, 'used': 0, 'free': 0};
  bool _bypassCharging = false;
  bool _optimizeGameThread = false;

  // App Manager State
  List<ConfiguredApp> _configuredApps = [];
  bool _isLoadingApps = false;

  // Animation
  late final AnimationController _shadowController;

  @override
  void initState() {
    super.initState();
    _fetchSystemInfo();
    _checkProfiles();
    _checkDaemonStatus();
    _checkToggles();
    _checkAutdAvailability();
    
    _ramTimer = Timer.periodic(const Duration(seconds: 2), (timer) {
      if (_selectedIndex == 2) _fetchRamStats();
    });

    _tuningTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_selectedIndex == 1) _checkActiveProfile();
    });

    _daemonMethodTimer = Timer.periodic(const Duration(seconds: 2), (timer) {
      if (_selectedIndex == 0) _checkDaemonMethod();
    });

    _shadowController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 4),
    )..repeat();
  }

  @override
  void dispose() {
    _shadowController.dispose();
    _ramTimer?.cancel();
    _tuningTimer?.cancel();
    _daemonMethodTimer?.cancel();
    super.dispose();
  }

  Future<void> _fetchSystemInfo() async {
    try {
      final Map<dynamic, dynamic> result = await platform.invokeMethod('getSystemInfo');
      if (!mounted) return;
      setState(() {
        _systemInfo = {
          'model': result['model']?.toString() ?? 'Unknown',
          'device': result['device']?.toString() ?? '-',
          'android': result['android']?.toString() ?? '-',
          'selinux': result['selinux']?.toString() ?? '-',
          'soc': result['soc']?.toString() ?? '-',
          'ram': result['ram']?.toString() ?? '-',
          'kernel': result['kernel']?.toString() ?? '-',
          'uptime': result['uptime']?.toString() ?? '-',
          'battery': result['battery']?.toString() ?? '-',
          'resolution': result['resolution']?.toString() ?? '-',
          'governor': result['governor']?.toString() ?? '-',
          'root_manager': result['root_manager']?.toString() ?? 'Unknown',
          'root_version': result['root_version']?.toString() ?? '-',
        };
      });
    } on PlatformException {
      // Handle error gracefully
    }
  }

  Future<void> _checkProfiles() async {
    Map<String, bool> availability = {};
    for (var profile in _profiles) {
      String id = profile['id'];
      try {
        final bool exists = await platform.invokeMethod('checkFileExists', {'path': '/system/bin/$id'});
        availability[id] = exists;
      } catch (e) {
        availability[id] = false;
      }
    }
    if (!mounted) return;
    setState(() {
      _profileAvailability = availability;
    });
  }

  Future<void> _checkActiveProfile() async {
    if (!_isAutdAvailable) return;
    try {
      final status = await platform.invokeMethod('readSystemFile', {'path': '/data/data/com.xaozora.manager/files/autd_status'});
      if (status != null && status.toString().trim().isNotEmpty) {
        if (mounted) {
          setState(() {
            _activeProfileId = status.toString().trim();
          });
        }
      }
    } catch (e) { /* ignore */ }
  }

  Future<void> _checkAutdAvailability() async {
    try {
      final bool exists = await platform.invokeMethod('checkFileExists', {'path': '/system/bin/autd'});
      if (mounted) {
        setState(() {
          _isAutdAvailable = exists;
          if (exists) _fetchConfiguredApps();
        });
      }
    } catch (e) { /* ignore */ }
  }

  Future<void> _checkDaemonMethod() async {
    if (!_isAutdAvailable) return;
    try {
      final String result = await platform.invokeMethod('readSystemFile', {'path': '/data/data/com.xaozora.manager/files/autd_awake_method.info'});
      if (!mounted) return;
      
      final text = result.trim();
      setState(() {
        if (text.isNotEmpty && 
            !text.toLowerCase().contains('no such file') && 
            !text.toLowerCase().contains('error')) {
          _daemonMethod = text;
        } else {
          _daemonMethod = 'Daemon info unavailable';
        }
      });
    } catch (e) {
      if (mounted) setState(() => _daemonMethod = 'Daemon info unavailable');
    }
  }

 Future<void> _executeProfile(String id) async {
    setState(() => _processingProfile = id);
    try {
      if (_isAutdAvailable && id != 'cachecleaner') {
        String cmd = 'echo "$id" > /data/data/com.xaozora.manager/files/autd_base_mode; echo "$id" > /data/data/com.xaozora.manager/files/autd_status';
        
        await platform.invokeMethod('executeScript', {'script': cmd});
        
        setState(() => _activeProfileId = id);
      } else {
        await platform.invokeMethod('executeScript', {'script': '/system/bin/$id'});
      }
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Profile $id applied successfully')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Failed to apply $id: $e'), 
          backgroundColor: Colors.redAccent,
        ),
      );
    } finally {
      if (mounted) setState(() => _processingProfile = null);
    }
  }

  Future<void> _checkDaemonStatus() async {
    try {
      final bool isRunning = await platform.invokeMethod('isDaemonRunning');
      if (!mounted) return;
      setState(() {
        _isDaemonRunning = isRunning;
      });
    } on PlatformException {
      // ignore
    }
  }

  Future<void> _startDaemon() async {
    try {
      await platform.invokeMethod('startDaemon');
      if (!mounted) return;
      setState(() => _isDaemonRunning = true);
    } on PlatformException {
      // ignore
    }
  }

  Future<void> _stopDaemon() async {
    try {
      await platform.invokeMethod('stopDaemon');
      if (!mounted) return;
      setState(() => _isDaemonRunning = false);
    } on PlatformException {
      // ignore
    }
  }

  Future<void> _fetchRamStats() async {
    try {
      final Map<dynamic, dynamic> result = await platform.invokeMethod('getRamStats');
      if (!mounted) return;
      setState(() {
        _ramStats = {
          'total': result['total'] as int,
          'used': result['used'] as int,
          'free': result['free'] as int,
        };
      });
    } catch (e) {
      // ignore
    }
  }

  Future<void> _flushRam() async {
    try {
      // Update: Tambahkan 'am kill-all' agar lebih agresif membunuh background apps
      const cmd = "for P in \$(pidof com.xaozora.manager); do echo -1000 > /proc/\$P/oom_score_adj; done; am kill-all; sync; echo 3 > /proc/sys/vm/drop_caches; [ -f /proc/sys/vm/compact_memory ] && echo 1 > /proc/sys/vm/compact_memory; fstrim -v /data";
      await platform.invokeMethod('executeScript', {'script': cmd});
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('RAM Flushed & Storage Trimmed')),
      );
      // Beri jeda 1 detik agar statistik RAM di /proc/meminfo sempat terupdate
      Future.delayed(const Duration(seconds: 1), _fetchRamStats);
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Failed to flush RAM')),
      );
    }
  }

  Future<void> _confirmFlushRam() async {
    final bool? confirm = await showDialog<bool>(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('Flush RAM?'),
          content: const Text('This will clear system cache and trim storage.'),
          actions: <Widget>[
            TextButton(
              onPressed: () => Navigator.of(context).pop(false),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(context).pop(true),
              child: const Text('Flush'),
            ),
          ],
        );
      },
    );

    if (confirm == true) {
      _flushRam();
    }
  }

  Future<void> _checkToggles() async {
    try {
      final bypass = await platform.invokeMethod('readSystemFile', {'path': '/sys/class/power_supply/battery/input_suspend'});
      final optimize = await platform.invokeMethod('readSystemFile', {'path': '/data/data/com.xaozora.manager/files/autd_opt_allow'});
      
      if (!mounted) return;
      setState(() {
        _bypassCharging = (bypass == '1');
        _optimizeGameThread = (optimize == '1');
      });
    } catch (e) { /* ignore */ }
  }

  Future<void> _toggleSystemSetting(String path, bool value, Function(bool) onUpdate) async {
    try {
      final strValue = value ? '1' : '0';
      final success = await platform.invokeMethod('writeSystemFile', {'path': path, 'value': strValue});
      if (success && mounted) {
        setState(() => onUpdate(value));
      }
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Failed to apply setting')));
    }
  }

  Future<void> _fetchConfiguredApps() async {
    if (!_isAutdAvailable) return;
    setState(() => _isLoadingApps = true);
    try {
      final String content = await platform.invokeMethod('readSystemFile', {'path': '/data/data/com.xaozora.manager/files/applist'});
      final List<String> lines = content.split('\n');
      List<ConfiguredApp> apps = [];

      for (String line in lines) {
        line = line.trim();
        if (line.isEmpty) continue;

        String mode = 'p';
        String packageName = line;

        if (line.endsWith('_g')) {
          mode = 'g';
          packageName = line.substring(0, line.length - 2);
        } else if (line.endsWith('_g2')) {
          mode = 'g2';
          packageName = line.substring(0, line.length - 3);
        } else if (line.endsWith('_p')) {
          mode = 'p';
          packageName = line.substring(0, line.length - 2);
        }

        try {
          final app = await InstalledApps.getAppInfo(packageName);
          if (app != null) {
            apps.add(ConfiguredApp(app, mode));
          }
        } catch (e) {
          // App might be uninstalled but still in config
        }
      }
      
      if (mounted) {
        setState(() {
          _configuredApps = apps;
          _isLoadingApps = false;
        });
      }
    } catch (e) {
      if (mounted) setState(() => _isLoadingApps = false);
    }
  }

  Future<void> _addAppToConfig(String packageName) async {
    try {
      // Default mode: Performance (_p)
      final cmd = "echo \"${packageName}_p\" >> /data/data/com.xaozora.manager/files/applist";
      await platform.invokeMethod('executeScript', {'script': cmd});
      _fetchConfiguredApps();
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Failed to add app')));
    }
  }

  Future<void> _updateAppConfig(String packageName, String newMode) async {
    try {
      // Remove old entry and add new one
      final cmd = "sed -i '/^${packageName}_/d' /data/data/com.xaozora.manager/files/applist; echo \"${packageName}_$newMode\" >> /data/data/com.xaozora.manager/files/applist";
      await platform.invokeMethod('executeScript', {'script': cmd});
      _fetchConfiguredApps();
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Failed to update app')));
    }
  }

  Future<void> _removeAppFromConfig(String packageName) async {
    try {
      final cmd = "sed -i '/^${packageName}_/d' /data/data/com.xaozora.manager/files/applist";
      await platform.invokeMethod('executeScript', {'script': cmd});
      _fetchConfiguredApps();
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Failed to remove app')));
    }
  }

  void _showAddAppSheet() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (context) {
        return DraggableScrollableSheet(
          initialChildSize: 0.8,
          minChildSize: 0.5,
          maxChildSize: 0.95,
          expand: false,
          builder: (context, scrollController) {
            return _AddAppSheetContent(
              scrollController: scrollController,
              onAppSelected: (packageName) {
                Navigator.pop(context);
                _addAppToConfig(packageName);
              },
            );
          },
        );
      },
    );
  }

  void _showEditAppSheet(ConfiguredApp config) {
    showModalBottomSheet(
      context: context,
      builder: (context) {
        return Container(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                children: [
                  config.app.icon != null
                      ? Image.memory(config.app.icon!, width: 48, height: 48)
                      : const Icon(Icons.android, size: 48),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(config.app.name ?? config.app.packageName, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
                        Text(config.app.packageName, style: Theme.of(context).textTheme.bodySmall),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),
              const Text("Select Mode", style: TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 12),
              SegmentedButton<String>(
                segments: const [
                  ButtonSegment(value: 'p', label: Text('Perf'), icon: Icon(Icons.rocket_launch)),
                  ButtonSegment(value: 'g', label: Text('Game'), icon: Icon(Icons.sports_esports)),
                  ButtonSegment(value: 'g2', label: Text('Game+'), icon: Icon(Icons.videogame_asset)),
                ],
                selected: {config.mode},
                onSelectionChanged: (Set<String> newSelection) {
                  Navigator.pop(context);
                  _updateAppConfig(config.app.packageName, newSelection.first);
                },
                style: ButtonStyle(
                  visualDensity: VisualDensity.compact,
                  tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                ),
              ),
              const SizedBox(height: 24),
              SizedBox(
                width: double.infinity,
                child: OutlinedButton.icon(
                  onPressed: () {
                    Navigator.pop(context);
                    _removeAppFromConfig(config.app.packageName);
                  },
                  icon: const Icon(Icons.delete_outline),
                  label: const Text("Remove from list"),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: Theme.of(context).colorScheme.error,
                    side: BorderSide(color: Theme.of(context).colorScheme.error),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      extendBody: true, // Penting agar navbar bisa floating di atas konten
      body: NotificationListener<UserScrollNotification>(
        onNotification: (notification) {
          if (notification.direction == ScrollDirection.forward) {
            if (!_isBottomBarVisible) setState(() => _isBottomBarVisible = true);
          } else if (notification.direction == ScrollDirection.reverse) {
            if (_isBottomBarVisible) setState(() => _isBottomBarVisible = false);
          }
          return true;
        },
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(24, 60, 24, 120),
          child: _selectedIndex == 0 
              ? _buildHomeContent(context, colorScheme)
              : (_selectedIndex == 1
                  ? _buildTuningContent(context, colorScheme)
                  : (_selectedIndex == 2
                      ? _buildTweaksContent(context, colorScheme)
                      : _buildAppManagerContent(context, colorScheme))),
        ),
      ),
      floatingActionButton: (_selectedIndex == 3 && _isAutdAvailable)
          ? FloatingActionButton(
              onPressed: _showAddAppSheet,
              child: const Icon(Icons.add),
            )
          : null,
      // Floating Navigation Bar
      bottomNavigationBar: AnimatedSlide(
        duration: const Duration(milliseconds: 200),
        offset: _isBottomBarVisible ? Offset.zero : const Offset(0, 1),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(40, 0, 40, 30),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(50),
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 5.0, sigmaY: 5.0),
              child: Container(
                height: 70,
                decoration: BoxDecoration(
                  color: colorScheme.surfaceVariant.withOpacity(0.3),
                  borderRadius: BorderRadius.circular(50),
                  border: Border.all(
                    color: colorScheme.outlineVariant.withOpacity(0.3),
                    width: 0.5,
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withOpacity(0.2),
                      blurRadius: 20,
                      offset: const Offset(0, 10),
                    ),
                  ],
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: [
                    _buildNavItem(context, Icons.home_rounded, "Home", 0),
                    _buildNavItem(context, Icons.tune_rounded, "Tuning", 1),
                    _buildNavItem(context, Icons.build_circle_outlined, "Tweaks", 2),
                    if (_isAutdAvailable) _buildNavItem(context, Icons.apps_rounded, "Apps", 3),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHomeContent(BuildContext context, ColorScheme colorScheme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
            // Hero Card (Main Display)
            AnimatedBuilder(
              animation: _shadowController,
              builder: (context, child) {
                final double angle = _shadowController.value * 2 * math.pi;
                final double offsetX = 10 * math.cos(angle);
                final double offsetY = 10 * math.sin(angle);

                return Container(
                  width: double.infinity,
                  height: 200,
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(
                      colors: [Color(0xFF311B92), Color(0xFF039BE5)], // Ungu Tua ke Biru Langit
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                    borderRadius: BorderRadius.circular(32),
                    boxShadow: [
                      BoxShadow(
                        color: colorScheme.primary.withOpacity(0.4),
                        blurRadius: 20,
                        offset: Offset(offsetX, offsetY),
                        spreadRadius: 1,
                      ),
                      BoxShadow(
                        color: colorScheme.tertiary.withOpacity(0.3),
                        blurRadius: 20,
                        offset: Offset(-offsetX, -offsetY),
                        spreadRadius: 1,
                      ),
                    ],
                  ),
                  child: child,
                );
              },
              child: Stack(
                children: [
                  Padding(
                    padding: const EdgeInsets.all(28.0),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          _systemInfo['model'] ?? "Unknown Device",
                          style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                                color: Colors.white,
                                fontWeight: FontWeight.bold,
                              ),
                        ),
                        const SizedBox(height: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                          decoration: BoxDecoration(
                            color: colorScheme.primary.withOpacity(0.4),
                            borderRadius: BorderRadius.circular(16),
                            border: Border.all(color: Colors.white24),
                          ),
                          child: const Text(
                            "Aozora Kernel Manager",
                            style: TextStyle(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 12),
                          ),
                        ),
                      ],
                    ),
                  ),
                  Positioned(
                    right: -20,
                    bottom: -20,
                    child: Opacity(
                      opacity: 0.25,
                      child: Image.asset(
                        'assets/icon/kai.png',
                        width: 180,
                        height: 180,
                        fit: BoxFit.contain,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Daemon Service Card
            if (_isAutdAvailable) ...[
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: _isDaemonRunning
                      ? colorScheme.primaryContainer.withOpacity(0.2)
                      : colorScheme.errorContainer.withOpacity(0.2),
                  borderRadius: BorderRadius.circular(24),
                  border: Border.all(
                    color: _isDaemonRunning ? colorScheme.primary : colorScheme.error,
                    width: 1,
                  ),
                ),
                child: Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: _isDaemonRunning ? colorScheme.primary : colorScheme.error,
                        shape: BoxShape.circle,
                      ),
                      child: Icon(
                        _isDaemonRunning ? Icons.memory : Icons.power_off,
                        color: _isDaemonRunning ? colorScheme.onPrimary : colorScheme.onError,
                        size: 24,
                      ),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            "Daemon Service",
                            style: Theme.of(context).textTheme.titleMedium?.copyWith(
                                  fontWeight: FontWeight.bold,
                                ),
                          ),
                          Text(
                            _isDaemonRunning ? "Running (autd)" : "Stopped",
                            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                                  color: colorScheme.onSurfaceVariant,
                                ),
                          ),
                        ],
                      ),
                    ),
                    Switch(
                      value: _isDaemonRunning,
                      onChanged: _isAutdAvailable ? (val) => val ? _startDaemon() : _stopDaemon() : null,
                      activeColor: colorScheme.primary,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
            ],

            // System Details Section
            Card(
              elevation: 0,
              color: colorScheme.surfaceContainer,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(28)),
              child: Padding(
                padding: const EdgeInsets.all(24.0),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      "System Details",
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                    ),
                    const SizedBox(height: 8),
                    GridView.count(
                      padding: EdgeInsets.zero,
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      crossAxisCount: 2,
                      childAspectRatio: 3.0,
                      crossAxisSpacing: 16,
                      mainAxisSpacing: 16,
                      children: [
                        _buildDetailItem(context, "Android", _systemInfo['android']!),
                        _buildDetailItem(context, "Codename", _systemInfo['device']!),
                        _buildDetailItem(context, "SELinux", _systemInfo['selinux']!),
                        _buildDetailItem(context, "SoC", _systemInfo['soc']!),
                        _buildDetailItem(context, "RAM", _systemInfo['ram']!),
                        _buildDetailItem(context, "Battery", _systemInfo['battery']!),
                        _buildDetailItem(context, "Uptime", _systemInfo['uptime']!),
                        _buildDetailItem(context, "Resolution", _systemInfo['resolution']!),
                      ],
                    ),
                    const SizedBox(height: 24),
                    Divider(
                      thickness: 0.5,
                      color: Theme.of(context).colorScheme.outlineVariant,
                    ),
                    const SizedBox(height: 16),
                    if (_isAutdAvailable) ...[
                      Text(
                        "Daemon Method",
                        style: Theme.of(context).textTheme.labelMedium?.copyWith(
                              color: colorScheme.onSurfaceVariant,
                              fontWeight: FontWeight.bold,
                            ),
                      ),
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          Icon(
                            Icons.sensors_rounded,
                            size: 16,
                            color: _daemonMethod == 'Daemon info unavailable' || _daemonMethod == 'Checking Daemon...'
                                ? colorScheme.error
                                : const Color(0xFF81C784),
                          ),
                          const SizedBox(width: 8),
                          Text(
                            _daemonMethod,
                            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                                  fontFamily: _daemonMethod == 'Daemon info unavailable' || _daemonMethod == 'Checking Daemon...'
                                      ? null
                                      : 'monospace',
                                  color: _daemonMethod == 'Daemon info unavailable' || _daemonMethod == 'Checking Daemon...'
                                      ? colorScheme.error
                                      : colorScheme.primary,
                                  fontWeight: FontWeight.bold,
                                ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      Divider(
                        thickness: 0.5,
                        color: Theme.of(context).colorScheme.outlineVariant,
                      ),
                      const SizedBox(height: 12),
                    ],
                    Text(
                      "Root Access",
                      style: Theme.of(context).textTheme.labelMedium?.copyWith(
                            color: colorScheme.onSurfaceVariant,
                            fontWeight: FontWeight.bold,
                          ),
                    ),
                    const SizedBox(height: 8),
                    SelectableText(
                      "${_systemInfo['root_manager']} ${_systemInfo['root_version']}",
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                            fontFamily: 'monospace',
                            color: colorScheme.primary,
                          ),
                    ),
                    const SizedBox(height: 12),
                    Divider(
                      thickness: 0.5,
                      color: Theme.of(context).colorScheme.outlineVariant,
                    ),
                    const SizedBox(height: 12),
                    Text(
                      "Kernel Information",
                      style: Theme.of(context).textTheme.labelMedium?.copyWith(
                            color: colorScheme.onSurfaceVariant,
                            fontWeight: FontWeight.bold,
                          ),
                    ),
                    const SizedBox(height: 8),
                    SelectableText(
                      _systemInfo['kernel'] ?? "-",
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                            fontFamily: 'monospace',
                            color: colorScheme.primary,
                          ),
                    ),
                  ],
                ),
              ),
            ),
      ],
    );
  }

  Widget _buildTuningContent(BuildContext context, ColorScheme colorScheme) {
    // Filter: Sembunyikan gaming/gaming2 jika tidak ditemukan
    final visibleProfiles = _profiles.where((profile) {
      final id = profile['id'];
      if (id == 'gaming' || id == 'gaming2') {
        return _profileAvailability[id] ?? false;
      }
      return true;
    }).toList();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          "Tuning Dashboard",
          style: Theme.of(context).textTheme.headlineLarge?.copyWith(
                fontWeight: FontWeight.bold,
                color: colorScheme.primary,
              ),
        ),
        const SizedBox(height: 24),
        GridView.builder(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: 2,
            crossAxisSpacing: 16,
            mainAxisSpacing: 16,
            childAspectRatio: 1.1,
          ),
          itemCount: visibleProfiles.length,
          itemBuilder: (context, index) {
            final profile = visibleProfiles[index];
            return _buildProfileCard(context, profile, colorScheme);
          },
        ),
      ],
    );
  }

  Widget _buildTweaksContent(BuildContext context, ColorScheme colorScheme) {
    final ramTotal = _ramStats['total'] ?? 1;
    final ramUsed = _ramStats['used'] ?? 0;
    final ramFree = _ramStats['free'] ?? 0;
    final ramPercent = (ramUsed / ramTotal).clamp(0.0, 1.0);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          "System Tweaks",
          style: Theme.of(context).textTheme.headlineLarge?.copyWith(
                fontWeight: FontWeight.bold,
                color: colorScheme.primary,
              ),
        ),
        const SizedBox(height: 24),

        // RAM Monitor Card
        Card(
          elevation: 0,
          color: colorScheme.surfaceContainer,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
          child: Padding(
            padding: const EdgeInsets.all(20.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text("RAM Status", style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
                    Material(
                      color: Colors.transparent,
                      child: Ink(
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            colors: [colorScheme.primary, colorScheme.tertiary],
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                          ),
                          borderRadius: BorderRadius.circular(12),
                          boxShadow: [
                            BoxShadow(
                              color: colorScheme.primary.withOpacity(0.3),
                              blurRadius: 8,
                              offset: const Offset(0, 4),
                            ),
                          ],
                        ),
                        child: InkWell(
                          onTap: _confirmFlushRam,
                          borderRadius: BorderRadius.circular(12),
                          child: Padding(
                            padding: const EdgeInsets.all(10.0),
                            child: Icon(
                              Icons.cleaning_services_outlined,
                              color: colorScheme.onPrimary,
                              size: 20,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: LinearProgressIndicator(
                    value: ramPercent,
                    minHeight: 12,
                    backgroundColor: colorScheme.surfaceContainerHighest,
                    color: ramPercent > 0.85 ? colorScheme.error : colorScheme.primary,
                  ),
                ),
                const SizedBox(height: 12),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text("Used: ${ramUsed}MB", style: TextStyle(color: colorScheme.onSurfaceVariant, fontWeight: FontWeight.w500)),
                    Text("Free: ${ramFree}MB", style: TextStyle(color: colorScheme.primary, fontWeight: FontWeight.bold)),
                  ],
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 24),

        // Toggles
        Text("Quick Toggles", style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
        const SizedBox(height: 12),
        _buildToggleCard(
          context,
          "Bypass Charging",
          "Stop charging while plugged in to reduce heat.",
          Icons.battery_charging_full,
          _bypassCharging,
          (val) => _toggleSystemSetting('/sys/class/power_supply/battery/input_suspend', val, (v) => _bypassCharging = v),
        ),
        if (_isAutdAvailable) ...[
          const SizedBox(height: 12),
          _buildToggleCard(
            context,
            "Optimize Game Thread",
            "Prioritize game processes for better performance.",
            Icons.games,
            _optimizeGameThread,
            (val) => _toggleSystemSetting('/data/data/com.xaozora.manager/files/autd_opt_allow', val, (v) => _optimizeGameThread = v),
          ),
        ],
      ],
    );
  }

  Widget _buildAppManagerContent(BuildContext context, ColorScheme colorScheme) {
    if (_isLoadingApps) {
      return const Center(child: CircularProgressIndicator());
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          "App Manager",
          style: Theme.of(context).textTheme.headlineLarge?.copyWith(
                fontWeight: FontWeight.bold,
                color: colorScheme.primary,
              ),
        ),
        const SizedBox(height: 24),
        if (_configuredApps.isEmpty)
          Center(
            child: Padding(
              padding: const EdgeInsets.only(top: 40),
              child: Text(
                "No apps configured",
                style: TextStyle(color: colorScheme.onSurfaceVariant),
              ),
            ),
          )
        else
          ListView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: _configuredApps.length,
            itemBuilder: (context, index) {
              final config = _configuredApps[index];
              Color badgeColor;
              String badgeText;

              switch (config.mode) {
                case 'g':
                  badgeColor = Colors.amber;
                  badgeText = "Gaming";
                  break;
                case 'g2':
                  badgeColor = Colors.redAccent;
                  badgeText = "Gaming+";
                  break;
                default:
                  badgeColor = Colors.blueAccent;
                  badgeText = "Perf";
              }

              return ListTile(
                contentPadding: EdgeInsets.zero,
                leading: config.app.icon != null
                    ? Image.memory(config.app.icon!, width: 48, height: 48)
                    : const Icon(Icons.android, size: 48),
                title: Text(config.app.name ?? config.app.packageName, style: const TextStyle(fontWeight: FontWeight.bold)),
                subtitle: Text(config.app.packageName, style: TextStyle(fontSize: 12, color: colorScheme.onSurfaceVariant)),
                trailing: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: badgeColor.withOpacity(0.2),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: badgeColor.withOpacity(0.5)),
                  ),
                  child: Text(badgeText, style: TextStyle(color: badgeColor, fontSize: 10, fontWeight: FontWeight.bold)),
                ),
                onTap: () => _showEditAppSheet(config),
              );
            },
          ),
      ],
    );
  }

  Widget _buildToggleCard(BuildContext context, String title, String subtitle, IconData icon, bool value, Function(bool) onChanged) {
    final colorScheme = Theme.of(context).colorScheme;
    return Card(
      elevation: 0,
      color: colorScheme.surfaceContainerHighest.withOpacity(0.5),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      child: SwitchListTile(
        value: value,
        onChanged: onChanged,
        title: Text(title, style: const TextStyle(fontWeight: FontWeight.bold)),
        subtitle: Text(subtitle, style: TextStyle(fontSize: 12, color: colorScheme.onSurfaceVariant)),
        secondary: Icon(icon, color: value ? colorScheme.primary : colorScheme.onSurfaceVariant),
        activeColor: colorScheme.primary,
      ),
    );
  }

  Widget _buildProfileCard(BuildContext context, Map<String, dynamic> profile, ColorScheme colorScheme) {
    String id = profile['id'];
    bool exists = _profileAvailability[id] ?? false;
    bool isProcessing = _processingProfile == id;
    bool isActive = _activeProfileId == id;

    return Opacity(
      opacity: exists ? 1.0 : 0.5,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: exists && !isProcessing ? () => _executeProfile(id) : null,
          borderRadius: BorderRadius.circular(28),
          child: Ink(
            decoration: BoxDecoration(
              color: colorScheme.surfaceVariant,
              borderRadius: BorderRadius.circular(28),
              border: Border.all(
                color: isProcessing || isActive ? colorScheme.primary : colorScheme.outlineVariant,
                width: isProcessing || isActive ? 2 : 1,
              ),
              boxShadow: isProcessing || isActive
                  ? [
                      BoxShadow(
                        color: colorScheme.primary.withOpacity(0.3),
                        blurRadius: 12,
                        spreadRadius: 2,
                      )
                    ]
                  : [],
            ),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                if (isProcessing)
                  SizedBox(
                    width: 32,
                    height: 32,
                    child: CircularProgressIndicator(
                      color: colorScheme.secondary,
                      strokeWidth: 3,
                    ),
                  )
                else
                  Icon(
                    exists ? profile['icon'] : Icons.error_outline,
                    size: 40,
                    color: exists ? (isActive ? colorScheme.primary : colorScheme.onSurfaceVariant) : colorScheme.error,
                  ),
                const SizedBox(height: 16),
                Text(
                  exists ? profile['name'] : 'Not Found',
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                        color: exists ? colorScheme.onSurfaceVariant : colorScheme.error,
                      ),
                ),
                if (exists && id != 'cachecleaner' && _isAutdAvailable)
                  Text(
                    isActive ? "ACTIVE" : "Tap to activate",
                    style: Theme.of(context).textTheme.labelSmall?.copyWith(
                          fontFamily: isActive ? 'monospace' : null,
                          color: isActive ? colorScheme.primary : colorScheme.outline,
                          fontWeight: isActive ? FontWeight.bold : FontWeight.normal,
                        ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildStatusCard(BuildContext context, {required String title, required String value, bool isSubtitle = false}) {
    final colorScheme = Theme.of(context).colorScheme;
    return Container(
      height: 100,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: colorScheme.surfaceContainerHighest.withOpacity(0.4),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: Colors.white.withOpacity(0.05)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(
            title,
            style: Theme.of(context).textTheme.labelMedium?.copyWith(
                  color: colorScheme.onSurfaceVariant,
                  fontWeight: FontWeight.bold,
                ),
          ),
          const SizedBox(height: 4),
          Text(
            value,
            style: isSubtitle
                ? Theme.of(context).textTheme.bodySmall?.copyWith(color: colorScheme.onSurface)
                : Theme.of(context).textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: colorScheme.onSurface,
                    ),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }

  Widget _buildDetailItem(BuildContext context, String label, String value) {
    final colorScheme = Theme.of(context).colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Text(
          label,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: colorScheme.onSurfaceVariant,
              ),
        ),
        const SizedBox(height: 4),
        Text(
          value,
          style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                fontFamily: 'monospace', // Tampilan seperti terminal
                color: colorScheme.primary,
                fontWeight: FontWeight.bold,
              ),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
      ],
    );
  }

  Widget _buildNavItem(BuildContext context, IconData icon, String label, int index) {
    final isSelected = _selectedIndex == index;
    final colorScheme = Theme.of(context).colorScheme;

    return GestureDetector(
      onTap: () => setState(() => _selectedIndex = index),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: EdgeInsets.symmetric(horizontal: isSelected ? 20 : 16, vertical: 12),
        decoration: isSelected
            ? BoxDecoration(
                color: colorScheme.primaryContainer,
                borderRadius: BorderRadius.circular(30),
              )
            : null,
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
}

class _AddAppSheetContent extends StatefulWidget {
  final ScrollController scrollController;
  final Function(String) onAppSelected;

  const _AddAppSheetContent({required this.scrollController, required this.onAppSelected});

  @override
  State<_AddAppSheetContent> createState() => _AddAppSheetContentState();
}

class _AddAppSheetContentState extends State<_AddAppSheetContent> {
  List<AppInfo> _allApps = [];
  List<AppInfo> _filteredApps = [];
  bool _loading = true;
  final TextEditingController _searchController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _loadApps();
    _searchController.addListener(_filterApps);
  }

  Future<void> _loadApps() async {
    final apps = await InstalledApps.getInstalledApps(
      excludeSystemApps: false,
      excludeNonLaunchableApps: true,
      withIcon: true,
    );
    if (mounted) {
      setState(() {
        _allApps = apps;
        _filteredApps = _allApps;
        _loading = false;
      });
    }
  }

  void _filterApps() {
    final query = _searchController.text.toLowerCase();
    setState(() {
      _filteredApps = _allApps.where((app) {
        return (app.name?.toLowerCase().contains(query) ?? false) || app.packageName.toLowerCase().contains(query);
      }).toList();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(16.0),
          child: TextField(
            controller: _searchController,
            decoration: const InputDecoration(
              labelText: 'Search App',
              prefixIcon: Icon(Icons.search),
              border: OutlineInputBorder(),
            ),
          ),
        ),
        Expanded(
          child: _loading
              ? const Center(child: CircularProgressIndicator())
              : ListView.builder(
                  controller: widget.scrollController,
                  itemCount: _filteredApps.length,
                  itemBuilder: (context, index) {
                    final app = _filteredApps[index];
                    return ListTile(
                      leading: app.icon != null
                          ? Image.memory(app.icon!, width: 40, height: 40)
                          : const Icon(Icons.android, size: 40),
                      title: Text(app.name ?? app.packageName),
                      subtitle: Text(app.packageName),
                      onTap: () => widget.onAppSelected(app.packageName),
                    );
                  },
                ),
        ),
      ],
    );
  }
}
