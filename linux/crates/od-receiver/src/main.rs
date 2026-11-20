use anyhow::Result;

fn main() -> Result<()> {
    od_receiver::cli::main_from(std::env::args_os())
}
