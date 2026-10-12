/*
 * ==============================================================================
 * VitalFi Real Data Streamer v2.0 for OpenWrt
 * ==============================================================================
 *
 * Extracts REAL wireless telemetry from an OpenWrt router and streams it
 * via UDP to the VitalFi Android app.
 *
 * Modes (auto-detected):
 *   CSI_REAL  — Reads real CSI subcarrier data from the Atheros CSI Tool
 *               kernel module (/dev/CSI_dev). Requires ath9k with CSI patch.
 *   RSSI_REAL — Reads real RSSI, noise floor, SNR, and channel stats from
 *               standard iw commands. Sends 0 subcarriers (NO fake data).
 *
 * Protocol: VitalFi Enhanced UDP v2 (24-byte header + N×4 subcarrier bytes)
 *           Backward-compatible: Android parser detects format via magic 'VF'.
 *
 * Build (cross-compile for router):
 *   <arch>-openwrt-linux-musl-gcc -O2 -o vitalfi-stream vitalfi_stream.c -lm
 *
 * Usage:
 *   vitalfi-stream [TARGET_IP] [PORT] [ROUTER_ID]
 *
 * Copyright (C) 2026 Carlos Mundaray — GPLv3
 * ==============================================================================
 */

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <time.h>
#include <math.h>
#include <fcntl.h>
#include <errno.h>
#include <signal.h>

/* ── Protocol Constants ───────────────────────────────────────────────── */
#define DEFAULT_PORT      5500
#define HEADER_SIZE       24
#define MAX_SUBCARRIERS   114      /* 802.11ac 80 MHz máximo */
#define MAX_PACKET_SIZE   (HEADER_SIZE + MAX_SUBCARRIERS * 4)
#define MAGIC_VF_LO       0x46    /* 'F' — little-endian byte 14 */
#define MAGIC_VF_HI       0x56    /* 'V' — little-endian byte 15 */

/* ── Atheros CSI Tool device path ─────────────────────────────────────── */
#define CSI_DEV_PATH      "/dev/CSI_dev"

/* ── Protocol Flags (header byte 5) ───────────────────────────────────── */
#define FLAG_REAL_CSI     0x01    /* Bit 0: Subcarrier data is from real hardware */
#define FLAG_HAS_NOISE    0x02    /* Bit 1: Noise floor field is valid */
#define FLAG_HAS_RATE     0x04    /* Bit 2: MCS rate field is valid */
#define FLAG_HAS_SURVEY   0x08    /* Bit 3: Channel utilization is valid */
#define FLAG_RSSI_ONLY    0x10    /* Bit 4: RSSI-only mode, no subcarrier data */

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

/* ── Global State ─────────────────────────────────────────────────────── */
static volatile int g_running = 1;
static char g_iface[64] = "";      /* Detected Wi-Fi interface name */
static int  g_csi_fd = -1;         /* File descriptor for /dev/CSI_dev */
static int  g_router_id = 0;       /* Multi-router identifier (0-255) */

/* Cached real values (updated at staggered intervals to minimize CPU) */
static int g_rssi        = -65;    /* dBm, from iw station dump */
static int g_noise       = -95;    /* dBm, from iw survey dump */
static int g_stations    = 0;      /* Connected station count */
static int g_freq        = 2437;   /* Channel center freq MHz */
static int g_bw          = 20;     /* Channel bandwidth MHz */
static int g_busy_pct    = 0;      /* Channel busy % */
static int g_mcs         = 0;      /* TX MCS index */

/* Persistent CSI buffer (for re-sending last known data) */
static short  g_csi_real[MAX_SUBCARRIERS];
static short  g_csi_imag[MAX_SUBCARRIERS];
static int    g_csi_tones = 0;     /* Tones in last valid read */

/* ── Atheros CSI packet header (23 bytes) ─────────────────────────────── */
struct ath_csi_hdr {
    uint16_t buf_len;
    uint16_t channel;
    uint8_t  phy_err;
    int8_t   noise;
    uint8_t  rate;
    uint8_t  bw;           /* 0 = 20 MHz, 1 = 40 MHz */
    uint8_t  num_tones;
    uint8_t  nr;           /* RX antennas */
    uint8_t  nc;           /* TX antennas */
    int8_t   rssi;
    int8_t   rssi_0;
    int8_t   rssi_1;
    int8_t   rssi_2;
    uint16_t payload_len;
    uint16_t csi_len;
} __attribute__((packed));
#define ATH_CSI_HDR_SIZE 23

