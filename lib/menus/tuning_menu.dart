import 'dart:async';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class TuningMenu extends StatefulWidget {
  const TuningMenu({super.key});

  @override
  State<TuningMenu> createState() => _TuningMenuState();
}

class _TuningMenuState extends State<TuningMenu> {
  static const platform = MethodChannel('com.xaozora.manager/daemon');

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
  String? _activeProfileId;
  bool _isAutdAvailable = false;
  Timer? _tuningTimer;

  @override
  void initState() {
    super.initState();
    _checkAutdAvailability();
    _checkProfiles();
    _tuningTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (mounted) _checkActiveProfile();
    });
  }

  @override
  void dispose() {
    _tuningTimer?.cancel();
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
    if (mounted) {
      setState(() {
        _profileAvailability = availability;
      });
    }
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

  Future<void> _executeProfile(String id) async {
    setState(() => _processingProfile = id);
    try {
      if (id == 'cachecleaner') {
        await platform.invokeMethod('executeScript', {'script': '/system/bin/cachecleaner'});
      } else {
        if (_isAutdAvailable) {
          String cmd =
              'echo "$id" > /data/data/com.xaozora.manager/files/autd_base_mode; echo "$id" > /data/data/com.xaozora.manager/files/autd_status';
          await platform.invokeMethod('executeScript', {'script': cmd});
          setState(() => _activeProfileId = id);
        } else {
          await platform.invokeMethod(
              'executeScript', {'script': '/system/bin/$id'});
        }
      }
      if (!mounted) return;
      if (id == 'cachecleaner') {
        _showGlassSnackBar('Cache cleaned successfully!');
      } else {
        _showGlassSnackBar('Profile $id applied successfully');
      }
    } catch (e) {
      if (!mounted) return;
      _showGlassSnackBar('Failed to apply $id: $e', isError: true);
    } finally {
      if (mounted) setState(() => _processingProfile = null);
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

  Widget _buildProfileCard(BuildContext context, Map<String, dynamic> profile, ColorScheme colorScheme) {
    String id = profile['id'];
    bool exists = _profileAvailability[id] ?? false;
    bool isProcessing = _processingProfile == id;
    bool isActive = _activeProfileId == id;

 return Opacity(
      opacity: exists ? 1.0 : 0.5,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
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
        child: Material(
          color: Colors.transparent,
          borderRadius: BorderRadius.circular(28),
          child: InkWell(
            onTap: exists && !isProcessing ? () => _executeProfile(id) : null,
            borderRadius: BorderRadius.circular(28),
            child: Padding(
              padding: const EdgeInsets.all(16.0),
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
      ),
    );
  }
}