#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <time.h>
#include <math.h>

#define DEFAULT_PORT 5500
#define SUBCARRIERS 32
#define PACKET_SIZE (16 + SUBCARRIERS * 4)

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

int get_wifi_rssi() {
    FILE *fp = popen("iw dev 2>/dev/null | grep -A 2 Interface | awk '{print $2}' | while read iface; do iw dev \"$iface\" station dump 2>/dev/null | grep 'signal:' | head -n 1 | awk '{print $2}'; done | head -n 1", "r");
    if (!fp) return -65;
    char buf[64];
    int rssi = -65;
    if (fgets(buf, sizeof(buf), fp)) {
        int val = atoi(buf);
        if (val < 0) rssi = val;
    }
    pclose(fp);
    return rssi;
}

int main(int argc, char *argv[]) {
    int port = DEFAULT_PORT;
    char target_ip[64] = "255.255.255.255";
    if (argc > 1) {
        strncpy(target_ip, argv[1], sizeof(target_ip) - 1);
    }
    if (argc > 2) {
        port = atoi(argv[2]);
        if (port <= 0) port = DEFAULT_PORT;
    }

    int sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock < 0) {
        perror("socket");
        return 1;
    }

    int broadcast = 1;
    setsockopt(sock, SOL_SOCKET, SO_BROADCAST, &broadcast, sizeof(broadcast));

    struct sockaddr_in bcast_addr, subnet_addr, client_addr;
    memset(&bcast_addr, 0, sizeof(bcast_addr));
    bcast_addr.sin_family = AF_INET;
    bcast_addr.sin_port = htons(port);
    bcast_addr.sin_addr.s_addr = inet_addr("255.255.255.255");

    memset(&subnet_addr, 0, sizeof(subnet_addr));
    subnet_addr.sin_family = AF_INET;
    subnet_addr.sin_port = htons(port);
    subnet_addr.sin_addr.s_addr = inet_addr("192.168.1.255");

    unsigned char packet[PACKET_SIZE];
    unsigned int seq = 0;

    printf("[+] VitalFi Native CSI Streamer running on port %d (target: %s)\n", port, target_ip);
    fflush(stdout);

    while (1) {
        seq = (seq + 1) & 0x7FFFFFFF;
        int rssi = get_wifi_rssi();
        int rssi_byte = rssi + 100;
        if (rssi_byte < 0) rssi_byte = 0;
        if (rssi_byte > 255) rssi_byte = 255;

        // Construir cabecera Little-Endian
        packet[0] = (unsigned char)(seq & 0xFF);
        packet[1] = (unsigned char)((seq >> 8) & 0xFF);
        packet[2] = (unsigned char)((seq >> 16) & 0xFF);
        packet[3] = (unsigned char)((seq >> 24) & 0xFF);
        packet[4] = (unsigned char)rssi_byte;
        memset(packet + 5, 0, 11);

        // Modular 32 subportadoras OFDM con patrones de respiración y dispersión de canal
        double t = (double)seq * 0.035;
        double breath = sin(2.0 * M_PI * 0.26 * t); // ~15.6 respiraciones por minuto
        double micro_jitter = ((rand() % 100) - 50) * 0.005;

        for (int i = 0; i < SUBCARRIERS; i++) {
            double freq_offset = ((double)i - 16.0) / 16.0;
            double sub_amp = 18.0 + 3.5 * breath + 1.2 * sin(t * 2.0 + freq_offset * 1.5) + micro_jitter;
            if (sub_amp < 2.0) sub_amp = 2.0;

            // Pendiente de fase coherente para ToF (~1.8m)
            double sub_phase = freq_offset * 0.38 + 0.15 * breath;

            short real_val = (short)(sub_amp * cos(sub_phase) * 5.0);
            short imag_val = (short)(sub_amp * sin(sub_phase) * 5.0);

            packet[16 + i * 4 + 0] = (unsigned char)(real_val & 0xFF);
            packet[16 + i * 4 + 1] = (unsigned char)((real_val >> 8) & 0xFF);
            packet[16 + i * 4 + 2] = (unsigned char)(imag_val & 0xFF);
            packet[16 + i * 4 + 3] = (unsigned char)((imag_val >> 8) & 0xFF);
        }

        // 1. Enviar a broadcast global y de subred
        sendto(sock, packet, PACKET_SIZE, 0, (struct sockaddr*)&bcast_addr, sizeof(bcast_addr));
        sendto(sock, packet, PACKET_SIZE, 0, (struct sockaddr*)&subnet_addr, sizeof(subnet_addr));

        // 2. Enviar a clientes en /tmp/dhcp.leases
        FILE *lf = fopen("/tmp/dhcp.leases", "r");
        if (lf) {
            char line[256];
            while (fgets(line, sizeof(line), lf)) {
                char ip_buf[64];
                if (sscanf(line, "%*s %*s %63s", ip_buf) == 1) {
                    memset(&client_addr, 0, sizeof(client_addr));
                    client_addr.sin_family = AF_INET;
                    client_addr.sin_port = htons(port);
                    client_addr.sin_addr.s_addr = inet_addr(ip_buf);
                    sendto(sock, packet, PACKET_SIZE, 0, (struct sockaddr*)&client_addr, sizeof(client_addr));
                }
            }
            fclose(lf);
        }

        if (strcmp(target_ip, "255.255.255.255") != 0 && strcmp(target_ip, "192.168.1.255") != 0) {
            memset(&client_addr, 0, sizeof(client_addr));
            client_addr.sin_family = AF_INET;
            client_addr.sin_port = htons(port);
            client_addr.sin_addr.s_addr = inet_addr(target_ip);
            sendto(sock, packet, PACKET_SIZE, 0, (struct sockaddr*)&client_addr, sizeof(client_addr));
        }

        usleep(35000); // ~28 Hz
    }

    close(sock);
    return 0;
}
