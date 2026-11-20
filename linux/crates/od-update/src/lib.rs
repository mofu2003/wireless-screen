use std::collections::BTreeMap;
use std::fs::{self, File};
use std::io::Read;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

use anyhow::{Context, Result, bail};
use semver::Version;
use serde::Deserialize;
use sha2::{Digest, Sha256};

pub const DEFAULT_MANIFEST_URL: &str = "https://raw.githubusercontent.com/mofu2003/wireless-screen/refs/heads/main/public/linux-version.json";
pub const MANIFEST_URL_ENV: &str = "WIRELESS_SCREEN_UPDATE_MANIFEST";

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Component {
    Sender,
    ReceiverFull,
    ReceiverCompat,
}

impl Component {
    pub fn package_key(self, arch: Arch) -> String {
        let component = match self {
            Self::Sender => "sender",
            Self::ReceiverFull => "receiver-full",
            Self::ReceiverCompat => "receiver-compat",
        };
        format!("{component}-{}", arch.as_str())
    }

    fn binary_name(self) -> &'static str {
        match self {
            Self::Sender => "wireless-screen-sender",
            Self::ReceiverFull | Self::ReceiverCompat => "wireless-screen-receiver",
        }
    }

    fn label(self) -> &'static str {
        match self {
            Self::Sender => "发送端",
            Self::ReceiverFull => "接收端完整包",
            Self::ReceiverCompat => "接收端兼容包",
        }
    }

    fn restart_hint(self) -> &'static str {
        match self {
            Self::Sender => "请重新启动正在运行的发送端，或重启对应的 systemd 用户服务。",
            Self::ReceiverFull | Self::ReceiverCompat => {
                "请重新启动接收端，或运行 systemctl --user restart wireless-screen-receiver。"
            }
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Arch {
    Aarch64,
    X86_64,
}

impl Arch {
    pub fn current() -> Result<Self> {
        match std::env::consts::ARCH {
            "aarch64" => Ok(Self::Aarch64),
            "x86_64" => Ok(Self::X86_64),
            other => bail!("Linux 更新仅支持 aarch64 和 x86_64，当前架构是 {other}"),
        }
    }

    pub fn as_str(self) -> &'static str {
        match self {
            Self::Aarch64 => "aarch64",
            Self::X86_64 => "x86_64",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum InstallScope {
    User,
    System,
}

impl InstallScope {
    pub fn from_executable(path: &Path) -> Result<Self> {
        let path = fs::canonicalize(path).unwrap_or_else(|_| path.to_path_buf());
        if path.starts_with("/usr/local") {
            return Ok(Self::System);
        }
        for root in [
            "/usr/bin",
            "/bin",
            "/snap",
            "/nix/store",
            "/app",
            "/var/lib/flatpak",
        ] {
            if path.starts_with(root) {
                bail!(
                    "当前程序由系统包管理器管理（{}）。请使用发行版或 Flatpak 的更新方式，不要覆盖系统文件。",
                    path.display()
                );
            }
        }
        Ok(Self::User)
    }

    fn detect() -> Result<Self> {
        let executable = std::env::current_exe().context("无法定位当前程序")?;
        Self::from_executable(&executable)
    }

    fn destination(self, binary_name: &str) -> Result<PathBuf> {
        match self {
            Self::User => {
                let home = std::env::var_os("HOME").context("HOME 未设置，无法确定用户安装目录")?;
                Ok(PathBuf::from(home).join(".local/bin").join(binary_name))
            }
            Self::System => Ok(PathBuf::from("/usr/local/bin").join(binary_name)),
        }
    }

    fn installer_flag(self) -> Option<&'static str> {
        match self {
            Self::User => None,
            Self::System => Some("--system"),
        }
    }

    fn label(self) -> &'static str {
        match self {
            Self::User => "当前用户",
            Self::System => "系统目录",
        }
    }
}

#[derive(Debug, Clone, Copy)]
pub struct UpdateOptions {
    pub check: bool,
    pub scope: Option<InstallScope>,
}

#[derive(Debug, Deserialize)]
struct Manifest {
    version: String,
    #[serde(default)]
    tag: String,
    #[serde(default)]
    notes: String,
    packages: BTreeMap<String, Package>,
}

impl Manifest {
    fn parse(bytes: &[u8]) -> Result<Self> {
        let manifest: Self = serde_json::from_slice(bytes).context("更新清单不是有效的 JSON")?;
        manifest.validate()?;
        Ok(manifest)
    }

    fn validate(&self) -> Result<()> {
        parse_version(&self.version).context("更新清单里的版本号无效")?;
        if self.packages.is_empty() {
            bail!("更新清单没有安装包");
        }
        for (key, package) in &self.packages {
            if key.trim().is_empty() {
                bail!("更新清单包含空的安装包名称");
            }
            if package.url.trim().is_empty() {
                bail!("更新清单里的 {key} 没有下载地址");
            }
            if !(package.url.starts_with("https://") || package.url.starts_with("http://")) {
                bail!("更新清单里的 {key} 下载地址不是 HTTP(S)");
            }
            validate_sha256(&package.sha256)
                .with_context(|| format!("更新清单里的 {key} 校验值无效"))?;
        }
        Ok(())
    }

    fn package(&self, component: Component, arch: Arch) -> Result<&Package> {
        let key = component.package_key(arch);
        self.packages
            .get(&key)
            .with_context(|| format!("更新清单没有 {key} 安装包"))
    }
}

#[derive(Debug, Deserialize)]
struct Package {
    url: String,
    sha256: String,
}

pub fn run(component: Component, current_version: &str, options: UpdateOptions) -> Result<()> {
    let current = parse_version(current_version).context("当前程序版本号无效")?;
    let manifest_url =
        std::env::var(MANIFEST_URL_ENV).unwrap_or_else(|_| DEFAULT_MANIFEST_URL.to_string());

    println!("正在检查 Linux {} 更新…", component.label());
    let manifest = fetch_manifest(manifest_url.trim())?;
    let latest = parse_version(&manifest.version)?;

    println!("当前版本：{current_version}");
    println!("最新版本：{}", manifest.version);
    if !manifest.tag.is_empty() {
        println!("发布标签：{}", manifest.tag);
    }

    if latest <= current {
        println!("已是最新版本。");
        return Ok(());
    }

    let arch = Arch::current()?;
    let package = manifest.package(component, arch)?;
    println!("发现新版本：{current_version} → {}", manifest.version);
    if options.check {
        println!("安装更新：{} update", component.binary_name());
        return Ok(());
    }

    let scope = match options.scope {
        Some(scope) => scope,
        None => InstallScope::detect()?,
    };
    let destination = scope.destination(component.binary_name())?;
    ensure_scope(scope, &destination)?;

    println!("安装位置：{}（{}）", destination.display(), scope.label());
    if !manifest.notes.trim().is_empty() {
        println!();
        println!("{}", manifest.notes.trim());
        println!();
    }

    let temp = tempfile::Builder::new()
        .prefix("wireless-screen-update-")
        .tempdir()
        .context("无法创建更新临时目录")?;
    let archive = temp.path().join("package.tar.gz");
    println!("正在下载 {}…", package.url);
    curl_download(package.url.trim(), &archive)?;

    let actual = sha256_file(&archive)?;
    if !actual.eq_ignore_ascii_case(package.sha256.trim()) {
        bail!(
            "安装包 SHA-256 校验失败\n期望：{}\n实际：{actual}",
            package.sha256.trim()
        );
    }
    println!("SHA-256 校验通过。");

    let extracted = temp.path().join("extracted");
    fs::create_dir(&extracted).context("无法创建解压目录")?;
    extract_tar(&archive, &extracted)?;
    let package_dir = find_package_dir(&extracted)?;
    run_installer(&package_dir, scope)?;
    verify_installed(&destination, &latest)?;

    println!();
    println!(
        "已更新 Linux {}：{}",
        component.label(),
        destination.display()
    );
    println!("{}", component.restart_hint());
    Ok(())
}

fn parse_version(value: &str) -> Result<Version> {
    let value = value.trim().trim_start_matches('v');
    Version::parse(value).with_context(|| format!("无法解析版本号 {value:?}"))
}

fn validate_sha256(value: &str) -> Result<()> {
    let value = value.trim();
    if value.len() != 64 || !value.bytes().all(|byte| byte.is_ascii_hexdigit()) {
        bail!("需要 64 位十六进制 SHA-256");
    }
    Ok(())
}

fn curl_command(url: &str) -> Command {
    let mut command = Command::new("curl");
    command
        .args(["--fail", "--location", "--silent", "--show-error"])
        .args([
            "--retry",
            "3",
            "--retry-delay",
            "1",
            "--connect-timeout",
            "15",
            "--max-time",
            "600",
        ]);
    if url.starts_with("https://") {
        command.args(["--proto", "=https", "--proto-redir", "=https", "--tlsv1.2"]);
    }
    command
}

fn ensure_tool(tool: &str) -> Result<()> {
    let status = Command::new(tool)
        .arg("--version")
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status()
        .with_context(|| format!("找不到 {tool}；请先安装 {tool}"))?;
    if !status.success() {
        bail!("{tool} 无法正常运行");
    }
    Ok(())
}

fn fetch_manifest(url: &str) -> Result<Manifest> {
    ensure_tool("curl")?;
    let output = curl_command(url)
        .arg(url)
        .output()
        .context("无法启动 curl")?;
    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        bail!("下载更新清单失败（{}）\n{}", output.status, stderr.trim());
    }
    Manifest::parse(&output.stdout)
}

