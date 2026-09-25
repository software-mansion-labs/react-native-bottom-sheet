#!/bin/zsh
set -euo pipefail

readonly MINIMUM_XCODE_VERSION="16.1"

SCRIPT_DIR=${0:A:h}
REPOSITORY_ROOT=${SCRIPT_DIR:h}
HARNESS_DIR="$REPOSITORY_ROOT/tests/ios-native"
ARTIFACT_DIRECTORY="$REPOSITORY_ROOT/.bundle/ios-native-results"
DERIVED_DATA_PATH=${IOS_TEST_DERIVED_DATA_PATH:-"$REPOSITORY_ROOT/.bundle/ios-native-derived-data"}
RUN_IDENTIFIER="$(date +%Y%m%d-%H%M%S)-$$"
LOG_PATH=${IOS_TEST_LOG_PATH:-"$ARTIFACT_DIRECTORY/BottomSheetNativeTests-$RUN_IDENTIFIER.log"}
RESULT_BUNDLE_PATH=${IOS_TEST_RESULT_BUNDLE_PATH:-"$ARTIFACT_DIRECTORY/BottomSheetNativeTests-$RUN_IDENTIFIER.xcresult"}
BUNDLE_EXECUTABLE=${BUNDLE_COMMAND:-bundle}

fail() {
  print -u2 -- "error: $*"
  exit 1
}

REQUIRED_RUBY_VERSION=$(<"$REPOSITORY_ROOT/.ruby-version")
REQUIRED_BUNDLER_VERSION=$(
  sed -n '/^BUNDLED WITH$/ { n; s/^[[:space:]]*//; p; }' "$REPOSITORY_ROOT/Gemfile.lock"
)
[[ -n "$REQUIRED_BUNDLER_VERSION" ]] || fail "Unable to read the Bundler version from Gemfile.lock"

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command is unavailable: $1"
}

mkdir -p \
  "$ARTIFACT_DIRECTORY" \
  "$DERIVED_DATA_PATH" \
  "${LOG_PATH:h}" \
  "${RESULT_BUNDLE_PATH:h}"
[[ ! -e "$RESULT_BUNDLE_PATH" ]] || fail "Result bundle already exists: $RESULT_BUNDLE_PATH"
exec > >(tee "$LOG_PATH") 2>&1

cd "$REPOSITORY_ROOT"
require_command ruby
require_command "$BUNDLE_EXECUTABLE"
require_command xcodebuild
require_command xcrun
require_command node
require_command bun

RUBY_VERSION_ACTUAL=$(ruby -e 'print RUBY_VERSION')
[[ "$RUBY_VERSION_ACTUAL" == "$REQUIRED_RUBY_VERSION" ]] ||
  fail "Ruby $REQUIRED_RUBY_VERSION is required; selected $RUBY_VERSION_ACTUAL"

BUNDLER_VERSION_ACTUAL=$("$BUNDLE_EXECUTABLE" --version | grep -Eo '[0-9]+\.[0-9]+\.[0-9]+' | tail -1)
[[ "$BUNDLER_VERSION_ACTUAL" == "$REQUIRED_BUNDLER_VERSION" ]] ||
  fail "Bundler $REQUIRED_BUNDLER_VERSION is required; selected $BUNDLER_VERSION_ACTUAL"

XCODE_VERSION_ACTUAL=$(xcodebuild -version | sed -n '1s/^Xcode //p')
ruby -rrubygems -e \
  'exit(Gem::Version.new(ARGV.fetch(0)) >= Gem::Version.new(ARGV.fetch(1)) ? 0 : 1)' \
  "$XCODE_VERSION_ACTUAL" "$MINIMUM_XCODE_VERSION" ||
  fail "Xcode $MINIMUM_XCODE_VERSION or newer is required; selected $XCODE_VERSION_ACTUAL"
if [[ "${CI:-}" == "true" && "$XCODE_VERSION_ACTUAL" != "16.4" ]]; then
  fail "CI requires Xcode 16.4; selected $XCODE_VERSION_ACTUAL"
fi

