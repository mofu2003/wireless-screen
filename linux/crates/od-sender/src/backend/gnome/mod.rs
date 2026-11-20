//! GNOME (Mutter): a virtual monitor from Mutter's ScreenCast API, input
//! through its RemoteDesktop API, frames from the stream's PipeWire node.
//! No portal and no dialog for an unsandboxed process (see `mutter.rs`).
//! Mutter removes the monitor when the session stops or this process's bus
//! connection closes, so nothing is ever left behind and `--keep-output`
//! cannot apply.

pub mod input;
pub mod layout;
pub mod mutter;

use anyhow::{Context, Result, bail};
use tracing::{info, warn};

use self::input::{Mapper, RemoteInput};
use self::mutter::{CursorMode, MonitorStream, Request, VirtualMonitor};
use super::lock::{OutputLock, lock_output};
use super::{
    Backend, BackendId, Caps, CaptureOptions, DisplayInfo, DisplaySpec, FrameSink, InputSink,
    VirtualDisplay,
};
use crate::pipewire::{PwCapture, PwSource};

pub struct GnomeBackend {
    versions: (i32, i32),
    cursor: CursorMode,
    mode: GnomeMode,
    monitors: Vec<String>,
}

#[derive(Debug, Clone)]
enum GnomeMode {
    Extend,
    Mirror { connector: String },
}

impl GnomeBackend {
    /// Connect to the session bus and check that Mutter's APIs are there.
    pub fn connect() -> Result<Self> {
        Self::connect_with(GnomeMode::Extend)
    }

    /// Mirror an existing monitor. `None` selects Mutter's primary monitor.
    pub fn connect_mirror(connector: Option<String>) -> Result<Self> {
        let conn = zbus::blocking::Connection::session()?;
        mutter::probe(&conn)?;
        let state = mutter::display_state(&conn)?;
        let connector = connector
            .or_else(|| state.primary_connector())
            .context("GNOME reports no monitor to mirror")?;
        if state.placement(&connector).is_none() {
            bail!(
                "unknown GNOME monitor {connector:?}; available: {}",
                state.connectors().join(", ")
            );
        }
        Self::connect_with(GnomeMode::Mirror { connector })
    }

    fn connect_with(mode: GnomeMode) -> Result<Self> {
        let conn = zbus::blocking::Connection::session()?;
        let versions = mutter::probe(&conn)?;
        let monitors = mutter::display_state(&conn)?.connectors();
        Ok(Self {
            versions,
            cursor: CursorMode::Embedded,
            mode,
            monitors,
        })
    }
}

impl Backend for GnomeBackend {
    /// Frames come over PipeWire, not a Wayland capture protocol.
    fn wayland_capture(&self) -> bool {
        false
    }

    fn set_capture(&mut self, _: super::capture::CapturePath) {}

    fn id(&self) -> BackendId {
        BackendId::Gnome
    }

    fn describe(&self) -> String {
        let mut text = format!(
            "GNOME Mutter (ScreenCast API v{}, RemoteDesktop API v{})",
            self.versions.0, self.versions.1
        );
        match &self.mode {
            GnomeMode::Extend => {}
            GnomeMode::Mirror { connector } => {
                text.push_str(&format!(", mirroring {connector}"));
            }
        }
        if !self.monitors.is_empty() {
            text.push_str(&format!("; monitors: {}", self.monitors.join(", ")));
        }
        text
    }

    fn caps(&self) -> Caps {
        Caps {
            // Mutter offers cursor metadata on the PipeWire stream; not read yet.
            cursor_metadata: false,
            dmabuf: false,
            // A stream created with `modes` has a fixed size: a new size is a
            // new monitor.
            resize_in_place: false,
            creates_outputs: matches!(self.mode, GnomeMode::Extend),
        }
    }

