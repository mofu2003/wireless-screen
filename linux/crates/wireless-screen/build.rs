//! `OD_APP_VERSION` for `wireless-screen --version` and updates: the
//! `vX.Y.Z` release tag, see `../build-version.rs`.

include!("../build-version.rs");

fn main() {
    emit_version("OD_APP_VERSION");
}
