import 'dart:async';
import 'dart:math' as math;
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class HomeMenu extends StatefulWidget {
  const HomeMenu({super.key});

  @override
  State<HomeMenu> createState() => _HomeMenuState();
}

class _HomeMenuState extends State<HomeMenu> with SingleTickerProviderStateMixin {
  static const platform = MethodChannel('com.xaozora.manager/daemon');

  // system info state
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

  bool _isDaemonRunning = false;
  String _daemonMethod = 'Checking Daemon...';
  Timer? _daemonMethodTimer;
  bool _isAutdAvailable = false;

  late final AnimationController _shadowController;

  @override
  void initState() {
    super.initState();
    _fetchSystemInfo();
    _checkDaemonStatus();
    _checkAutdAvailability();
    
    _daemonMethodTimer = Timer.periodic(const Duration(seconds: 2), (timer) {
      _checkDaemonMethod();
    });

    _shadowController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 4),
    )..repeat();
  }

  @override
  void dispose() {
    _shadowController.dispose();
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
      // ignore
    }
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

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
            // hero card
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
                      colors: [Color(0xFF311B92), Color(0xFF039BE5)],
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
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(
                          "Running on",
                          style: Theme.of(context).textTheme.labelLarge?.copyWith(
                                color: Colors.white.withOpacity(0.7),
                                letterSpacing: 1.2,
                              ),
                        ),
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

            // daemon service card
            if (_isAutdAvailable) ...[
              ClipRRect(
                borderRadius: BorderRadius.circular(24),
                child: BackdropFilter(
                  filter: ImageFilter.blur(sigmaX: _isDaemonRunning ? 20.0 : 0.0, sigmaY: _isDaemonRunning ? 20.0 : 0.0),
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    decoration: BoxDecoration(
                      color: _isDaemonRunning
                          ? colorScheme.primaryContainer.withOpacity(0.25)
                          : colorScheme.surfaceContainer,
                      borderRadius: BorderRadius.circular(24),
                      border: Border.all(
                        color: _isDaemonRunning ? colorScheme.primary.withOpacity(0.4) : colorScheme.outlineVariant.withOpacity(0.5),
                        width: 1.2,
                      ),
                    ),
                    child: Material(
                      color: Colors.transparent,
                      child: InkWell(
                        borderRadius: BorderRadius.circular(24),
                        onTap: _isAutdAvailable ? () => _isDaemonRunning ? _stopDaemon() : _startDaemon() : null,
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Row(
                            children: [
                              Container(
                                padding: const EdgeInsets.all(12),
                                decoration: BoxDecoration(
                                  color: _isDaemonRunning ? colorScheme.primary.withOpacity(0.2) : colorScheme.error.withOpacity(0.2),
                                  shape: BoxShape.circle,
                                ),
                                child: Icon(
                                  _isDaemonRunning ? Icons.memory : Icons.power_off,
                                  color: _isDaemonRunning ? colorScheme.primary : colorScheme.error,
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
                                            color: _isDaemonRunning ? colorScheme.onPrimaryContainer : colorScheme.onSurface,
                                          ),
                                    ),
                                    const SizedBox(height: 4),
                                    Text(
                                      _isDaemonRunning ? "Running (autd)" : "Stopped",
                                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                                            color: _isDaemonRunning ? colorScheme.onPrimaryContainer.withOpacity(0.7) : colorScheme.onSurfaceVariant,
                                          ),
                                    ),
                                  ],
                                ),
                              ),
                              // Custom Glass Toggle Indicator
                              AnimatedContainer(
                                duration: const Duration(milliseconds: 200),
                                width: 52,
                                height: 28,
                                padding: const EdgeInsets.all(4),
                                decoration: BoxDecoration(
                                  color: _isDaemonRunning ? colorScheme.primary : colorScheme.surfaceContainerHighest,
                                  borderRadius: BorderRadius.circular(30),
                                  border: Border.all(
                                    color: _isDaemonRunning ? colorScheme.primary : colorScheme.outlineVariant.withOpacity(0.5),
                                  ),
                                ),
                                alignment: _isDaemonRunning ? Alignment.centerRight : Alignment.centerLeft,
                                child: Container(
                                  width: 20,
                                  height: 20,
                                  decoration: BoxDecoration(
                                    shape: BoxShape.circle,
                                    color: _isDaemonRunning ? colorScheme.onPrimary : colorScheme.outline,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 24),
            ],

            Text(
              "System Dashboard",
              style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: colorScheme.onSurface,
                  ),
            ),
            const SizedBox(height: 16),

            // Quick Stats Row
            Row(
              children: [
                Expanded(child: _buildQuickStatCard(context, "Battery", _systemInfo['battery']!, Icons.battery_charging_full_rounded, const Color(0xFF4CAF50))),
                const SizedBox(width: 12),
                Expanded(child: _buildQuickStatCard(context, "RAM", _systemInfo['ram']!, Icons.memory_rounded, colorScheme.primary)),
                const SizedBox(width: 12),
                Expanded(child: _buildQuickStatCard(context, "Uptime", _systemInfo['uptime']!, Icons.timer_rounded, const Color(0xFFFF9800))),
              ],
            ),
            const SizedBox(height: 16),

            // Hardware Info Grid
            GridView.count(
              padding: EdgeInsets.zero,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              crossAxisCount: 2,
              childAspectRatio: 2.6,
              crossAxisSpacing: 12,
              mainAxisSpacing: 12,
              children: [
                _buildInfoTile(context, "Android", _systemInfo['android']!, Icons.android_rounded, color: const Color(0xFF81C784)),
                _buildInfoTile(context, "Codename", _systemInfo['device']!, Icons.smartphone_rounded),
                _buildInfoTile(context, "SoC", _systemInfo['soc']!, Icons.developer_board_rounded),
                _buildInfoTile(context, "Display", _systemInfo['resolution']!, Icons.screenshot_rounded),
              ],
            ),
            const SizedBox(height: 16),

            // Root & Security
            Row(
              children: [
                Expanded(child: _buildInfoTile(context, "Root Access", "${_systemInfo['root_manager']} ${_systemInfo['root_version']}", Icons.admin_panel_settings_rounded, isMonospace: true)),
                const SizedBox(width: 12),
                Expanded(child: _buildInfoTile(context, "SELinux", _systemInfo['selinux']!, Icons.security_rounded, color: _systemInfo['selinux'] == 'Enforcing' ? const Color(0xFF4CAF50) : colorScheme.error)),
              ],
            ),
            const SizedBox(height: 16),

            if (_isAutdAvailable) ...[
              _buildFullWidthCard(
                context,
                "Daemon Method",
                _daemonMethod,
                Icons.sensors_rounded,
                isError: _daemonMethod == 'Daemon info unavailable' || _daemonMethod == 'Checking Daemon...',
              ),
              const SizedBox(height: 12),
            ],

            _buildFullWidthCard(
              context,
              "Kernel Information",
              _systemInfo['kernel'] ?? "-",
              Icons.settings_system_daydream_rounded,
            ),
      ],
    );
  }

  Widget _buildQuickStatCard(BuildContext context, String title, String value, IconData icon, Color iconColor) {
    final colorScheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: colorScheme.surfaceContainer,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: colorScheme.outlineVariant.withOpacity(0.5)),
      ),
      child: Column(
        children: [
          Icon(icon, color: iconColor, size: 28),
          const SizedBox(height: 12),
          Text(
            value,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.bold,
                  fontFamily: 'monospace',
                ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 4),
          Text(
            title,
            style: Theme.of(context).textTheme.labelSmall?.copyWith(
                  color: colorScheme.onSurfaceVariant,
                ),
          ),
        ],
      ),
    );
  }

  Widget _buildInfoTile(BuildContext context, String title, String value, IconData icon, {Color? color, bool isMonospace = false}) {
    final colorScheme = Theme.of(context).colorScheme;
    final activeColor = color ?? colorScheme.primary;
    
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: colorScheme.surfaceContainerHighest.withOpacity(0.4),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: activeColor.withOpacity(0.2),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(icon, size: 18, color: activeColor),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  title,
                  style: Theme.of(context).textTheme.labelSmall?.copyWith(
                        color: colorScheme.onSurfaceVariant,
                      ),
                ),
                const SizedBox(height: 2),
                Text(
                  value,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        fontWeight: FontWeight.bold,
                        fontFamily: isMonospace ? 'monospace' : null,
                      ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFullWidthCard(BuildContext context, String title, String value, IconData icon, {bool isError = false}) {
    final colorScheme = Theme.of(context).colorScheme;
    final activeColor = isError ? colorScheme.error : colorScheme.primary;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: colorScheme.surfaceContainer,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: colorScheme.outlineVariant.withOpacity(0.5)),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: activeColor.withOpacity(0.2),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(icon, color: activeColor, size: 24),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: Theme.of(context).textTheme.labelMedium?.copyWith(
                        color: colorScheme.onSurfaceVariant,
                      ),
                ),
                const SizedBox(height: 4),
                SelectableText(
                  value,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                        fontFamily: 'monospace',
                        color: activeColor,
                      ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}