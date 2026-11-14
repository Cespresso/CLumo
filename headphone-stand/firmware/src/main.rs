#[cfg(target_os = "espidf")]
mod device;
#[cfg(target_os = "espidf")]
fn main() {
    if let Err(e) = device::run() {
        log::error!("Stand stopped: {e}");
        loop {
            std::thread::sleep(std::time::Duration::from_secs(1));
        }
    }
}
#[cfg(not(target_os = "espidf"))]
fn main() {
    panic!("Build the device binary for riscv32imc-esp-espidf");
}
