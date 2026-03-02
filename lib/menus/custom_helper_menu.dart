import 'dart:io';
import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:dio/dio.dart';
import 'package:crypto/crypto.dart';
import 'package:archive/archive_io.dart';
import 'package:path_provider/path_provider.dart';
import 'package:file_picker/file_picker.dart';

class CustomHelperMenu extends StatefulWidget {
  const CustomHelperMenu({super.key});

  @override
  State<CustomHelperMenu> createState() => _CustomHelperMenuState();
}

class _CustomHelperMenuState extends State<CustomHelperMenu> {
  File? _selectedZip;
  bool _useAutd = false;
  bool _isAutdReady = false;
  bool _isInstalling = false;
  String _statusText = 'Select a module ZIP to begin';

  final Dio _dio = Dio();

  @override
  void initState() {
    super.initState();
    _checkLocalAutd();
  }

  Future<void> _checkLocalAutd() async {
    try {
      final dir = await getApplicationSupportDirectory();
      final autdFile = File('${dir.path}/autd');
      final shaFile = File('${dir.path}/autd.sha256');

      if (await autdFile.exists() && await shaFile.exists()) {
        setState(() {
          _isAutdReady = true;
        });
      }
    } catch (e) {
      // Ignore errors during initial check
    }
  }

  Future<void> _pickZip() async {
    try {
      FilePickerResult? result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['zip'],
      );

      if (result != null && result.files.single.path != null) {
        setState(() {
          _selectedZip = File(result.files.single.path!);
          _statusText = 'Selected: ${_selectedZip!.path.split('/').last}\nReady to install.';
        });
      }
    } catch (e) {
      setState(() => _statusText = 'Error picking file: $e');
    }
  }

  Future<void> _verifyOrDownloadAutd(bool value) async {
    if (!value) {
      setState(() => _useAutd = false);
      return;
    }

    setState(() => _statusText = 'Verifying AUTD binary...');

    try {
      final dir = await getApplicationSupportDirectory();
      final autdFile = File('${dir.path}/autd');
      
      // fetch latest release
      final response = await _dio.get('https://api.github.com/repos/xMikkkaa/Automation-Daemon/releases/latest');
      final List assets = response.data['assets'];
      
      final autdAsset = assets.firstWhere((a) => a['name'] == 'autd', orElse: () => null);
      final shaAsset = assets.firstWhere((a) => a['name'] == 'autd.sha256', orElse: () => null);

      if (autdAsset == null || shaAsset == null) {
        throw Exception('Release assets not found on GitHub');
      }

      // download remote sha
      final shaResponse = await _dio.get(shaAsset['browser_download_url']);
      final remoteSha = shaResponse.data.toString().trim().split(' ').first;

      // check local file
      bool needsDownload = true;
      if (await autdFile.exists()) {
        final bytes = await autdFile.readAsBytes();
        final localSha = sha256.convert(bytes).toString();
        if (localSha == remoteSha) {
          needsDownload = false;
        }
      }

      if (!needsDownload) {
        setState(() {
          _useAutd = true;
          _isAutdReady = true;
          _statusText = 'AUTD binary verified and ready.';
        });
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('AUTD binary is up to date')),
          );
        }
        return;
      }

      // download if needed
      if (mounted) {
        await _showDownloadDialog(autdAsset['browser_download_url'], autdFile.path, remoteSha);
      }

    } catch (e) {
      setState(() {
        _useAutd = false;
        _statusText = 'Failed to verify/download AUTD: $e';
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: $e'), backgroundColor: Theme.of(context).colorScheme.error),
        );
      }
    }
  }

  Future<void> _showDownloadDialog(String url, String savePath, String expectedSha) async {
    bool downloadSuccess = false;
    String errorMessage = '';

    await showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) {
        double progress = 0.0;
        return StatefulBuilder(
          builder: (context, setDialogState) {
            // start download
            _dio.get(
              url,
              options: Options(responseType: ResponseType.bytes),
              onReceiveProgress: (received, total) {
                if (total != -1) {
                  setDialogState(() {
                    progress = received / total;
                  });
                }
              },
            ).then((response) async {
              try {
                print('Download completed, received ${response.data.length} bytes');
                
                // write to file
                final file = File(savePath);
                print('Writing to file: $savePath');
                await file.writeAsBytes(response.data);
                
                // verify file was written
                final fileSize = await file.length();
                print('File written, size: $fileSize bytes');
                
                if (fileSize == 0) {
                  throw Exception('File write failed - file is empty');
                }

                // verify hash
                final downloaded = response.data as List<int>;
                final localSha = sha256.convert(downloaded).toString();
                print('Downloaded SHA256: $localSha');
                print('Expected SHA256: $expectedSha');
                
                if (localSha != expectedSha) {
                  throw Exception('Hash mismatch: $localSha != $expectedSha');
                }

                // save sha file
                final dir = await getApplicationSupportDirectory();
                await File('${dir.path}/autd.sha256').writeAsString(expectedSha);
                print('SHA256 file saved');

                downloadSuccess = true;
              } catch (e) {
                print('Error during download processing: $e');
                errorMessage = e.toString();
                downloadSuccess = false;
              }
              
              if (mounted) {
                Navigator.pop(context);
              }
            }).catchError((e) {
              print('Download error: $e');
              errorMessage = e.toString();
              downloadSuccess = false;
              if (mounted) {
                Navigator.pop(context);
              }
            });

            return AlertDialog(
              title: const Text('Downloading AUTD'),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text('Fetching latest binary from GitHub...'),
                  const SizedBox(height: 16),
                  LinearProgressIndicator(value: progress),
                  const SizedBox(height: 8),
                  Text('${(progress * 100).toStringAsFixed(1)}%'),
                ],
              ),
            );
          },
        );
      },
    );

    // handle result
    if (downloadSuccess) {
      setState(() {
        _useAutd = true;
        _isAutdReady = true;
        _statusText = 'AUTD downloaded and verified.';
      });
    } else {
      setState(() {
        _useAutd = false;
        _statusText = 'Download failed: $errorMessage';
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Error: $errorMessage'),
            backgroundColor: Theme.of(context).colorScheme.error,
          ),
        );
      }
    }
  }

  Future<void> _installModule() async {
    if (_selectedZip == null) return;

    setState(() {
      _isInstalling = true;
      _statusText = 'Preparing installation...';
    });

    try {
      // pre-check autd
      if (_useAutd) {
        final dir = await getApplicationSupportDirectory();
        final autdFile = File('${dir.path}/autd');
        if (!await autdFile.exists()) {
          throw Exception('AUTD binary not found at: ${autdFile.path}\nPlease verify AUTD download completed successfully.');
        }
        final size = await autdFile.length();
        if (size == 0) {
          throw Exception('AUTD binary is empty (0 bytes)');
        }
        print('Pre-check: AUTD binary found, size: $size bytes');
      }

      // read zip
      final bytes = await _selectedZip!.readAsBytes();
      final archive = ZipDecoder().decodeBytes(bytes);
      final newArchive = Archive();

      // filter files
      setState(() => _statusText = 'Processing ZIP structure...');
      for (final file in archive) {
        if (!file.name.startsWith('webroot/')) {
          newArchive.addFile(file);
        }
      }

      // inject autd
      if (_useAutd) {
        setState(() => _statusText = 'Injecting AUTD binary...');
        final dir = await getApplicationSupportDirectory();
        final autdFile = File('${dir.path}/autd');
        if (await autdFile.exists()) {
          final autdBytes = await autdFile.readAsBytes();
          print('Injecting AUTD: ${autdBytes.length} bytes');
          final autdArchiveFile = ArchiveFile('system/bin/autd', autdBytes.length, autdBytes);
          // set unix permissions
          autdArchiveFile.mode = 493; 
          newArchive.addFile(autdArchiveFile);
        } else {
          throw Exception('AUTD file missing despite being enabled: ${autdFile.path}');
        }
      }

      // encode zip
      setState(() => _statusText = 'Encoding new ZIP...');
      final encodedBytes = ZipEncoder().encode(newArchive);
      if (encodedBytes == null) throw Exception('Failed to encode ZIP');

      final tempDir = await getTemporaryDirectory();
      final tempZipPath = '${tempDir.path}/aozora_update.zip';
      final tempFile = File(tempZipPath);
      await tempFile.writeAsBytes(encodedBytes);
      print('Created temp ZIP: $tempZipPath (${encodedBytes.length} bytes)');

      // generate shell script
      final shellScript = '''
        cp "$tempZipPath" /data/local/tmp/aozora_update.zip
        chmod 755 /data/local/tmp/aozora_update.zip
        
        # clean old module
        EXISTING=\$(grep -l 'id=aozora' /data/adb/modules/*/module.prop 2>/dev/null)
        if [ ! -z "\$EXISTING" ]; then
          MOD_DIR=\$(dirname "\$EXISTING")
          echo "Cleaning old module at \$MOD_DIR"
          rm -rf "\$MOD_DIR"
        fi

        # install based on root manager
        if [ -f /data/adb/magisk/magisk ]; then
           echo "Detected Magisk"
           magisk --install-module /data/local/tmp/aozora_update.zip
        elif [ -f /data/adb/ksu/bin/ksud ]; then
           echo "Detected KernelSU"
           /data/adb/ksu/bin/ksud module install /data/local/tmp/aozora_update.zip
        elif [ -f /data/adb/ap/bin/apatch ]; then
           echo "Detected APatch"
           /data/adb/ap/bin/apatch module install /data/local/tmp/aozora_update.zip
        else
           echo "Error: No supported root manager found (Magisk/KSU/APatch)"
           exit 1
        fi

        # cleanup
        rm /data/local/tmp/aozora_update.zip
      ''';

      // execute shell script
      setState(() => _statusText = 'Executing root installation...');
      final result = await Process.run('su', ['-c', shellScript]);

      setState(() {
        _isInstalling = false;
        _statusText = '--- Installation Output ---\n'
            'Exit Code: ${result.exitCode}\n\n'
            'STDOUT:\n${result.stdout}\n\n'
            'STDERR:\n${result.stderr}';
      });

      // Cleanup app cache file
      if (await tempFile.exists()) await tempFile.delete();

    } catch (e) {
      setState(() {
        _isInstalling = false;
        _statusText = 'Installation Failed:\n$e';
      });
      print('Installation error: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          "Custom Helper Installer",
          style: Theme.of(context).textTheme.headlineLarge?.copyWith(
                fontWeight: FontWeight.bold,
                color: colorScheme.primary,
              ),
        ),
        const SizedBox(height: 24),
        // File Selection Card
        Card(
          elevation: 0,
          color: colorScheme.surfaceContainerHighest,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
            side: BorderSide(color: colorScheme.outlineVariant),
          ),
          child: InkWell(
            onTap: _isInstalling ? null : _pickZip,
            borderRadius: BorderRadius.circular(16),
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                children: [
                  Icon(
                    Icons.folder_zip_outlined,
                    size: 48,
                    color: colorScheme.primary,
                  ),
                  const SizedBox(height: 16),
                  Text(
                    _selectedZip != null
                        ? _selectedZip!.path.split('/').last
                        : "Tap to select Module ZIP",
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                    textAlign: TextAlign.center,
                  ),
                  if (_selectedZip != null)
                    Text(
                      _selectedZip!.path,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: colorScheme.onSurfaceVariant,
                          ),
                      textAlign: TextAlign.center,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                ],
              ),
            ),
          ),
        ),
        const SizedBox(height: 24),

        // Options
        SwitchListTile(
          value: _useAutd,
          onChanged: (_selectedZip == null || _isInstalling)
              ? null
              : _verifyOrDownloadAutd,
          title: const Text("Inject AUTD Daemon"),
          subtitle: const Text("Downloads & injects latest binary from GitHub"),
          secondary: Icon(Icons.download_for_offline_outlined, color: colorScheme.secondary),
          activeColor: colorScheme.primary,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        ),
        const SizedBox(height: 24),

        // Install Button
        FilledButton(
          onPressed: (_selectedZip == null || _isInstalling) ? null : _installModule,
          style: FilledButton.styleFrom(
            padding: const EdgeInsets.symmetric(vertical: 16),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          ),
          child: _isInstalling
              ? SizedBox(
                  width: 24,
                  height: 24,
                  child: CircularProgressIndicator(
                    strokeWidth: 2.5,
                    color: colorScheme.onPrimary,
                  ),
                )
              : const Text("Install Module"),
        ),
        const SizedBox(height: 24),

        // Terminal Output
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: const Color(0xFF1E1E1E), // Terminal dark bg
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: colorScheme.outlineVariant),
          ),
          child: SelectableText(
            _statusText,
            style: const TextStyle(
              fontFamily: 'monospace',
              color: Color(0xFF00E676), // Terminal green
              fontSize: 12,
            ),
          ),
        ),
      ],
    );
  }
}