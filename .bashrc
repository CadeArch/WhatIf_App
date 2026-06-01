# MixedUp development helpers

help() {
  cat <<'EOF'
MixedUp shell commands

gs            Show git status.
mas           Check out the master branch.
mmas          Pull latest master, then merge master into the current branch.
src           Reload this repo .bashrc in the current terminal.
debug         Install and relaunch the debug APK on all connected devices/emulators.
build         Build the MixedUp debug APK.
rerun         Build the debug APK, then run debug.
tests         Run API debug unit tests.
help          Show this command list.
EOF
}

gs() {
  git status "$@"
}

mas() {
  git checkout master
}

mmas() {
  git pull origin master:master
}

src() {
  source "/c/Users/Cade Rasmussen/Documents/USU_Fall_2021/CS_5950_App_Dev/WhatIf_App/.bashrc"
}

debug() {
  local repo_root
  repo_root="$(git rev-parse --show-toplevel 2>/dev/null)"

  if [ -z "$repo_root" ]; then
    repo_root="/c/Users/Cade Rasmussen/Documents/USU_Fall_2021/CS_5950_App_Dev/WhatIf_App"
  fi

  powershell.exe -ExecutionPolicy Bypass -File "$repo_root/scripts/refresh-all-devices.ps1"
}

build() {
  ./gradlew.bat :MixedUp:assembleDebug
}

rerun() {
  build && debug
}

tests() {
  ./gradlew.bat :API:testDebugUnitTest
}