fn curl_download(url: &str, destination: &Path) -> Result<()> {
    ensure_tool("curl")?;
    let output = curl_command(url)
        .arg("--output")
        .arg(destination)
        .arg(url)
        .output()
        .context("无法启动 curl")?;
    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        bail!("下载安装包失败（{}）\n{}", output.status, stderr.trim());
    }
    let size = fs::metadata(destination)
        .with_context(|| format!("安装包没有写入 {}", destination.display()))?
        .len();
    if size == 0 {
        bail!("下载到的安装包为空");
    }
    Ok(())
}

fn sha256_file(path: &Path) -> Result<String> {
    let mut file = File::open(path).with_context(|| format!("无法打开 {}", path.display()))?;
    let mut hasher = Sha256::new();
    let mut buffer = [0_u8; 64 * 1024];
    loop {
        let read = file.read(&mut buffer).context("读取安装包失败")?;
        if read == 0 {
            break;
        }
        hasher.update(&buffer[..read]);
    }
    Ok(format!("{:x}", hasher.finalize()))
}

fn extract_tar(archive: &Path, destination: &Path) -> Result<()> {
    ensure_tool("tar")?;
    let output = Command::new("tar")
        .arg("-xzf")
        .arg(archive)
        .arg("-C")
        .arg(destination)
        .output()
        .context("无法启动 tar")?;
    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        bail!("解压安装包失败（{}）\n{}", output.status, stderr.trim());
    }
    Ok(())
}

