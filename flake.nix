{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    systems.url = "github:nix-systems/default";
  };

  outputs =
    { systems, nixpkgs, ... }:
    let
      eachSystem =
        f:
        nixpkgs.lib.genAttrs (import systems) (
          system:
          f {
            inherit system;
            pkgs = nixpkgs.legacyPackages.${system};
          }
        );
    in
    {
      devShells = eachSystem (
        { system, pkgs }:
        let
          # Yarn is provided through corepack, pinned by `packageManager` in
          # package.json. nixpkgs' corepack wrapper cannot be pointed at a
          # specific Node, so the shims are generated against the shell's.
          corepackShim =
            shellPkgs: nodejs:
            shellPkgs.stdenv.mkDerivation {
              name = "corepack-shims";
              buildInputs = [ nodejs ];
              phases = [ "installPhase" ];
              installPhase = ''
                mkdir -p $out/bin
                corepack enable --install-directory=$out/bin
              '';
            };
          # Node follows .nvmrc (lts/krypton).
          nodeShellBuildInputs =
            shellPkgs: nodejs: with shellPkgs; [
              (corepackShim shellPkgs nodejs)
              nodejs
              typescript
              typescript-language-server
            ];
          # Versions the example app's Android build requires. The SDK platform,
          # build tools and NDK follow React Native's gradle/libs.versions.toml;
          # the CMake versions are the one ReactAndroid requests when built from
          # source and the Android Gradle plugin's default used by the app's own
          # native build.
          androidPlatformVersion = "37";
          androidBuildToolsVersion = "37.0.0";
          androidNdkVersion = "27.1.12297006";
          androidCmakeVersions = [
            "3.30.5"
            "3.22.1"
          ];
          # Native modules that don't follow the root project's versions fall
          # back to the Android Gradle plugin's defaults.
          androidExtraBuildToolsVersions = [ "36.0.0" ];
          # The Android SDK is unfree and requires accepting its license, which
          # is scoped to this shell rather than the machine's nixpkgs config.
          androidPkgs = import nixpkgs {
            inherit system;
            config = {
              allowUnfree = true;
              android_sdk.accept_license = true;
            };
          };
          androidComposition = androidPkgs.androidenv.composeAndroidPackages {
            platformVersions = [ androidPlatformVersion ];
            buildToolsVersions = [ androidBuildToolsVersion ] ++ androidExtraBuildToolsVersions;
            cmakeVersions = androidCmakeVersions;
            includeNDK = true;
            ndkVersions = [ androidNdkVersion ];
            includeEmulator = false;
            includeSystemImages = false;
          };
        in
        {
          default = pkgs.mkShell {
            buildInputs = nodeShellBuildInputs pkgs pkgs.nodejs_24;
          };

          # Everything needed to build the example app and run the library's
          # Android unit tests: `nix develop .#android`, then Gradle in
          # apps/example/android.
          android = androidPkgs.mkShell {
            buildInputs =
              (nodeShellBuildInputs androidPkgs androidPkgs.nodejs_24)
              ++ [
                androidComposition.androidsdk
                androidPkgs.jdk17
              ];
            shellHook = ''
              export ANDROID_HOME="${androidComposition.androidsdk}/libexec/android-sdk"
              export ANDROID_SDK_ROOT="$ANDROID_HOME"
              export ANDROID_NDK_ROOT="$ANDROID_HOME/ndk/${androidNdkVersion}"
              export JAVA_HOME="${androidPkgs.jdk17.home}"
              export CMAKE_VERSION="${builtins.head androidCmakeVersions}"
              # Gradle downloads its own aapt2, which cannot run on NixOS
              if [ "$(uname -s)" = "Linux" ]; then
                export GRADLE_OPTS="-Dorg.gradle.project.android.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/${androidBuildToolsVersion}/aapt2 ''${GRADLE_OPTS:-}"
              fi
            '';
          };
        }
      );
    };
}
