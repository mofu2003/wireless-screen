use std::ffi::OsString;
use std::io::{self, IsTerminal, Write};

use anyhow::{Result, bail};
use od_update::{Component, InstallScope, UpdateOptions};

const VERSION: &str = env!("OD_APP_VERSION");

fn main() {
    let code = match run() {
        Ok(code) => code,
        Err(error) => {
            eprintln!("wireless-screen: {error:#}");
            1
        }
    };
    std::process::exit(code);
}

fn run() -> Result<i32> {
    let mut args = std::env::args_os();
    let _program = args.next();
    let Some(role) = args.next() else {
        if io::stdin().is_terminal() {
            return menu();
        }
        print_help();
        return Ok(0);
    };
    let role = role
        .to_str()
        .ok_or_else(|| anyhow::anyhow!("参数不是有效的 UTF-8"))?;
    let rest: Vec<OsString> = args.collect();
    match role {
        "send" | "connect" => {
            if requests_version(&rest) {
                println!("wireless-screen {VERSION}");
                return Ok(0);
            }
            Ok(run_sender("send", rest))
        }
        "receive" => {
            if requests_version(&rest) {
                println!("wireless-screen {VERSION}");
                return Ok(0);
            }
            if rest
                .first()
                .and_then(|arg| arg.to_str())
                .is_some_and(|arg| arg == "update")
            {
                return update(rest.into_iter().skip(1));
            }
            let args = std::iter::once(OsString::from("wireless-screen receive")).chain(rest);
            od_receiver::cli::main_from(args)?;
            Ok(0)
        }
        "list" | "doctor" | "disconnect" | "clean" | "dev" => Ok(run_sender(role, rest)),
        "update" => update(rest),
        "menu" => menu(),
        "-V" | "--version" => {
            println!("wireless-screen {VERSION}");
            Ok(0)
        }
        "-h" | "--help" | "help" => {
            print_help();
            Ok(0)
        }
        other => {
            eprintln!("wireless-screen: 未知命令 {other:?}\n");
            print_help();
            Ok(2)
        }
    }
}

fn requests_version(args: &[OsString]) -> bool {
    matches!(
        args,
        [arg] if matches!(arg.to_str(), Some("-V" | "--version"))
    )
}

fn run_sender(command: &str, rest: Vec<OsString>) -> i32 {
    let args = std::iter::once(OsString::from("wireless-screen"))
        .chain(std::iter::once(OsString::from(command)))
        .chain(rest);
    od_sender::main_from(args)
}

fn update<I>(args: I) -> Result<i32>
where
    I: IntoIterator<Item = OsString>,
{
    let mut check = false;
    let mut scope = None;
    for arg in args {
        match arg.to_str() {
            Some("--check") => check = true,
            Some("--system") if scope != Some(InstallScope::User) => {
                scope = Some(InstallScope::System);
            }
            Some("--user") if scope != Some(InstallScope::System) => {
                scope = Some(InstallScope::User);
            }
            Some("-h" | "--help") => {
                println!(
                    "用法：wireless-screen update [--check] [--system | --user]\n\
                     \n\
                     检查并安装最新的 Linux 无线副屏。系统级安装使用 sudo。"
                );
                return Ok(0);
            }
            Some(other) => bail!("update 未知参数：{other}"),
            None => bail!("update 参数不是有效的 UTF-8"),
        }
    }
    od_update::run(Component::App, VERSION, UpdateOptions { check, scope })?;
    Ok(0)
}

fn menu() -> Result<i32> {
    println!(
        "\
无线副屏 {VERSION}

  1  发送屏幕到 Mac / Android
  2  接收 Mac / Android / Linux 的投屏
  3  查找接收端
  4  检查更新
  5  环境诊断
  q  退出
"
    );
    loop {
        print!("请选择：");
        io::stdout().flush()?;
        let mut line = String::new();
        if io::stdin().read_line(&mut line)? == 0 {
            return Ok(0);
        }
        match line.trim() {
            "1" => return Ok(run_sender("send", Vec::new())),
            "2" => {
                let args = std::iter::once(OsString::from("wireless-screen receive"));
                od_receiver::cli::main_from(args)?;
                return Ok(0);
            }
            "3" => return Ok(run_sender("list", Vec::new())),
            "4" => return update(Vec::new()),
            "5" => return Ok(run_sender("doctor", Vec::new())),
            "q" | "Q" => return Ok(0),
            "" => {}
            _ => println!("请输入 1–5 或 q。"),
        }
    }
}

fn print_help() {
    println!(
        "\
无线副屏 {VERSION} — 一个软件，既能发送也能接收

用法：
  wireless-screen                    打开终端菜单
  wireless-screen send [选项]        发送这台 Linux 屏幕
  wireless-screen receive [选项]     用这台 Linux 接收投屏
  wireless-screen list [选项]        查找局域网接收端
  wireless-screen doctor [选项]      检查发送环境和桌面协议
  wireless-screen disconnect         断开正在运行的发送会话
  wireless-screen clean              清理异常退出留下的虚拟显示器
  wireless-screen update [--check]   检查或安装更新

发送和接收的高级参数：
  wireless-screen send --help
  wireless-screen receive --help

示例：
  wireless-screen send --to Mac
  wireless-screen send --mode mirror --output eDP-1
  wireless-screen receive --windowed
  sudo wireless-screen update"
    );
}