/* ══════════════════════════════════════════════════════════════════════ */
/*                          SIGNAL HANDLING                             */
/* ══════════════════════════════════════════════════════════════════════ */

static void on_signal(int sig) {
    (void)sig;
    g_running = 0;
}

/* ══════════════════════════════════════════════════════════════════════ */
/*                     SHELL COMMAND HELPERS                            */
/* ══════════════════════════════════════════════════════════════════════ */

/** Run a shell command and return the first line as an integer. */
static int cmd_int(const char *cmd, int fallback) {
    FILE *fp = popen(cmd, "r");
    if (!fp) return fallback;
    char buf[128];
    int val = fallback;
    if (fgets(buf, sizeof(buf), fp)) {
        int v = atoi(buf);
        if (v != 0 || buf[0] == '0') val = v;
    }
    pclose(fp);
    return val;
}

/** Run a shell command and return the first line as a string. */
static int cmd_str(const char *cmd, char *out, int len) {
    FILE *fp = popen(cmd, "r");
    if (!fp) return -1;
    if (fgets(out, len, fp)) {
        char *nl = strchr(out, '\n');
        if (nl) *nl = '\0';
        pclose(fp);
        return 0;
    }
    pclose(fp);
    return -1;
}

/* ══════════════════════════════════════════════════════════════════════ */
/*                   REAL DATA EXTRACTION (iw)                         */
/* ══════════════════════════════════════════════════════════════════════ */

/** Auto-detect the active wireless interface with connected stations. */
static void detect_interface(void) {
    if (cmd_int("iw dev phy0-ap0 station dump 2>/dev/null | grep -c 'Station'", 0) > 0) {
        strncpy(g_iface, "phy0-ap0", sizeof(g_iface) - 1);
        return;
    }
    if (cmd_int("iw dev phy1-ap0 station dump 2>/dev/null | grep -c 'Station'", 0) > 0) {
        strncpy(g_iface, "phy1-ap0", sizeof(g_iface) - 1);
        return;
    }
    if (cmd_str("iw dev 2>/dev/null | awk '/Interface/{print $2}' | head -n1",
                g_iface, sizeof(g_iface)) == 0 && g_iface[0])
        return;

    /* Fallback: probe common names */
    const char *names[] = {"phy0-ap0", "phy1-ap0", "wlan0", "wlan1", "ath0", "ra0", NULL};
    for (int i = 0; names[i]; i++) {
        char p[128];
        snprintf(p, sizeof(p), "/sys/class/net/%s", names[i]);
        if (access(p, F_OK) == 0) {
            strncpy(g_iface, names[i], sizeof(g_iface) - 1);
            return;
        }
    }
    strncpy(g_iface, "phy0-ap0", sizeof(g_iface) - 1);
}

/** Read real RSSI from the first connected station. */
static int read_rssi(void) {
    char cmd[256];
    snprintf(cmd, sizeof(cmd),
        "iw dev %s station dump 2>/dev/null | grep 'signal:' "
        "| head -n1 | awk '{print $2}'", g_iface);
    int v = cmd_int(cmd, -127);
    if (v < -100 || v > 0) {
        /* Check alternate AP interfaces if no station on current */
        const char *alts[] = {"phy0-ap0", "phy1-ap0", "wlan0", "wlan1", NULL};
        for (int i = 0; alts[i]; i++) {
            if (strcmp(g_iface, alts[i]) != 0) {
                snprintf(cmd, sizeof(cmd),
                    "iw dev %s station dump 2>/dev/null | grep 'signal:' "
                    "| head -n1 | awk '{print $2}'", alts[i]);
                v = cmd_int(cmd, -127);
                if (v >= -100 && v <= 0) {
                    strncpy(g_iface, alts[i], sizeof(g_iface) - 1);
                    break;
                }
            }
        }
    }
    return (v >= -100 && v <= 0) ? v : g_rssi;
}

/** Read real noise floor from the active channel survey. */
static int read_noise(void) {
    char cmd[256];
    snprintf(cmd, sizeof(cmd),
        "iw dev %s survey dump 2>/dev/null | grep -A5 'in use' "
        "| awk '/noise/{print $2}'", g_iface);
    int v = cmd_int(cmd, -95);
    return (v >= -120 && v <= -30) ? v : -95;
}