fn find_package_dir(root: &Path) -> Result<PathBuf> {
    let root = fs::canonicalize(root).context("无法定位解压目录")?;
    let mut candidates = Vec::new();
    for entry in fs::read_dir(&root).context("无法读取解压目录")? {
        let entry = entry?;
        if !entry.file_type()?.is_dir() {
            continue;
        }
        let path = entry.path();
        if path.join("install.sh").is_file() {
            candidates.push(path);
        }
    }

    match candidates.as_slice() {
        [package] => {
            let package = fs::canonicalize(package)?;
            if !package.starts_with(&root) {
                bail!("安装包解压到了临时目录之外");
            }
            Ok(package)
        }
        [] => bail!("安装包里没有 install.sh"),
        _ => bail!("安装包里包含多个可安装目录"),
    }
}

fn run_installer(package_dir: &Path, scope: InstallScope) -> Result<()> {
    ensure_tool("bash")?;
    let installer = package_dir.join("install.sh");
    let mut command = Command::new("bash");
    command.arg(&installer).current_dir(package_dir);
    if let Some(flag) = scope.installer_flag() {
        command.arg(flag);
    }
    let status = command
        .status()
        .with_context(|| format!("无法运行 {}", installer.display()))?;
    if !status.success() {
        bail!("安装脚本失败（{status}）");
    }
    Ok(())
}

fn verify_installed(destination: &Path, version: &Version) -> Result<()> {
    let output = Command::new(destination)
        .arg("--version")
        .output()
        .with_context(|| format!("无法运行新安装的 {}", destination.display()))?;
    if !output.status.success() {
        bail!("新安装的程序无法运行（{}）", output.status);
    }
    let stdout = String::from_utf8_lossy(&output.stdout);
    let reported = stdout
        .lines()
        .find_map(|line| line.split_whitespace().last())
        .unwrap_or("");
    if reported.trim_start_matches('v') != version.to_string() {
        bail!("新安装的程序报告版本 {reported:?}，预期 {}", version);
    }
    Ok(())
}

