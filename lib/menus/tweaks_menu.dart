import 'dart:async';
import 'dart:ui';
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
      const cmd = r'''
        for P in $(pidof com.xaozora.manager); do 
            echo -1000 > /proc/$P/oom_score_adj 2>/dev/null; 
        done;

        for p in /proc/[0-9]*; do
            read oom < "$p/oom_score_adj" 2>/dev/null
            [ "${oom:-0}" -ge 500 ] && echo "${p##*/}"
        done | xargs -r kill -9 2>/dev/null;

        sync; 
        echo 3 > /proc/sys/vm/drop_caches;
        [ -f /proc/sys/vm/compact_memory ] && echo 1 > /proc/sys/vm/compact_memory;

        (
          pm list packages -3 | cut -d':' -f2 | grep -v "com.xaozora.manager" | while read -r app; do
              am force-stop "$app"
          done;
          fstrim -v /data;
        ) &
      ''';
      await platform.invokeMethod('executeScript', {'script': cmd});
      
      if (!mounted) return;
      _showGlassSnackBar('FLush Ram & Cache Cleared Successfully!');
      Future.delayed(const Duration(milliseconds: 1500), _fetchRamStats);
      
    } catch (e) {
      if (!mounted) return;
      _showGlassSnackBar('Failed to flush RAM', isError: true);
    }
  }

  Future<void> _confirmFlushRam() async {
    final colorScheme = Theme.of(context).colorScheme;
    final bool? confirm = await showDialog<bool>(
      context: context,
      builder: (BuildContext context) {
        return Dialog(
          backgroundColor: Colors.transparent,
          elevation: 0,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(28),
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 20.0, sigmaY: 20.0),
              child: Container(
                padding: const EdgeInsets.all(24),
                decoration: BoxDecoration(
                  color: colorScheme.surfaceContainer.withOpacity(0.25),
                  borderRadius: BorderRadius.circular(28),
                  border: Border.all(
                    color: Colors.white.withOpacity(0.2),
                    width: 1,
                  ),
                  gradient: LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [
                      Colors.white.withOpacity(0.1),
                      Colors.transparent,
                    ],
                  ),
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Flush RAM?',
                      style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                            fontWeight: FontWeight.bold,
                            color: colorScheme.onSurface,
                          ),
                    ),
                    const SizedBox(height: 16),
                    Text(
                      'This will clear system cache and trim storage.',
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                            color: colorScheme.onSurfaceVariant,
                          ),
                    ),
                    const SizedBox(height: 24),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        TextButton(
                          onPressed: () => Navigator.of(context).pop(false),
                          child: const Text('Cancel'),
                        ),
                        const SizedBox(width: 8),
                        FilledButton(
                          onPressed: () => Navigator.of(context).pop(true),
                          style: FilledButton.styleFrom(
                            backgroundColor: colorScheme.primaryContainer.withOpacity(0.25),
                            foregroundColor: colorScheme.onPrimaryContainer,
                            elevation: 0,
                            shadowColor: Colors.transparent,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(30),
                              side: BorderSide(
                                color: colorScheme.primary.withOpacity(0.4),
                                width: 1,
                              ),
                            ),
                          ),
                          child: const Text('Flush'),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
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
      _showGlassSnackBar('Failed to apply setting', isError: true);
    }
  }

  void _showGlassSnackBar(String message, {bool isError = false}) {
    final colorScheme = Theme.of(context).colorScheme;
    ScaffoldMessenger.of(context).clearSnackBars();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        behavior: SnackBarBehavior.floating,
        content: ClipRRect(
          borderRadius: BorderRadius.circular(18),
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 20.0, sigmaY: 20.0),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
              decoration: BoxDecoration(
                color: (isError ? colorScheme.errorContainer : colorScheme.primaryContainer).withOpacity(0.3),
                borderRadius: BorderRadius.circular(18),
                border: Border.all(
                  color: (isError ? colorScheme.error : colorScheme.primary).withOpacity(0.5),
                  width: 1,
                ),
              ),
              child: Text(
                message,
                style: TextStyle(
                  color: isError ? colorScheme.onErrorContainer : colorScheme.onPrimaryContainer,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ),
        ),
      ),
    );
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