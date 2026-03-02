import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class TweaksMenu extends StatefulWidget {
  const TweaksMenu({super.key});

  @override
  State<TweaksMenu> createState() => _TweaksMenuState();
}

class _TweaksMenuState extends State<TweaksMenu> {
  static const platform = MethodChannel('com.xaozora.manager/daemon');

  Timer? _ramTimer;
  Map<String, int> _ramStats = {'total': 0, 'used': 0, 'free': 0};
  bool _bypassCharging = false;
  bool _optimizeGameThread = false;
  bool _isAutdAvailable = false;

  @override
  void initState() {
    super.initState();
    _checkAutdAvailability();
    _checkToggles();
    _fetchRamStats();
    _ramTimer = Timer.periodic(const Duration(seconds: 2), (timer) {
      if (mounted) _fetchRamStats();
    });
  }

  @override
  void dispose() {
    _ramTimer?.cancel();
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
      const cmd = "for P in \$(pidof com.xaozora.manager); do echo -1000 > /proc/\$P/oom_score_adj; done; am kill-all; sync; echo 3 > /proc/sys/vm/drop_caches; [ -f /proc/sys/vm/compact_memory ] && echo 1 > /proc/sys/vm/compact_memory; fstrim -v /data";
      await platform.invokeMethod('executeScript', {'script': cmd});
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('RAM Flushed & Storage Trimmed')),
      );
      Future.delayed(const Duration(seconds: 1), _fetchRamStats);
    } catch (e) {
      if (!mounted) return;
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

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
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

        // ram monitor
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

        // toggles
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
}