NODE_VERSION_REQUIRED=$(<.nvmrc)
NODE_VERSION_REQUIRED=${NODE_VERSION_REQUIRED#v}
NODE_VERSION_ACTUAL=$(node --version)
NODE_VERSION_ACTUAL=${NODE_VERSION_ACTUAL#v}
[[ "$NODE_VERSION_ACTUAL" == "$NODE_VERSION_REQUIRED" ]] ||
  fail "Node $NODE_VERSION_REQUIRED is required; selected $NODE_VERSION_ACTUAL"

BUN_VERSION_REQUIRED=$(<.bun-version)
BUN_VERSION_ACTUAL=$(bun --version)
[[ "$BUN_VERSION_ACTUAL" == "$BUN_VERSION_REQUIRED" ]] ||
  fail "Bun $BUN_VERSION_REQUIRED is required; selected $BUN_VERSION_ACTUAL"

print -- "IOS_TEST_RUBY=ruby $RUBY_VERSION_ACTUAL"
print -- "IOS_TEST_BUNDLER=Bundler $BUNDLER_VERSION_ACTUAL"
print -- "IOS_TEST_XCODE=$(xcodebuild -version | tr '\n' ' ')"
print -- "IOS_TEST_NODE=node $NODE_VERSION_ACTUAL"
print -- "IOS_TEST_BUN=bun $BUN_VERSION_ACTUAL"
print -- "IOS_TEST_LOG_PATH=$LOG_PATH"
print -- "IOS_TEST_RESULT_BUNDLE_PATH=$RESULT_BUNDLE_PATH"

if [[ -n "${IOS_TEST_DESTINATION:-}" ]]; then
  DESTINATION="$IOS_TEST_DESTINATION"
  SELECTED_UDID=$(ruby -e '
    match = ARGV.fetch(0).match(/(?:^|,)id=([^,]+)/)
    print(match ? match[1] : "override-without-id")
  ' "$DESTINATION")
else
  EXPECTED_RUNTIME=${IOS_TEST_RUNTIME_IDENTIFIER:-}
  if [[ "${CI:-}" == "true" && -z "$EXPECTED_RUNTIME" ]]; then
    fail "IOS_TEST_RUNTIME_IDENTIFIER is required in CI"
  fi

  SELECTED_UDID=$(
    IOS_TEST_RUNTIME_IDENTIFIER="$EXPECTED_RUNTIME" xcrun simctl list devices available -j |
      ruby -rjson -e '
        devices_by_runtime = JSON.parse(STDIN.read).fetch("devices")
        expected_runtime = ENV.fetch("IOS_TEST_RUNTIME_IDENTIFIER", "")
        devices = if expected_runtime.empty?
          devices_by_runtime.values.flatten
        else
          abort "No available Simulator runtime matches #{expected_runtime}" unless devices_by_runtime.key?(expected_runtime)
          devices_by_runtime.fetch(expected_runtime)
        end
        iphones = devices.select do |device|
          device["isAvailable"] && device.fetch("name", "").start_with?("iPhone")
        end
        selected = iphones.find { |device| device["state"] == "Booted" } || iphones.first
        abort "No available iPhone Simulator matches the selected runtime" unless selected
        puts selected.fetch("udid")
      '
  )
  DESTINATION="platform=iOS Simulator,id=$SELECTED_UDID"
fi

print -- "IOS_TEST_DESTINATION=$DESTINATION"
print -- "IOS_TEST_SELECTED_UDID=$SELECTED_UDID"

export BUNDLE_GEMFILE="$REPOSITORY_ROOT/Gemfile"
if [[ "${CI:-}" != "true" && -z "${BUNDLE_PATH:-}" ]]; then
  export BUNDLE_PATH="$REPOSITORY_ROOT/.bundle/gems"
fi
"$BUNDLE_EXECUTABLE" check || "$BUNDLE_EXECUTABLE" install
"$BUNDLE_EXECUTABLE" exec pod install --project-directory="$HARNESS_DIR"

xcodebuild test \
  -workspace "$HARNESS_DIR/BottomSheetNativeTests.xcworkspace" \
  -scheme BottomSheetNativeTests \
  -testPlan BottomSheetNativeTests \
  -configuration Debug \
  -destination "$DESTINATION" \
  -derivedDataPath "$DERIVED_DATA_PATH" \
  -resultBundlePath "$RESULT_BUNDLE_PATH" \
  CODE_SIGNING_ALLOWED=NO
