import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:manager/theme_manager.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:url_launcher/url_launcher.dart';

class AboutMenu extends StatefulWidget {
  const AboutMenu({super.key});

  @override
  State<AboutMenu> createState() => _AboutMenuState();
}

class _AboutMenuState extends State<AboutMenu> {
  String _appVersion = "Loading...";
  bool _isAutdAvailable = false;
  String _autdVersionStr = "";

  @override
  void initState() {
    super.initState();
    _loadInfo();
  }

  Future<void> _loadInfo() async {
    final info = await PackageInfo.fromPlatform();
    const platform = MethodChannel('com.xaozora.manager/daemon');
    final autdExists = await platform.invokeMethod('checkFileExists', {'path': '/system/bin/autd'});
    
    String autdVer = "";
    if (autdExists) {
      final sha = await platform.invokeMethod('executeScript', {'script': 'sha256sum /system/bin/autd | head -c 7'});
      autdVer = (sha != null && sha.toString().trim().isNotEmpty) ? sha.toString().trim() : "Unknown";
    }
    
    if (mounted) {
      setState(() {
        _appVersion = "${info.version}+${info.buildNumber}";
        _isAutdAvailable = autdExists;
        _autdVersionStr = autdVer;
      });
    }
  }

  Future<void> _launchGitHub(BuildContext context) async {
    final Uri url = Uri.parse('https://github.com/xMikkkaa');
    try {
      if (!await launchUrl(url, mode: LaunchMode.externalApplication)) {
        throw Exception('Could not launch url');
      }
    } catch (e) {
      // Fallback copy to clipboard jika device tidak memiliki browser default
      await Clipboard.setData(const ClipboardData(text: 'https://github.com/xMikkkaa'));
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Text('Gagal membuka browser. URL disalin ke clipboard!'),
            backgroundColor: Theme.of(context).colorScheme.secondary,
            behavior: SnackBarBehavior.floating,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          ),
        );
      }
    }
  }

  Widget _buildThemeItem(BuildContext context, AozoraThemeManager? themeManager, ThemeMode mode, IconData icon, String label) {
    final colorScheme = Theme.of(context).colorScheme;
    final isSelected = themeManager?.themeMode == mode;

    return GestureDetector(
      onTap: () {
        if (themeManager != null && !isSelected) {
          themeManager.setThemeMode(mode);
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
          mainAxisSize: MainAxisSize.min,
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

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final themeManager = AozoraThemeManager.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          "About",
          style: Theme.of(context).textTheme.headlineLarge?.copyWith(
                fontWeight: FontWeight.bold,
                color: colorScheme.primary,
              ),
        ),
        const SizedBox(height: 24),
        
        // Developer Profile Card
        Card(
          elevation: 0,
          color: colorScheme.surfaceContainer,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(28),
            side: BorderSide(color: colorScheme.outlineVariant.withOpacity(0.5)),
          ),
          child: Padding(
            padding: const EdgeInsets.all(32.0),
            child: Column(
              children: [
                // Avatar with Gradient Border
                Container(
                  padding: const EdgeInsets.all(4),
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: LinearGradient(
                      colors: [colorScheme.primary, colorScheme.tertiary],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: colorScheme.primary.withOpacity(0.3),
                        blurRadius: 15,
                        offset: const Offset(0, 5),
                      ),
                    ],
                  ),
                  child: const CircleAvatar(
                    radius: 56,
                    backgroundImage: NetworkImage('https://avatars.githubusercontent.com/xMikkkaa'),
                    backgroundColor: Colors.transparent,
                  ),
                ),
                const SizedBox(height: 20),
                
                // Name & Role
                Text(
                  "xMikkkaa",
                  style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                        fontWeight: FontWeight.bold,
                        color: colorScheme.onSurface,
                      ),
                ),
                const SizedBox(height: 6),
                Text(
                  "LIP",
                  style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                        color: colorScheme.primary,
                        fontWeight: FontWeight.w600,
                      ),
                ),
                const SizedBox(height: 16),
                
                // Description
                Text(
                  "Creator of Aozora Kernel Manager and Automation Daemon.",
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: colorScheme.onSurfaceVariant,
                        height: 1.5,
                      ),
                ),
                const SizedBox(height: 28),
                
                // Action Button
                FilledButton.icon(
                  onPressed: () => _launchGitHub(context),
                  icon: const Icon(Icons.code_rounded),
                  label: const Text("View GitHub Profile"),
                  style: FilledButton.styleFrom(
                    padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 24),

        // Appearance Card
        Text(
          "Appearance",
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
        ),
        const SizedBox(height: 12),
        ClipRRect(
          borderRadius: BorderRadius.circular(50),
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 20.0, sigmaY: 20.0),
            child: Container(
              height: 70,
              padding: const EdgeInsets.symmetric(horizontal: 8),
              decoration: BoxDecoration(
                color: colorScheme.surfaceContainer.withOpacity(0.25),
                borderRadius: BorderRadius.circular(50),
                border: Border.all(
                  color: colorScheme.outlineVariant.withOpacity(0.5),
                  width: 1.2,
                ),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: [
                  _buildThemeItem(context, themeManager, ThemeMode.system, Icons.brightness_auto_outlined, "Auto"),
                  _buildThemeItem(context, themeManager, ThemeMode.light, Icons.light_mode_outlined, "Light"),
                  _buildThemeItem(context, themeManager, ThemeMode.dark, Icons.dark_mode_outlined, "Dark"),
                ],
              ),
            ),
          ),
        ),
        const SizedBox(height: 24),
        
        // App Info Footer
        Center(
          child: Column(
            children: [
              Text(
                "Aozora Kernel Manager",
                style: TextStyle(color: colorScheme.onSurfaceVariant, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 4),
              Text(
                "v$_appVersion",
                style: TextStyle(color: colorScheme.onSurfaceVariant.withOpacity(0.7), fontSize: 12),
              ),
              if (_isAutdAvailable) ...[
                const SizedBox(height: 6),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: colorScheme.primary.withOpacity(0.15),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    "AUTD: $_autdVersionStr",
                    style: TextStyle(color: colorScheme.primary, fontSize: 11, fontWeight: FontWeight.bold, fontFamily: 'monospace'),
                  ),
                ),
              ],
            ],
          ),
        ),
        const SizedBox(height: 24),
      ],
    );
  }
}