    fn open(&mut self, spec: &DisplaySpec) -> Result<Box<dyn VirtualDisplay>> {
        let lock = lock_output(&spec.name)?;
        match &self.mode {
            GnomeMode::Extend => {
                let vm = create(spec, self.cursor)?;
                Ok(Box::new(GnomeDisplay::new_virtual(
                    spec,
                    vm,
                    lock,
                    self.cursor,
                )))
            }
            GnomeMode::Mirror { connector } => {
                let monitor = MonitorStream::create(Some(connector), self.cursor)?;
                Ok(Box::new(GnomeDisplay::new_mirror(
                    monitor,
                    lock,
                    self.cursor,
                )))
            }
        }
    }

    fn set_cursor_visible(&mut self, visible: bool) {
        self.cursor = if visible {
            CursorMode::Embedded
        } else {
            CursorMode::Hidden
        };
    }

    fn clean(&mut self) -> Result<Vec<String>> {
        // Mutter removes a virtual monitor with its session; nothing outlives us.
        Ok(Vec::new())
    }
}

/// The monitor for `spec`, then its scale and position (Mutter places a new
/// monitor at scale 1 on the right).
fn create(spec: &DisplaySpec, cursor: CursorMode) -> Result<VirtualMonitor> {
    let mut vm = VirtualMonitor::create(&Request {
        width: spec.width,
        height: spec.height,
        hz: spec.hz,
        cursor,
    })?;
    match vm.arrange(spec.scale, spec.position) {
        Ok(scale) if (scale - spec.scale).abs() > 1e-6 => info!(
            "scale {} is not available for {}x{}; using {scale}",
            spec.scale, spec.width, spec.height
        ),
        Ok(_) => {}
        Err(e) => warn!("could not set scale/position of the virtual monitor: {e:#}"),
    }
    Ok(vm)
}

fn info_of(name: &str, placement: &mutter::Placement) -> DisplayInfo {
    let p = placement;
    DisplayInfo {
        name: name.to_string(),
        width: p.width,
        height: p.height,
        refresh_hz: p.refresh,
        scale: p.scale,
        x: p.x,
        y: p.y,
    }
}

fn mirror_info(placement: &mutter::Placement) -> DisplayInfo {
    let (width, height) = placement.stream_size();
    DisplayInfo {
        name: placement.connector.clone(),
        width,
        height,
        refresh_hz: placement.refresh,
        scale: placement.scale,
        x: placement.x,
        y: placement.y,
    }
}

enum GnomeSource {
    Virtual(VirtualMonitor),
    Mirror(MonitorStream),
}

impl GnomeSource {
    fn placement(&self) -> &mutter::Placement {
        match self {
            Self::Virtual(vm) => &vm.placement,
            Self::Mirror(monitor) => &monitor.placement,
        }
    }

    fn node(&self) -> u32 {
        match self {
            Self::Virtual(vm) => vm.node,
            Self::Mirror(monitor) => monitor.node,
        }
    }

    fn input(&self) -> mutter::InputHandle {
        match self {
            Self::Virtual(vm) => vm.input(),
            Self::Mirror(monitor) => monitor.input(),
        }
    }

    fn stop(&mut self) -> Result<()> {
        match self {
            Self::Virtual(vm) => vm.stop(),
            Self::Mirror(monitor) => monitor.stop(),
        }
    }
}

pub struct GnomeDisplay {
    /// Field order is drop order: capture before the monitor.
    capture: Option<PwCapture>,
    source: Option<GnomeSource>,
    info: DisplayInfo,
    cursor: CursorMode,
    _lock: OutputLock,
}

impl GnomeDisplay {
    fn new_virtual(
        spec: &DisplaySpec,
        vm: VirtualMonitor,
        lock: OutputLock,
        cursor: CursorMode,
    ) -> Self {
        let info = info_of(&spec.name, &vm.placement);
        info!(
            "virtual display {} ({}, PipeWire node {})",
            info, vm.placement.connector, vm.node
        );
        Self {
            capture: None,
            info,
            source: Some(GnomeSource::Virtual(vm)),
            cursor,
            _lock: lock,
        }
    }

