//! Setup trial: Wi-Fi only, no BLE initialization and no automatic exit.
use super::{hardware::Hardware, Error};
use esp_idf_svc::{
    eventloop::EspSystemEventLoop,
    hal::modem::Modem,
    http::{server::EspHttpServer, Method},
    io::Write,
    ipv4,
    netif::{EspNetif, NetifConfiguration, NetifStack},
    nvs::{EspDefaultNvsPartition, EspNvs, NvsDefault},
    sys,
    wifi::{AccessPointConfiguration, AuthMethod, Configuration, EspWifi, WifiDriver},
};
use headphone_stand::controls::Bindings;
use headphone_stand::{Settings, SetupButtons, SetupEvent};
use std::io::ErrorKind;
use std::net::UdpSocket;
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

const WIFI_FRAME: [u8; 8] = [0, 0x7e, 0x81, 0x3c, 0x42, 0x18, 0, 0x18];
const PAGE: &str = include_str!("editor.html");
struct EditorState {
    saved: Settings,
    bindings: Bindings,
    nvs: EspNvs<NvsDefault>,
}

pub fn progress_frame(progress: u8) -> [u8; 8] {
    let row = (0xffu16 << (8 - progress.min(8))) as u8;
    [0, 0, 0, row, row, 0, 0, 0]
}

