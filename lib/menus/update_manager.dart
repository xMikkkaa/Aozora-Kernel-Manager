import 'dart:io';
import 'dart:ui';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

class UpdateManager {
  static const platform = MethodChannel('com.xaozora.manager/daemon');
  static final Dio _dio = Dio();

  static Future<void> checkAndUpdate(BuildContext context) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final autoCheck = prefs.getBool('auto_check_updates') ?? true;
      if (!autoCheck) return;

      bool appNeedsUpdate = false;
      bool autdNeedsUpdate = false;
      String? latestAppUrl;
      String? latestAutdUrl;
      String newAppVersion = "";
      String newAutdVersion = "";
      String appReleaseNotes = "";
      String autdReleaseNotes = "";

      // 1. Cek App Update
      final packageInfo = await PackageInfo.fromPlatform();
      final currentAppVersion = packageInfo.version;

      try {
        final appResp = await _dio.get('https://api.github.com/repos/xMikkkaa/Aozora-Kernel-Manager/releases/latest');
        newAppVersion = appResp.data['tag_name'].toString().replaceAll('v', '');
        appReleaseNotes = appResp.data['body']?.toString() ?? "No release notes provided.";
        if (_isVersionGreater(newAppVersion, currentAppVersion)) {
          appNeedsUpdate = true;
          final assets = appResp.data['assets'] as List;
          final apkAsset = assets.firstWhere((a) => a['name'].toString().endsWith('.apk'), orElse: () => null);
          if (apkAsset != null) latestAppUrl = apkAsset['browser_download_url'];
        }
      } catch (e) { /* Abaikan jika repo private / limit */ }

      // 2. Cek AUTD Update (Hanya jika terinstall)
      final bool autdExists = await platform.invokeMethod('checkFileExists', {'path': '/system/bin/autd'});
      if (autdExists) {
        try {
          final autdResp = await _dio.get('https://api.github.com/repos/xMikkkaa/Automation-Daemon/releases/latest');
          final assets = autdResp.data['assets'] as List;
          final shaAsset = assets.firstWhere((a) => a['name'] == 'autd.sha256', orElse: () => null);
          final autdAsset = assets.firstWhere((a) => a['name'] == 'autd', orElse: () => null);

          if (shaAsset != null && autdAsset != null) {
            newAutdVersion = autdResp.data['tag_name'].toString().replaceAll('v', '');
            autdReleaseNotes = autdResp.data['body']?.toString() ?? "No release notes provided.";
            final shaResp = await _dio.get(shaAsset['browser_download_url']);
            final expectedSha = shaResp.data.toString().trim().split(' ').first;

            await platform.invokeMethod('executeScript', {'script': 'sha256sum /system/bin/autd | awk \'{print \$1}\' > /data/local/tmp/autd_sha'});
            final localShaStr = await platform.invokeMethod('readSystemFile', {'path': '/data/local/tmp/autd_sha'});
            final localSha = localShaStr?.toString().trim() ?? '';

            if (localSha != expectedSha && localSha.isNotEmpty) {
              autdNeedsUpdate = true;
              latestAutdUrl = autdAsset['browser_download_url'];
            } else if (localSha == expectedSha && localSha.isNotEmpty) {
              await platform.invokeMethod('writeSystemFile', {'path': '/data/data/com.xaozora.manager/files/autd_version', 'value': newAutdVersion});
            }
          }
        } catch (e) { /* Abaikan */ }
      }