/** Count connected stations across interfaces. */
static int read_stations(void) {
    char cmd[256];
    snprintf(cmd, sizeof(cmd),
        "iw dev %s station dump 2>/dev/null | grep -c 'Station'", g_iface);
    int v = cmd_int(cmd, 0);
    return (v >= 0) ? v : 0;
}

/** Read channel frequency and bandwidth. */
static void read_channel_info(void) {
    char cmd[256];
    snprintf(cmd, sizeof(cmd),
        "iw dev %s info 2>/dev/null | awk '/channel/{print $2}' | head -1",
        g_iface);
    int ch = cmd_int(cmd, 6);
    if (ch >= 1 && ch <= 13)      g_freq = 2407 + ch * 5;
    else if (ch == 14)            g_freq = 2484;
    else if (ch >= 36)            g_freq = 5000 + ch * 5;

    snprintf(cmd, sizeof(cmd),
        "iw dev %s info 2>/dev/null | grep 'width:' | awk '{print $6}'",
        g_iface);
    int bw = cmd_int(cmd, 20);
    if (bw == 20 || bw == 40 || bw == 80 || bw == 160) g_bw = bw;
}

/** Read channel utilization (busy / active × 100). */
static int read_busy(void) {
    char cmd[256];
    snprintf(cmd, sizeof(cmd),
        "iw dev %s survey dump 2>/dev/null | grep -A5 'in use' | "
        "awk '/active/{for(i=1;i<=NF;i++) if($i ~ /^[0-9]+$/) a=$i} "
        "/busy/{for(i=1;i<=NF;i++) if($i ~ /^[0-9]+$/) b=$i} "
        "END{a=a+0; b=b+0; if(a>0) printf \"%%d\", (b*100)/a; else print \"0\"}'", g_iface);
    int v = cmd_int(cmd, 0);
    return (v >= 0 && v <= 100) ? v : 0;
}

/** Read TX MCS index of first station. */
static int read_mcs(void) {
    char cmd[256];
    snprintf(cmd, sizeof(cmd),
        "iw dev %s station dump 2>/dev/null | grep 'tx bitrate' | head -1 "
        "| grep -o 'MCS [0-9]*' | awk '{print $2}'", g_iface);
    int v = cmd_int(cmd, 0);
    return (v >= 0 && v <= 31) ? v : 0;
}

/* ══════════════════════════════════════════════════════════════════════ */
/*               ATHEROS CSI TOOL — REAL CSI EXTRACTION                */
/* ══════════════════════════════════════════════════════════════════════ */

/** Try to open /dev/CSI_dev (Atheros CSI Tool kernel module). */
static int open_csi_device(void) {
    int fd = open(CSI_DEV_PATH, O_RDONLY | O_NONBLOCK);
    if (fd >= 0) {
        printf("[+] Atheros CSI device opened: %s → REAL CSI MODE\n", CSI_DEV_PATH);
        return fd;
    }
    printf("[!] CSI device not available (%s): %s\n", CSI_DEV_PATH, strerror(errno));
    printf("    Running in RSSI-ONLY REAL mode (no simulated data).\n");
    printf("    For real CSI, install the Atheros CSI Tool kernel module:\n");
    printf("    → https://github.com/xieyaxiongfly/Atheros-CSI-Tool\n");
    return -1;
}

/** Extract a 10-bit signed integer from a packed bit stream (ath9k CSI format). */
static int extract_10bit(const uint8_t *buf, int bit_pos) {
    int byte_pos = bit_pos / 8;
    int bit_off  = bit_pos % 8;
    uint32_t val = ((uint32_t)buf[byte_pos]     << 16) |
                   ((uint32_t)buf[byte_pos + 1] <<  8) |
                   ((uint32_t)buf[byte_pos + 2]);
    int shift = 24 - bit_off - 10;
    if (shift < 0) shift = 0;
    val = (val >> shift) & 0x3FF;
    if (val & 0x200) val |= 0xFFFFFC00;  /* Sign-extend from 10 bits */
    return (int32_t)val;
}

/**
 * Read and unpack one CSI frame from /dev/CSI_dev.
 * Returns 0 on success, -1 if no data available.
 *
 * The ath9k CSI format packs each complex value as:
 *   10-bit imaginary + 10-bit real = 20 bits per subcarrier.
 * For SISO (1 TX × 1 RX), the first spatial stream is extracted.
 */
