import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:installed_apps/app_info.dart';
import 'package:installed_apps/installed_apps.dart';

class ConfiguredApp {
  final AppInfo app;
  final String mode; // 'p', 'g', 'g2'
  ConfiguredApp(this.app, this.mode);
}

class AppManagerMenu extends StatefulWidget {
  const AppManagerMenu({super.key});

  @override
  State<AppManagerMenu> createState() => AppManagerMenuState();
}

class AppManagerMenuState extends State<AppManagerMenu> {
  static const platform = MethodChannel('com.xaozora.manager/daemon');
  List<ConfiguredApp> _configuredApps = [];
  bool _isLoadingApps = false;

  @override
  void initState() {
    super.initState();
    _fetchConfiguredApps();
  }

  // read configured apps
  Future<void> _fetchConfiguredApps() async {
    setState(() => _isLoadingApps = true);
    try {
      final String content = await platform.invokeMethod('readSystemFile', {'path': '/data/data/com.xaozora.manager/files/applist'});
      final List<String> lines = content.split('\n');
      List<ConfiguredApp> apps = [];

      // parse each line
      for (String line in lines) {
        line = line.trim();
        if (line.isEmpty) continue;

        String mode = 'p';
        String packageName = line;

        // check mode suffix
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

  // add app to config
  Future<void> _addAppToConfig(String packageName) async {
    try {
      final cmd = "echo \"${packageName}_p\" >> /data/data/com.xaozora.manager/files/applist";
      await platform.invokeMethod('executeScript', {'script': cmd});
      _fetchConfiguredApps();
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Failed to add app')));
    }
  }

  // update app mode
  Future<void> _updateAppConfig(String packageName, String newMode) async {
    try {
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

  // show add app sheet
  void showAddAppSheet() {
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
              existingApps: _configuredApps.map((e) => e.app.packageName).toList(),
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

  // show edit app sheet
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
                style: const ButtonStyle(
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
              
              // set badge color
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
}

class _AddAppSheetContent extends StatefulWidget {
  final ScrollController scrollController;
  final List<String> existingApps;
  final Function(String) onAppSelected;

  const _AddAppSheetContent({required this.scrollController, required this.existingApps, required this.onAppSelected});

  @override
  State<_AddAppSheetContent> createState() => _AddAppSheetContentState();
}

class _AddAppSheetContentState extends State<_AddAppSheetContent> {
  List<AppInfo> _allApps = [];
  List<AppInfo> _filteredApps = [];
  bool _loading = true;
  final TextEditingController _searchController = TextEditingController();
  final Map<String, Uint8List> _iconCache = {};

  @override
  void initState() {
    super.initState();
    _loadApps();
    _searchController.addListener(_filterApps);
  }

  // load all apps
  Future<void> _loadApps() async {
    final apps = await InstalledApps.getInstalledApps(
      excludeSystemApps: true,
      excludeNonLaunchableApps: true,
      withIcon: false,
    );
    if (mounted) {
      setState(() {
        _allApps = apps.where((app) => !widget.existingApps.contains(app.packageName)).toList();
        _filteredApps = _allApps;
        _loading = false;
      });
      _preloadIcons();
    }
  }

  // preload icons
  Future<void> _preloadIcons() async {
    for (final app in _allApps) {
      if (!mounted) return;
      try {
        final info = await InstalledApps.getAppInfo(app.packageName);
        if (info?.icon != null) {
          _iconCache[app.packageName] = info!.icon!;
          if (mounted) setState(() {});
        }
      } catch (e) {
        // ignore
      }
      await Future.delayed(Duration.zero);
    }
  }

  // filter apps
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
                      leading: _iconCache.containsKey(app.packageName)
                          ? Image.memory(_iconCache[app.packageName]!, width: 40, height: 40)
                          : const SizedBox(
                              width: 40,
                              height: 40,
                              child: Padding(
                                padding: EdgeInsets.all(8.0),
                                child: CircularProgressIndicator(strokeWidth: 2),
                              ),
                            ),
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