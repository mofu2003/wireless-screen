//! Board facts used to choose a decoder and name the receiver.
//!
//! RK3576 BSP images expose the vendor GStreamer MPP decoder
//! (`mppvideodec`), while mainline kernels expose the stateless V4L2 decoder
//! (`v4l2slh264dec`). The generic receiver deliberately does not trust every
//! V4L2 stateless decoder, so Rockchip boards get a narrow exception here.

use std::path::Path;
use std::sync::OnceLock;

const DEVICE_TREE: &str = "/proc/device-tree";

#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct Platform {
    pub model: Option<String>,
    pub compatible: Vec<String>,
}

impl Platform {
    pub fn is_rockchip(&self) -> bool {
        self.compatible
            .iter()
            .any(|s| s.to_ascii_lowercase().contains("rockchip,rk"))
    }

    pub fn is_rk3576(&self) -> bool {
        self.compatible
            .iter()
            .any(|s| s.to_ascii_lowercase().contains("rk3576"))
            || self
                .model
                .as_deref()
                .is_some_and(|s| s.to_ascii_lowercase().contains("rk3576"))
    }

    pub fn device_name(&self) -> String {
        if self.is_rk3576() {
            "RK3576 (Linux)".into()
        } else if self.is_rockchip() {
            "Rockchip (Linux)".into()
        } else {
            "Linux".into()
        }
    }

    pub fn summary(&self) -> String {
        let model = self.model.as_deref().unwrap_or("unknown board");
        let compatible = if self.compatible.is_empty() {
            "no device-tree compatible list".into()
        } else {
            self.compatible.join(", ")
        };
        format!("{model} [{compatible}]")
    }
}

fn read_trimmed(path: &Path) -> Option<String> {
    let bytes = std::fs::read(path).ok()?;
    let value = String::from_utf8_lossy(&bytes)
        .trim_end_matches('\0')
        .trim()
        .to_string();
    (!value.is_empty()).then_some(value)
}

fn read_list(path: &Path) -> Vec<String> {
    let Ok(bytes) = std::fs::read(path) else {
        return Vec::new();
    };
    String::from_utf8_lossy(&bytes)
        .split('\0')
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(ToOwned::to_owned)
        .collect()
}

pub fn detect() -> Platform {
    let root = Path::new(DEVICE_TREE);
    Platform {
        model: read_trimmed(&root.join("model")),
        compatible: read_list(&root.join("compatible")),
    }
}

pub fn get() -> &'static Platform {
    static PLATFORM: OnceLock<Platform> = OnceLock::new();
    PLATFORM.get_or_init(detect)
}

#[cfg(test)]
mod tests {
    use super::Platform;

    fn board(model: &str, compatible: &[&str]) -> Platform {
        Platform {
            model: Some(model.into()),
            compatible: compatible.iter().map(|s| (*s).into()).collect(),
        }
    }

    #[test]
    fn recognises_rk3576_and_names_it() {
        let p = board(
            "RK3576 EVB1 V10 Board",
            &["rockchip,rk3576-evb1-v10", "rockchip,rk3576"],
        );
        assert!(p.is_rockchip());
        assert!(p.is_rk3576());
        assert_eq!(p.device_name(), "RK3576 (Linux)");
    }

    #[test]
    fn distinguishes_other_rockchip_boards() {
        let p = board("RK3588 Board", &["rockchip,rk3588"]);
        assert!(p.is_rockchip());
        assert!(!p.is_rk3576());
        assert_eq!(p.device_name(), "Rockchip (Linux)");
    }

    #[test]
    fn leaves_non_rockchip_boards_generic() {
        let p = board("Raspberry Pi 5", &["raspberrypi,5-model-b"]);
        assert!(!p.is_rockchip());
        assert!(!p.is_rk3576());
        assert_eq!(p.device_name(), "Linux");
    }
}