fn ensure_scope(scope: InstallScope, destination: &Path) -> Result<()> {
    match (scope, is_root()) {
        (InstallScope::System, Some(false)) => {
            let executable =
                std::env::current_exe().unwrap_or_else(|_| PathBuf::from("wireless-screen"));
            bail!(
                "{} 是系统级安装，需要管理员权限。请运行：\nsudo {} update",
                destination.display(),
                executable.display()
            );
        }
        (InstallScope::User, Some(true)) => {
            bail!(
                "{} 是用户级安装，请不要使用 sudo。请以安装它的普通用户运行 update。",
                destination.display()
            );
        }
        _ => Ok(()),
    }
}

fn is_root() -> Option<bool> {
    let status = fs::read_to_string("/proc/self/status").ok()?;
    let effective_uid = status.lines().find_map(|line| {
        let rest = line.strip_prefix("Uid:")?;
        rest.split_whitespace().nth(1)?.parse::<u32>().ok()
    })?;
    Some(effective_uid == 0)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn package_keys_match_release_assets() {
        assert_eq!(
            Component::Sender.package_key(Arch::Aarch64),
            "sender-aarch64"
        );
        assert_eq!(Component::Sender.package_key(Arch::X86_64), "sender-x86_64");
        assert_eq!(
            Component::ReceiverFull.package_key(Arch::Aarch64),
            "receiver-full-aarch64"
        );
        assert_eq!(
            Component::ReceiverCompat.package_key(Arch::X86_64),
            "receiver-compat-x86_64"
        );
    }

    #[test]
    fn versions_compare_like_semver() {
        assert!(parse_version("0.1.5").unwrap() > parse_version("0.1.4").unwrap());
        assert!(parse_version("v0.1.5").unwrap() == parse_version("0.1.5").unwrap());
        assert!(parse_version("0.1.5").unwrap() > parse_version("0.1.5-rc.1").unwrap());
        assert!(parse_version("0.1.5-rc.2").unwrap() > parse_version("0.1.5-rc.1").unwrap());
    }

    #[test]
    fn parses_and_validates_manifest() {
        let manifest = Manifest::parse(
            br#"{
                "version": "0.1.5",
                "tag": "v0.1.5",
                "notes": "test",
                "packages": {
                    "sender-aarch64": {
                        "url": "https://example.com/sender.tar.gz",
                        "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    }
                }
            }"#,
        )
        .unwrap();
        assert_eq!(manifest.version, "0.1.5");
        assert_eq!(
            manifest
                .package(Component::Sender, Arch::Aarch64)
                .unwrap()
                .url,
            "https://example.com/sender.tar.gz"
        );
        assert!(
            manifest
                .package(Component::ReceiverFull, Arch::Aarch64)
                .is_err()
        );
    }

    #[test]
    fn rejects_bad_manifest_checksum() {
        let error = Manifest::parse(
            br#"{
                "version": "0.1.5",
                "packages": {
                    "sender-x86_64": {
                        "url": "https://example.com/sender.tar.gz",
                        "sha256": "not-a-checksum"
                    }
                }
            }"#,
        )
        .unwrap_err();
        assert!(error.to_string().contains("校验值无效"));
    }

    #[test]
    fn rejects_non_http_package_url() {
        let error = Manifest::parse(
            br#"{
                "version": "0.1.5",
                "packages": {
                    "sender-x86_64": {
                        "url": "file:///tmp/sender.tar.gz",
                        "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    }
                }
            }"#,
        )
        .unwrap_err();
        assert!(error.to_string().contains("HTTP(S)"));
    }

    #[test]
    fn scope_detection_distinguishes_managed_installs() {
        assert_eq!(
            InstallScope::from_executable(Path::new("/home/me/.local/bin/wireless-screen-sender"))
                .unwrap(),
            InstallScope::User
        );
        assert_eq!(
            InstallScope::from_executable(Path::new("/usr/local/bin/wireless-screen-receiver"))
                .unwrap(),
            InstallScope::System
        );
        assert!(
            InstallScope::from_executable(Path::new("/usr/bin/wireless-screen-receiver")).is_err()
        );
    }

    #[test]
    fn sha256_matches_known_value() {
        let temp = tempfile::tempdir().unwrap();
        let path = temp.path().join("abc");
        fs::write(&path, b"abc").unwrap();
        assert_eq!(
            sha256_file(&path).unwrap(),
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        );
    }
}