pub fn run(
    mut hw: Hardware,
    modem: Modem,
    partition: EspDefaultNvsPartition,
    nvs: EspNvs<NvsDefault>,
    settings: Settings,
    bindings: Bindings,
    id: [u8; 16],
) -> Result<(), Error> {
    hw.brightness(settings.brightness)?;
    hw.volume(0)?;
    hw.frame(WIFI_FRAME)?;
    let ssid = format!("CLumo-Setup-{:02X}{:02X}", id[0], id[1]);
    let address = ipv4::Ipv4Addr::new(192, 168, 71, 1);
    let mut ap_config = NetifConfiguration::wifi_default_router();
    ap_config.ip_configuration = Some(ipv4::Configuration::Router(ipv4::RouterConfiguration {
        subnet: ipv4::Subnet {
            gateway: address,
            mask: "24".parse().unwrap(),
        },
        dhcp_enabled: true,
        dns: Some(address),
        secondary_dns: None,
    }));
    let driver = WifiDriver::new(modem, EspSystemEventLoop::take()?, Some(partition))?;
    let mut wifi = EspWifi::wrap_all(
        driver,
        EspNetif::new(NetifStack::Sta)?,
        EspNetif::new_with_conf(&ap_config)?,
    )?;
    wifi.set_configuration(&Configuration::AccessPoint(AccessPointConfiguration {
        ssid: ssid.as_str().try_into().unwrap(),
        auth_method: AuthMethod::None,
        channel: 6,
        max_connections: 1,
        ..Default::default()
    }))?;
    wifi.start()?;
    let dns = UdpSocket::bind((address, 53))?;
    dns.set_nonblocking(true)?;
    let mut dns_buffer = [0u8; 513];
    let mut server = EspHttpServer::new(&esp_idf_svc::http::server::Configuration {
        uri_match_wildcard: true,
        ..Default::default()
    })?;
    server.fn_handler("/", Method::Get, |req| {
        req.into_response(
            200,
            Some("OK"),
            &[("Content-Type", "text/html; charset=utf-8")],
        )?
        .write_all(PAGE.as_bytes())
    })?;
    let editor = Arc::new(Mutex::new(EditorState {
        saved: settings,
        bindings,
        nvs,
    }));
    let data = editor.clone();
    server.fn_handler("/api/settings", Method::Get, move |req| {
        let bytes = data.lock().unwrap().saved.encode();
        req.into_response(
            200,
            Some("OK"),
            &[
                ("Content-Type", "application/octet-stream"),
                ("Cache-Control", "no-store"),
            ],
        )?
        .write_all(&bytes)
    })?;
    {
        let data = editor.clone();
        server.fn_handler::<Error, _>("/api/save", Method::Post, move |mut req| {
            if req.header("Content-Type") != Some("application/octet-stream")
                || req
                    .header("Content-Length")
                    .and_then(|s| s.parse::<usize>().ok())
                    != Some(Settings::ENCODED_LEN)
            {
                req.into_response(400, Some("Bad Request"), &[])?
                    .write_all(b"Invalid payload")?;
                return Ok(());
            }
            let mut bytes = [0; Settings::ENCODED_LEN];
            let mut offset = 0;
            while offset < bytes.len() {
                let n = req.read(&mut bytes[offset..])?;
                if n == 0 {
                    break;
                }
                offset += n;
            }
            let Some(settings) = Settings::decode(&bytes).filter(|_| offset == bytes.len()) else {
                req.into_response(400, Some("Bad Request"), &[])?
                    .write_all(b"Invalid settings")?;
                return Ok(());
            };
            let mut editor = data.lock().unwrap();
            if editor.saved != settings {
                if let Err(e) = editor.nvs.set_blob("settings", &settings.encode()) {
                    log::warn!("Save animation: {e}");
                    drop(editor);
                    req.into_response(500, Some("Save failed"), &[])?
                        .write_all(b"Save failed")?;
                    return Ok(());
                }
                editor.saved = settings;
            }
            drop(editor);
            req.into_ok_response()?.write_all(b"OK")?;
            Ok(())
        })?;
    }
    {
        let data = editor.clone();
        server.fn_handler::<Error, _>("/api/bindings", Method::Get, move |req| {
            let bytes = data.lock().unwrap().bindings.encode();
            req.into_response(
                200,
                Some("OK"),
                &[
                    ("Content-Type", "application/octet-stream"),
                    ("Cache-Control", "no-store"),
                ],
            )?
            .write_all(&bytes)?;
            Ok(())
        })?;
        let data = editor.clone();
        server.fn_handler::<Error, _>("/api/bindings", Method::Post, move |mut req| {
            if req.header("Content-Type") != Some("application/octet-stream")
                || req.header("Content-Length") != Some("7")
            {
                req.into_response(400, Some("Bad Request"), &[])?
                    .write_all(b"Invalid payload")?;
                return Ok(());
            }
            let mut bytes = [0; 7];
            let mut offset = 0;
            while offset < 7 {
                let n = req.read(&mut bytes[offset..])?;
                if n == 0 {
                    break;
                }
                offset += n;
            }
            let Some(next) = Bindings::decode(&bytes[..offset]) else {
                req.into_response(400, Some("Bad Request"), &[])?
                    .write_all(b"Invalid bindings")?;
                return Ok(());
            };
            let mut state = data.lock().unwrap();
            if next != state.bindings {
                if state.nvs.set_blob("bindings", &next.encode()).is_err() {
                    drop(state);
                    req.into_response(500, Some("Save failed"), &[])?
                        .write_all(b"Save failed")?;
                    return Ok(());
                }
                state.bindings = next;
            }
            drop(state);
            req.into_ok_response()?.write_all(&bytes)?;
            Ok(())
        })?;
    }
    let portal_url = format!("http://{address}/");
    server.fn_handler("/*", Method::Get, move |req| {
        req.into_response(
            302,
            Some("Found"),
            &[
                ("Location", portal_url.as_str()),
                ("Cache-Control", "no-store"),
                ("Content-Type", "text/plain; charset=utf-8"),
            ],
        )?
        .write_all(b"Open the CLumo setup page.")
    })?;
    log::info!(
        "SETUP ready SSID={} URL=http://{} BLE=off",
        ssid,
        wifi.ap_netif().get_ip_info()?.ip
    );
    let start = Instant::now();
    let mut buttons = SetupButtons::default();
    let mut last_frame = WIFI_FRAME;
    loop {
        // Bounded, nonblocking DNS work keeps physical buttons responsive.
        for _ in 0..4 {
            match dns.recv_from(&mut dns_buffer) {
                Ok((len, peer)) => {
                    if let Some(reply) =
                        headphone_stand::captive_dns::response(&dns_buffer[..len], address.octets())
                    {
                        let _ = dns.send_to(&reply, peer);
                    }
                }
                Err(e) if e.kind() == ErrorKind::WouldBlock => break,
                Err(e) => {
                    log::warn!("Portal DNS: {e}");
                    break;
                }
            }
        }
        let time = start.elapsed().as_millis() as u64;
        let inputs = hw.buttons();
        if buttons.update([inputs[0], inputs[1]], time) == Some(SetupEvent::Toggle) {
            log::info!("SETUP returning to Bluetooth mode");
            unsafe { sys::esp_restart() }
        }
        let frame = buttons
            .progress(time)
            .map(progress_frame)
            .unwrap_or(WIFI_FRAME);
        if frame != last_frame {
            hw.frame(frame)?;
            last_frame = frame;
        }
        std::thread::sleep(Duration::from_millis(1));
    }
}