static int read_csi(int fd, struct ath_csi_hdr *hdr) {
    uint8_t buf[4096];
    ssize_t n = read(fd, buf, sizeof(buf));
    if (n < ATH_CSI_HDR_SIZE + 4) return -1;

    memcpy(hdr, buf, ATH_CSI_HDR_SIZE);

    int tones = hdr->num_tones;
    if (tones <= 0 || tones > MAX_SUBCARRIERS) return -1;

    const uint8_t *csi_buf = buf + ATH_CSI_HDR_SIZE;
    int csi_bytes = (int)n - ATH_CSI_HDR_SIZE;
    int bits_needed = tones * 20;

    /* Clamp if buffer is shorter than expected */
    if (csi_bytes * 8 < bits_needed) {
        tones = (csi_bytes * 8) / 20;
        if (tones <= 0) return -1;
    }

    /* Unpack first spatial stream: SISO or first stream of MIMO */
    for (int k = 0; k < tones; k++) {
        int bp = k * 20;
        g_csi_imag[k] = (short)extract_10bit(csi_buf, bp);
        g_csi_real[k] = (short)extract_10bit(csi_buf, bp + 10);
    }
    g_csi_tones = tones;

    /* Update global caches from CSI header (more accurate than iw) */
    g_rssi  = hdr->rssi;
    g_noise = hdr->noise;
    g_freq  = hdr->channel;
    g_bw    = hdr->bw ? 40 : 20;

    return 0;
}

/* ══════════════════════════════════════════════════════════════════════ */
/*                VitalFi UDP v2 PACKET BUILDING                       */
/* ══════════════════════════════════════════════════════════════════════ */

/** Get monotonic uptime in milliseconds. */
static uint32_t uptime_ms(void) {
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) == 0)
        return (uint32_t)(ts.tv_sec * 1000 + ts.tv_nsec / 1000000);
    return 0;
}

/** Clamp an integer to [0, 255] for single-byte encoding. */
static uint8_t clamp_u8(int v) {
    if (v < 0)   return 0;
    if (v > 255) return 255;
    return (uint8_t)v;
}

/**
 * Build a VitalFi Enhanced UDP v2 packet.
 *
 * Header layout (24 bytes):
 *   [0..3]   seq           uint32 LE
 *   [4]      rssi          uint8  (dBm + 100)
 *   [5]      flags         uint8
 *   [6]      noise floor   uint8  (dBm + 100)
 *   [7]      chan busy %    uint8
 *   [8]      MCS index     uint8
 *   [9]      station count uint8
 *   [10]     SNR           uint8  (dB)
 *   [11..12] freq MHz      uint16 LE
 *   [13]     bandwidth     uint8  (20/40/80)
 *   [14..15] magic "VF"    0x46 0x56
 *   [16]     num_subcarr.  uint8
 *   [17]     router_id     uint8
 *   [18..21] uptime ms     uint32 LE
 *   [22..23] reserved
 *
 * Payload: num_subcarriers × 4 bytes (real16LE + imag16LE each)
 *
 * Returns total packet length.
 */
static int build_packet(uint8_t *pkt, uint32_t seq, uint8_t flags,
                        int num_sc, const short *sc_r, const short *sc_i) {
    memset(pkt, 0, HEADER_SIZE);

    /* Sequence number (LE) */
    pkt[0] = (uint8_t)(seq);
    pkt[1] = (uint8_t)(seq >> 8);
    pkt[2] = (uint8_t)(seq >> 16);
    pkt[3] = (uint8_t)(seq >> 24);

    /* RSSI encoded as unsigned (dBm + 100) */
    pkt[4] = clamp_u8(g_rssi + 100);

    /* Flags */
    pkt[5] = flags;

    /* Noise floor */
    pkt[6] = clamp_u8(g_noise + 100);

    /* Channel utilization % */
    pkt[7] = clamp_u8(g_busy_pct);

    /* MCS rate index */
    pkt[8] = clamp_u8(g_mcs);

    /* Station count */
    pkt[9] = clamp_u8(g_stations);

    /* SNR = RSSI − noise */
    int snr = g_rssi - g_noise;
    pkt[10] = clamp_u8(snr);

    /* Channel frequency MHz (LE) */
    pkt[11] = (uint8_t)(g_freq);
    pkt[12] = (uint8_t)(g_freq >> 8);

    /* Bandwidth */
    pkt[13] = (uint8_t)g_bw;

    /* Magic "VF" */
    pkt[14] = MAGIC_VF_LO;
    pkt[15] = MAGIC_VF_HI;

    /* Number of subcarriers */
    pkt[16] = (uint8_t)(num_sc & 0xFF);

    /* Router ID */
    pkt[17] = (uint8_t)(g_router_id & 0xFF);

    /* Uptime timestamp ms (LE) */
    uint32_t ts = uptime_ms();
    pkt[18] = (uint8_t)(ts);
    pkt[19] = (uint8_t)(ts >> 8);
    pkt[20] = (uint8_t)(ts >> 16);
    pkt[21] = (uint8_t)(ts >> 24);

    /* Subcarrier data: real(16LE) + imag(16LE) per subcarrier */
    for (int i = 0; i < num_sc; i++) {
        int off = HEADER_SIZE + i * 4;
        short r  = sc_r ? sc_r[i] : 0;
        short im = sc_i ? sc_i[i] : 0;
        pkt[off + 0] = (uint8_t)(r);
        pkt[off + 1] = (uint8_t)(r >> 8);
        pkt[off + 2] = (uint8_t)(im);
        pkt[off + 3] = (uint8_t)(im >> 8);
    }

    return HEADER_SIZE + num_sc * 4;
}

