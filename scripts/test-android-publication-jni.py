#!/usr/bin/env python3
"""Build/run non-UI JNI tests in the existing library instrumentation APK.

Fixture mode reuses SDK/CMake flags and production objects with a test-only
native factory. Installation and mount modes load the actual production library
through a live ReactHost, in separate processes. Production files stay unchanged.
"""
import argparse
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'android/build/publication-jni'
TEST_CLASS = 'com.swmansion.reactnativebottomsheet.accessibility.FabricPortalPublicationJniTest'
INSTALL_TEST_CLASS = 'com.swmansion.reactnativebottomsheet.accessibility.FabricPortalPublicationInstallJniTest'
MOUNT_TEST_CLASS = 'com.swmansion.reactnativebottomsheet.accessibility.FabricPortalPublicationMountJniTest'
FAILURE_TEST_CLASS = 'com.swmansion.reactnativebottomsheet.accessibility.FabricPortalPublicationFailureJniTest'
ABIS = ('arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64')


def run(command, *, cwd=ROOT, capture=False):
    return subprocess.check_output(command, cwd=cwd, text=True) if capture else subprocess.run(
        command, cwd=cwd, check=True)


def physical_device():
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not sdk:
        properties = ROOT / 'example/android/local.properties'
        match = re.search(r'^sdk.dir=(.+)$', properties.read_text(), re.MULTILINE)
        if not match:
            sys.exit('Set ANDROID_HOME or sdk.dir in example/android/local.properties.')
        sdk = match[1]
    adb = Path(sdk) / 'platform-tools/adb'
    devices = [line.split()[0] for line in run([str(adb), 'devices'], capture=True).splitlines()
               if len(line.split()) >= 2 and line.split()[1] == 'device'
               and not line.startswith('emulator-')]
    selected = os.environ.get('ANDROID_SERIAL')
    if selected:
        if selected not in devices:
            sys.exit('ANDROID_SERIAL must select an authorized physical Android device.')
    elif len(devices) == 1:
        selected = devices[0]
    else:
        sys.exit('Connect one authorized physical Android, or select it with ANDROID_SERIAL.')
    os.environ['ANDROID_SERIAL'] = selected


