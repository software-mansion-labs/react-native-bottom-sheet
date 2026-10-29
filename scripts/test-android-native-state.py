#!/usr/bin/env python3
"""Run Android C++ state data tests on a Mac host using existing example Pods.

No Android device, simulator or application is launched. The ANDROID branch
of the data header is tested; Android ABI compilation is a separate check.
"""
from pathlib import Path
import hashlib
import json
import platform
import re
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
output = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else root / 'android/build/native-state-host'
output.mkdir(parents=True, exist_ok=True)
pods = root / 'example/ios/Pods'
react = pods / 'React-Core-prebuilt/React.xcframework/ios-arm64_x86_64-maccatalyst'
deps = pods / 'ReactNativeDependencies/framework/packages/react-native/ReactNativeDependencies.xcframework/ios-arm64_x86_64-maccatalyst'
hermes = pods / 'hermes-engine/destroot/Library/Frameworks/universal/hermesvm.xcframework/ios-arm64_x86_64-maccatalyst'
if not all((path / name).exists() for path, name in (
        (react, 'React.framework'), (deps, 'ReactNativeDependencies.framework'),
        (hermes, 'hermesvm.framework'))):
    sys.exit('Missing Mac Catalyst RN dependencies: install the example iOS Pods before running this host test.')
sdk = Path(subprocess.check_output(['xcrun', '--sdk', 'macosx', '--show-sdk-path'], text=True).strip())
rn = root / 'node_modules/react-native/ReactCommon'
common = root / 'common/cpp/react/renderer/components/ReactNativeBottomSheetSpec'
generated = root / 'android/build/generated/source/codegen/jni'
generated_components = generated / 'react/renderer/components/ReactNativeBottomSheetSpec'
if not (generated_components / 'Props.cpp').exists():
    sys.exit('Missing Android component codegen: run bun run test:android:unit before this host test.')
targets = {
    'BottomSheetViewStateTests': [],
    'PortalPublicationSourceTests': [common / 'PortalPublicationSource.cpp',
        common / 'PortalContextGraph.cpp', common / 'BottomSheetViewShadowNode.cpp',
        *[generated_components / name for name in ('Props.cpp', 'EventEmitters.cpp', 'ShadowNodes.cpp')]],
}
sources = [root / 'tests/android-native' / (name + '.cpp') for name in targets]
inputs = [Path(__file__).resolve(), *sources,
          *[common / name for name in ('BottomSheetViewState.h', 'PortalPublicationSource.h',
              'PortalPublicationSource.cpp', 'PortalContextGraph.h', 'PortalContextGraph.cpp',
              'BottomSheetViewShadowNode.cpp', 'ShadowNodes.h', 'ComponentDescriptors.h')],
          *targets['PortalPublicationSourceTests'][-3:]]
pins = {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in inputs}
command = ['xcrun', 'clang++', '-std=c++20', '-DANDROID', '-target', platform.machine() + '-apple-ios17.0-macabi',
           '-isysroot', str(sdk), '-F', str(sdk / 'System/iOSSupport/System/Library/Frameworks'),
           '-DREACT_NATIVE_TARGET_PLATFORM_IOS=1', '-DFOLLY_MOBILE=1',
           '-DFOLLY_USE_LIBCPP=1', '-DFOLLY_CFG_NO_COROUTINES=1']
# Prefer headers shipped with the linked prebuilt SDK. Its JSI ABI can differ
# from the JS package's source headers (including Runtime versus IRuntime).
structured_headers = react.parents[1] / 'Headers'
if structured_headers.is_dir():
    command += ['-I', str(structured_headers)]
for path in (react / 'React.framework/Headers').iterdir():
    command += ['-I', str(path)]
for path in [root / 'common/cpp', generated, rn, rn / 'yoga', rn / 'jsi',
             pods / 'ReactNativeDependencies/Headers', pods / 'hermes-engine/destroot/include',
             rn / 'react/renderer/components/view/platform/cxx'] + list(rn.rglob('platform/ios')):
    command += ['-I', str(path)]
for path in [react, deps, hermes]:
    command += ['-F', str(path), '-Wl,-rpath,' + str(path)]
result = {'source_sha256': pins, 'UI_launches': 0, 'host_data_test': True,
          'Android_runtime': False, 'targets': {}}
exit_code = 0
for source in sources:
    name = source.stem
    binary = output / name
    compiled = subprocess.run(command + [str(source), *map(str, targets[name]),
        '-framework', 'React', '-framework', 'ReactNativeDependencies', '-framework', 'hermesvm',
        '-framework', 'Foundation', '-framework', 'UIKit', '-o', str(binary)],
        text=True, capture_output=True)
    (output / (name + '-compile.log')).write_text(compiled.stdout + compiled.stderr)
    outcome = {'compile_exit': compiled.returncode}
    if compiled.returncode == 0:
        tested = subprocess.run([str(binary)], text=True, capture_output=True, timeout=20)
        log = tested.stdout + tested.stderr
        (output / (name + '-test.log')).write_text(log)
        match = re.search(r'STATE RESULT tests=(\d+) failures=(\d+)', log)
        outcome.update(test_exit=tested.returncode, tests=int(match[1]) if match else None,
                       failures=int(match[2]) if match else None)
        exit_code |= int(tested.returncode != 0 or match is None or int(match[2]) != 0)
        print(log)
    else:
        exit_code = 1
        print(compiled.stderr[-3000:])
    result['targets'][name] = outcome
for path, expected in pins.items():
    assert hashlib.sha256((root / path).read_bytes()).hexdigest() == expected, path
    (output / Path(path).name).write_bytes((root / path).read_bytes())
(output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
sys.exit(exit_code)
