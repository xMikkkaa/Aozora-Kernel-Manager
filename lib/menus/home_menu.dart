import 'dart:async';
import 'dart:math' as math;
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

            // daemon service card
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

            // system details
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
                fontFamily: 'monospace',
                color: colorScheme.primary,
                fontWeight: FontWeight.bold,
              ),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
      ],
    );
  }
}