/* ══════════════════════════════════════════════════════════════════════ */
/*                        NETWORK SEND                                 */
/* ══════════════════════════════════════════════════════════════════════ */

static void send_all(int sock, const uint8_t *pkt, int len, int port,
                     const struct sockaddr_in *bcast,
                     const struct sockaddr_in *subnet,
                     const char *target_ip, uint32_t seq) {
    /* Global broadcast */
    sendto(sock, pkt, len, 0,
           (struct sockaddr *)bcast, sizeof(*bcast));
    /* Subnet broadcast */
    sendto(sock, pkt, len, 0,
           (struct sockaddr *)subnet, sizeof(*subnet));

    /* DHCP clients — send unicast to all connected devices at full rate */
    static char cached_ips[16][64];
    static int cached_ip_count = 0;
    static uint32_t last_lease_check = 0;

    if (seq - last_lease_check >= 30 || cached_ip_count == 0) {
        last_lease_check = seq;
        cached_ip_count = 0;
        FILE *lf = fopen("/tmp/dhcp.leases", "r");
        if (lf) {
            char line[256];
            while (fgets(line, sizeof(line), lf) && cached_ip_count < 16) {
                char ip[64];
                if (sscanf(line, "%*s %*s %63s", ip) == 1 && ip[0]) {
                    strncpy(cached_ips[cached_ip_count++], ip, 63);
                }
            }
            fclose(lf);
        }
    }

    for (int i = 0; i < cached_ip_count; i++) {
        struct sockaddr_in c;
        memset(&c, 0, sizeof(c));
        c.sin_family = AF_INET;
        c.sin_port   = htons(port);
        c.sin_addr.s_addr = inet_addr(cached_ips[i]);
        sendto(sock, pkt, len, 0,
               (struct sockaddr *)&c, sizeof(c));
    }

    /* Specific target override */
    if (target_ip[0] &&
        strcmp(target_ip, "255.255.255.255") != 0 &&
        strcmp(target_ip, "192.168.1.255")   != 0) {
        struct sockaddr_in t;
        memset(&t, 0, sizeof(t));
        t.sin_family = AF_INET;
        t.sin_port   = htons(port);
        t.sin_addr.s_addr = inet_addr(target_ip);
        sendto(sock, pkt, len, 0,
               (struct sockaddr *)&t, sizeof(t));
    }
}

/* ══════════════════════════════════════════════════════════════════════ */
/*                              MAIN                                   */
/* ══════════════════════════════════════════════════════════════════════ */