      // 3. Tampilkan Modal jika ada update
      if ((appNeedsUpdate && latestAppUrl != null) || (autdNeedsUpdate && latestAutdUrl != null)) {
        if (context.mounted) {
          _showUpdateModal(context, appNeedsUpdate, autdNeedsUpdate, latestAppUrl, latestAutdUrl, newAppVersion, newAutdVersion, appReleaseNotes, autdReleaseNotes);
        }
      }
    } catch (e) {
      // Fail silently for background checks
    }
  }

  static bool _isVersionGreater(String newVer, String oldVer) {
    List<int> v1 = newVer.split('.').map((s) => int.tryParse(s) ?? 0).toList();
    List<int> v2 = oldVer.split('.').map((s) => int.tryParse(s) ?? 0).toList();
    for (int i = 0; i < 3; i++) {
      int n1 = i < v1.length ? v1[i] : 0;
      int n2 = i < v2.length ? v2[i] : 0;
      if (n1 > n2) return true;
      if (n1 < n2) return false;
    }
    return false;
  }

  static void _showUpdateModal(BuildContext context, bool updateApp, bool updateAutd, String? appUrl, String? autdUrl, String newAppVer, String newAutdVer, String appNotes, String autdNotes) {
    final colorScheme = Theme.of(context).colorScheme;
    bool isDownloading = false;
    bool isSuccess = false;

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (BuildContext context) {
        return StatefulBuilder(
          builder: (context, setState) {
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
                      border: Border.all(color: colorScheme.outlineVariant, width: 1),
                    ),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.system_update_rounded, size: 48, color: colorScheme.primary),
                        const SizedBox(height: 16),
                        Text(
                          'Update Available!',
                          style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold),
                        ),
                        const SizedBox(height: 8),
                        Flexible(
                          child: SingleChildScrollView(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                if (updateApp) ...[
                                  Text('App v$newAppVer', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold, color: colorScheme.primary)),
                                  const SizedBox(height: 4),
                                  Text(appNotes, style: Theme.of(context).textTheme.bodySmall?.copyWith(color: colorScheme.onSurfaceVariant)),
                                  if (updateAutd) const SizedBox(height: 16),
                                ],
                                if (updateAutd) ...[
                                  Text('AUTD v$newAutdVer', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold, color: colorScheme.primary)),
                                  const SizedBox(height: 4),
                                  Text(autdNotes, style: Theme.of(context).textTheme.bodySmall?.copyWith(color: colorScheme.onSurfaceVariant)),
                                ],
                              ],
                            ),
                          ),
                        ),
                        isSuccess
                            ? Column(
                                children: [
                                  Icon(Icons.check_circle_rounded, color: colorScheme.primary, size: 48),
                                  const SizedBox(height: 16),
                                  Text("Update Completed!", style: TextStyle(color: colorScheme.primary, fontWeight: FontWeight.bold)),
                                ],
                              )
                            : isDownloading
                                ? Column(
                                    children: [
                                      CircularProgressIndicator(color: colorScheme.primary),
                                      const SizedBox(height: 16),
                                      Text("Downloading & Installing...", style: TextStyle(color: colorScheme.primary)),
                                    ],
                                  )
                                : Row(
                                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                                children: [
                                  TextButton(
                                    onPressed: () => Navigator.of(context).pop(),
                                    child: const Text('Later'),
                                  ),
                                  FilledButton(
                                    onPressed: () async {
                                      setState(() => isDownloading = true);
                                      final success = await _performUpdate(updateApp, updateAutd, appUrl, autdUrl, newAutdVer);
                                      if (context.mounted) {
                                        if (updateAutd && !updateApp && success) {
                                          setState(() { isDownloading = false; isSuccess = true; });
                                          Future.delayed(const Duration(milliseconds: 1500), () {
                                            if (context.mounted) Navigator.of(context).pop();
                                          });
                                        } else {
                                          // Jika update App, modal langsung tutup karena UI installer system akan muncul
                                          Navigator.of(context).pop();
                                        }
                                      }
                                    },
                                    style: FilledButton.styleFrom(
                                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                                    ),
                                    child: const Text('Update Now'),
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
      },
    );
  }

  static Future<bool> _performUpdate(bool updateApp, bool updateAutd, String? appUrl, String? autdUrl, String newAutdVer) async {
    bool success = true;
    try {
      final tempDir = await getTemporaryDirectory();

      if (updateAutd && autdUrl != null) {
        final autdTempPath = '${tempDir.path}/autd_update';
        await _dio.download(autdUrl, autdTempPath);
        
        final shellScript = '''
          MOD_PROP=\$(grep -il 'id=.*aozora' /data/adb/modules/*/module.prop 2>/dev/null | head -n 1)
          if [ ! -z "\$MOD_PROP" ]; then
            MOD_DIR=\$(dirname "\$MOD_PROP")
            cp "$autdTempPath" "\$MOD_DIR/system/bin/autd"
            chmod 755 "\$MOD_DIR/system/bin/autd"
            
            killall autd
            nohup "\$MOD_DIR/system/bin/autd" > /dev/null 2>&1 &
          fi
          rm -f "$autdTempPath"
        ''';
        await platform.invokeMethod('executeScript', {'script': shellScript});
        await platform.invokeMethod('writeSystemFile', {'path': '/data/data/com.xaozora.manager/files/autd_version', 'value': newAutdVer});
      }

      if (updateApp && appUrl != null) {
        final apkTempPath = '${tempDir.path}/aozora_update.apk';
        await _dio.download(appUrl, apkTempPath);
        
        final shellScript = '''
          cp "$apkTempPath" /data/local/tmp/aozora_update.apk
          chmod 777 /data/local/tmp/aozora_update.apk
          
          OLD_VER=\$(dumpsys package com.xaozora.manager | grep versionName | head -n 1)
          
          am start -a android.intent.action.VIEW -d "file:///data/local/tmp/aozora_update.apk" -t application/vnd.android.package-archive
          rm -f "$apkTempPath"
          
          (
            for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
              sleep 10
              NEW_VER=\$(dumpsys package com.xaozora.manager | grep versionName | head -n 1)
              if [ "\$OLD_VER" != "\$NEW_VER" ]; then
                break
              fi
            done
            rm -f /data/local/tmp/aozora_update.apk
          ) &
        ''';
        await platform.invokeMethod('executeScript', {'script': shellScript});
      }
    } catch (e) {
      success = false;
    }
    return success;
  }
}