    fn new_mirror(monitor: MonitorStream, lock: OutputLock, cursor: CursorMode) -> Self {
        let info = mirror_info(&monitor.placement);
        info!(
            "mirroring display {} ({}, PipeWire node {})",
            info, monitor.placement.connector, monitor.node
        );
        Self {
            capture: None,
            info,
            source: Some(GnomeSource::Mirror(monitor)),
            cursor,
            _lock: lock,
        }
    }
}

impl VirtualDisplay for GnomeDisplay {
    fn info(&self) -> &DisplayInfo {
        &self.info
    }

    /// The cursor is part of the stream (`cursor-mode` embedded), fixed when
    /// the monitor was created; `opts.cursor` does not change it.
    fn start_capture(&mut self, _opts: CaptureOptions, sink: FrameSink) -> Result<()> {
        self.stop_capture();
        let Some(source) = &self.source else {
            bail!("no Mutter stream");
        };
        // Mutter's stream lives on the session's PipeWire daemon. Asking for
        // the monitor's size is what sizes it on Mutter < 50 (no `modes`).
        self.capture = Some(PwCapture::spawn(
            PwSource {
                node: source.node(),
                serial: None,
                remote_fd: None,
            },
            (self.info.width, self.info.height),
            sink,
        )?);
        Ok(())
    }

    fn stop_capture(&mut self) {
        if let Some(mut c) = self.capture.take() {
            c.stop();
        }
    }

    fn reconfigure(&mut self, spec: &DisplaySpec) -> Result<()> {
        self.stop_capture();
        let source = match self.source.take() {
            Some(GnomeSource::Virtual(mut old)) => {
                if let Err(e) = old.stop() {
                    warn!("stopping the old virtual monitor: {e:#}");
                }
                let vm = create(spec, self.cursor)?;
                self.info = info_of(&spec.name, &vm.placement);
                info!("virtual display {} ({})", self.info, vm.placement.connector);
                GnomeSource::Virtual(vm)
            }
            Some(GnomeSource::Mirror(mut old)) => {
                let connector = old.placement.connector.clone();
                if let Err(e) = old.stop() {
                    warn!("stopping the old monitor stream: {e:#}");
                }
                let monitor = MonitorStream::create(Some(&connector), self.cursor)?;
                self.info = mirror_info(&monitor.placement);
                info!("mirroring display {} ({connector})", self.info);
                GnomeSource::Mirror(monitor)
            }
            None => bail!("no Mutter stream"),
        };
        self.source = Some(source);
        Ok(())
    }

    fn input(&mut self) -> Result<Box<dyn InputSink>> {
        let Some(source) = &self.source else {
            bail!("no Mutter stream");
        };
        let p = source.placement();
        let (width, height) = match source {
            // Virtual monitor coordinates are layout units; an existing
            // monitor's RecordMonitor stream is in upright physical pixels.
            GnomeSource::Virtual(_) => p.layout_size(),
            GnomeSource::Mirror(_) => {
                let (w, h) = p.stream_size();
                (w as f64, h as f64)
            }
        };
        Ok(Box::new(RemoteInput::start(
            source.input(),
            Mapper::new(width, height, p.scale),
        )?))
    }

    fn close(mut self: Box<Self>, keep_output: bool) -> Result<()> {
        if keep_output && matches!(&self.source, Some(GnomeSource::Virtual(_))) {
            warn!("--keep-output: Mutter removes a virtual monitor with its session");
        }
        self.stop_capture();
        if let Some(mut source) = self.source.take() {
            source.stop()?;
            if matches!(&source, GnomeSource::Virtual(_)) {
                info!("removed virtual display {}", self.info.name);
            } else {
                info!("stopped mirroring display {}", self.info.name);
            }
        }
        Ok(())
    }
}

impl Drop for GnomeDisplay {
    fn drop(&mut self) {
        self.stop_capture();
        // VirtualMonitor/MonitorStream's own Drop stops the session.
    }
}