int main(int argc, char *argv[]) {
    /* ── Parse arguments ──────────────────────────────────────────── */
    int  port = DEFAULT_PORT;
    char target_ip[64] = "255.255.255.255";

    if (argc > 1) strncpy(target_ip, argv[1], sizeof(target_ip) - 1);
    if (argc > 2) { port = atoi(argv[2]); if (port <= 0) port = DEFAULT_PORT; }
    if (argc > 3) { g_router_id = atoi(argv[3]) & 0xFF; }

    signal(SIGINT,  on_signal);
    signal(SIGTERM, on_signal);

    /* ── Detect Wi-Fi interface ───────────────────────────────────── */
    detect_interface();

    printf("=================================================================\n");
    printf("   📡 VitalFi Real Data Streamer v2.0\n");
    printf("=================================================================\n");
    printf("[+] Interface : %s\n", g_iface);
    printf("[+] Router ID : %d\n", g_router_id);
    printf("[+] Target    : %s:%d\n", target_ip, port);

    /* ── Try real CSI device ──────────────────────────────────────── */
    g_csi_fd = open_csi_device();
    int csi_mode = (g_csi_fd >= 0);

    /* ── Initial reads ────────────────────────────────────────────── */
    read_channel_info();
    g_noise    = read_noise();
    g_stations = read_stations();
    g_rssi     = read_rssi();
    g_mcs      = read_mcs();
    g_busy_pct = read_busy();

    printf("[+] Channel   : %d MHz / %d MHz\n", g_freq, g_bw);
    printf("[+] RSSI      : %d dBm\n", g_rssi);
    printf("[+] Noise     : %d dBm\n", g_noise);
    printf("[+] SNR       : %d dB\n", g_rssi - g_noise);
    printf("[+] Stations  : %d\n", g_stations);
    printf("[+] Mode      : %s\n", csi_mode ? "REAL CSI (ath9k)" : "REAL RSSI-ONLY");
    printf("[+] Streaming at ~30 Hz on UDP port %d\n", port);
    printf("=================================================================\n");
    fflush(stdout);

    /* ── Create UDP socket ────────────────────────────────────────── */
    int sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock < 0) { perror("socket"); return 1; }
    int bopt = 1;
    setsockopt(sock, SOL_SOCKET, SO_BROADCAST, &bopt, sizeof(bopt));

    struct sockaddr_in bcast, subnet;
    memset(&bcast, 0, sizeof(bcast));
    bcast.sin_family      = AF_INET;
    bcast.sin_port        = htons(port);
    bcast.sin_addr.s_addr = inet_addr("255.255.255.255");

    memset(&subnet, 0, sizeof(subnet));
    subnet.sin_family      = AF_INET;
    subnet.sin_port        = htons(port);
    subnet.sin_addr.s_addr = inet_addr("192.168.1.255");

    /* ── Main loop ────────────────────────────────────────────────── */
    uint8_t  packet[MAX_PACKET_SIZE];
    uint32_t seq = 0;

    while (g_running) {
        seq = (seq + 1) & 0x7FFFFFFF;

        /* ── Staggered iw reads to keep CPU ≈ 0.1% ─────────────── */
        if (!csi_mode && (seq % 30 == 1))   g_rssi     = read_rssi();
        if (seq % 90  == 1)                 g_noise    = read_noise();
        if (seq % 150 == 1)  { g_stations = read_stations(); g_mcs = read_mcs(); }
        if (seq % 150 == 5)                 g_busy_pct = read_busy();
        if (seq % 900 == 1)                 read_channel_info();

        /* ── Build packet ───────────────────────────────────────── */
        uint8_t flags = FLAG_HAS_NOISE | FLAG_HAS_RATE | FLAG_HAS_SURVEY;
        int  num_sc = 0;

        if (csi_mode) {
            struct ath_csi_hdr hdr;
            if (read_csi(g_csi_fd, &hdr) == 0) {
                /* Fresh real CSI data */
                flags |= FLAG_REAL_CSI;
                num_sc = g_csi_tones;
            } else if (g_csi_tones > 0) {
                /* No new CSI this frame — re-send last known real data */
                flags |= FLAG_REAL_CSI;
                num_sc = g_csi_tones;
            } else {
                /* CSI device open but no data ever received */
                flags |= FLAG_RSSI_ONLY;
            }
        } else {
            /* RSSI-only mode — NO fake subcarrier data */
            flags |= FLAG_RSSI_ONLY;
        }

        int pkt_len = build_packet(packet, seq, flags, num_sc,
                                   g_csi_real, g_csi_imag);

        send_all(sock, packet, pkt_len, port,
                 &bcast, &subnet, target_ip, seq);

        usleep(33000); /* ~30 Hz */
    }

    /* ── Cleanup ──────────────────────────────────────────────────── */
    printf("\n[+] Shutting down VitalFi streamer.\n");
    if (g_csi_fd >= 0) close(g_csi_fd);
    close(sock);
    return 0;
}