def build_native(abi, installation=False, mount=False):
    databases = sorted((ROOT / 'example/android/app/.cxx/Debug').glob(
        f'*/{abi}/compile_commands.json'), key=lambda path: path.stat().st_mtime, reverse=True)
    item = None
    for database in databases:
        item = next((entry for entry in json.loads(database.read_text())
                     if entry['file'].endswith('/PortalPublicationSourceAndroid.cpp')), None)
        if item:
            break
    if not item:
        sys.exit(f'Missing production CMake compilation database for {abi}.')
    directory = Path(item['directory'])
    cache = (directory / 'CMakeCache.txt').read_text()
    ninja = re.search(r'^CMAKE_MAKE_PROGRAM:[^=]+=(.+)$', cache, re.MULTILINE)[1]
    target = re.search(r'^build (.+): CXX_SHARED_LIBRARY_LINKER__react_codegen_ReactNativeBottomSheetSpec_',
                       (directory / 'build.ninja').read_text(), re.MULTILINE)[1]
    if installation or mount:
        # This process loads the production JNI library, never the fixture's copy.
        folder = OUTPUT / ('mount-libs' if mount else 'production-libs') / abi
        folder.mkdir(parents=True, exist_ok=True)
        libraries = [Path(target)]
        if mount:
            # The app's real entry point registers its codegen descriptors.
            app_modules = Path(target).parent / 'libappmodules.so'
            if not app_modules.is_file():
                sys.exit(f'Missing app component registration for {abi}.')
            libraries = [app_modules, *Path(target).parent.glob('libreact_codegen_*.so')]
            for old in folder.glob('*.so'):
                old.unlink()
        for library in libraries:
            shutil.copy2(library, folder / library.name)
        print(f'Production JNI packaged: {abi}', flush=True)
        return
    command = shlex.split(item['command'])
    original_object = command[command.index('-o') + 1]
    object_file = OUTPUT / 'objects' / f'{abi}.o'
    object_file.parent.mkdir(parents=True, exist_ok=True)
    command[command.index('-o') + 1] = str(object_file)
    command[command.index('-c') + 1] = str(ROOT / 'tests/android-native/FabricPublicationJniFixture.cpp')
    run(command, cwd=directory)

    link = shlex.split(run([ninja, '-t', 'commands', target], cwd=directory, capture=True).splitlines()[-1])
    # CMake wraps its actual linker invocation in two shell no-ops.
    if link[:2] == [':', '&&']:
        link = link[2:]
    if link[-2:] == ['&&', ':']:
        link = link[:-2]
    if any(token in ('&&', ';', '|') for token in link):
        sys.exit('Unsupported CMake linker command; refusing to execute shell syntax.')
    if original_object not in link:
        sys.exit('JNI production object not found in the actual CMake link command.')
    link[link.index(original_object)] = str(object_file)
    library = OUTPUT / 'libs' / abi / 'libbottomsheet_publication_jni_test.so'
    library.parent.mkdir(parents=True, exist_ok=True)
    link[link.index('-o') + 1] = str(library)
    link = [token.replace('-Wl,-soname,libreact_codegen_ReactNativeBottomSheetSpec.so',
                          '-Wl,-soname,libbottomsheet_publication_jni_test.so') for token in link]
    run(link, cwd=directory)
    print(f'JNI fixture built: {abi}', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--assemble', action='store_true', help='Build only; no device is needed.')
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--installation', action='store_true',
                        help='Test acquisition through a live ReactHost in a separate process.')
    mode.add_argument('--mount', action='store_true',
                      help='Test production State delivery through an unattached ReactSurface.')
    mode.add_argument('--failure', action='store_true',
                      help='Document source validation limits after an ignored native layout failure.')
    args = parser.parse_args()
    mount = args.mount or args.failure
    test_class = FAILURE_TEST_CLASS if args.failure else MOUNT_TEST_CLASS if args.mount else INSTALL_TEST_CLASS if args.installation else TEST_CLASS
    expected_tests = 1 if args.installation or mount else 3
    if not args.assemble:
        physical_device()
    if mount:
        run(['node', str(ROOT / 'scripts/build-android-publication-mount.js')] +
            (['--failure'] if args.failure else []))
    gradle = [str(ROOT / 'example/android/gradlew'), '--project-dir', str(ROOT / 'example/android'),
              '--no-daemon', '--console=plain', '-PreactNativeArchitectures=' + ','.join(ABIS)]
    run(gradle + [':app:assembleDebug'])
    for abi in ABIS:
        build_native(abi, args.installation, mount)
    task = 'assembleDebugAndroidTest' if args.assemble else 'connectedDebugAndroidTest'
    started = time.time()
    if args.installation:
        gradle += ['-PbottomSheetPublicationInstallJni=true',
                   '-Pandroid.testInstrumentationRunnerArguments.bottomSheetPublicationInstallJni=true']
    if mount:
        gradle += ['-PbottomSheetPublicationMountJni=true',
                   '-Pandroid.testInstrumentationRunnerArguments.bottomSheetPublicationMountJni=true']
    if args.failure:
        gradle += ['-Pandroid.testInstrumentationRunnerArguments.bottomSheetPublicationFailureJni=true']
    run(gradle + ['--init-script', str(ROOT / 'tests/android-native/publication-jni.gradle'),
                 '-Pandroid.testInstrumentationRunnerArguments.class=' + test_class,
                 '-Pandroid.testInstrumentationRunnerArguments.bottomSheetPublicationJni=true',
                 ':swmansion_react-native-bottom-sheet:' + task])
    if not args.assemble:
        cases = []
        for report in (ROOT / 'android/build/outputs/androidTest-results/connected/debug').rglob('TEST-*.xml'):
            if report.stat().st_mtime >= started:
                cases.extend(ET.parse(report).getroot().iter('testcase'))
        if len(cases) != expected_tests or any(case.get('classname') != test_class or
                                 any(case.find(tag) is not None for tag in ('skipped', 'failure', 'error'))
                                 for case in cases):
            sys.exit(f'Expected exactly {expected_tests} freshly executed JNI tests without skips/failures; inspect the report.')
    print('JNI test APK assembled; Android runtime is pending.' if args.assemble else
          'JNI instrumentation finished; see android/build/reports/androidTests/connected/debug.')


if __name__ == '__main__':
    main()
