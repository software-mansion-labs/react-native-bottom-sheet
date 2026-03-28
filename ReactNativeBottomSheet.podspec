require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "ReactNativeBottomSheet"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => min_ios_version_supported }
  s.source       = { :git => "https://github.com/software-mansion-labs/react-native-bottom-sheet.git", :tag => "#{s.version}" }

  s.source_files = ["ios/**/*.{h,m,mm,cpp,swift}", "common/**/*.{h,cpp}"]
  s.private_header_files = ["ios/**/*.h", "common/**/*.h"]
  s.swift_version = "5.0"
  s.pod_target_xcconfig = {
    "DEFINES_MODULE" => "YES",
    "HEADER_SEARCH_PATHS" => "\"$(PODS_TARGET_SRCROOT)/common/cpp\""
  }

  install_modules_dependencies(s)
end
