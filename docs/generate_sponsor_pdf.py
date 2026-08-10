from reportlab.lib.pagesizes import letter
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import inch
from reportlab.platypus import (
    SimpleDocTemplate,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
    HRFlowable,
)
from reportlab.lib.enums import TA_CENTER
from datetime import date
import os

out_path = r"C:\xampp\htdocs\vitalfi\docs\VitalFi_Rescue_Lista_Patrocinio.pdf"
os.makedirs(os.path.dirname(out_path), exist_ok=True)

doc = SimpleDocTemplate(
    out_path,
    pagesize=letter,
    leftMargin=0.65 * inch,
    rightMargin=0.65 * inch,
    topMargin=0.6 * inch,
    bottomMargin=0.6 * inch,
)

brand = colors.HexColor("#0B3D2E")
accent = colors.HexColor("#C45C26")
light = colors.HexColor("#F4F1EA")
line = colors.HexColor("#D0D0D0")
muted = colors.HexColor("#555555")

styles = getSampleStyleSheet()
styles.add(
    ParagraphStyle(
        name="TitleMain",
        fontName="Helvetica-Bold",
        fontSize=15,
        textColor=brand,
        alignment=TA_CENTER,
        spaceAfter=4,
    )
)
styles.add(
    ParagraphStyle(
        name="Sub",
        fontName="Helvetica",
        fontSize=9,
        textColor=muted,
        alignment=TA_CENTER,
        spaceAfter=8,
    )
)
styles.add(
    ParagraphStyle(
        name="H2",
        fontName="Helvetica-Bold",
        fontSize=11,
        textColor=brand,
        spaceBefore=10,
        spaceAfter=6,
    )
)
styles.add(
    ParagraphStyle(
        name="Body",
        fontName="Helvetica",
        fontSize=8.5,
        textColor=colors.black,
        leading=11,
        spaceAfter=4,
    )
)
styles.add(
    ParagraphStyle(
        name="Small",
        fontName="Helvetica",
        fontSize=7.5,
        textColor=muted,
        leading=9.5,
    )
)
styles.add(ParagraphStyle(name="Cell", fontName="Helvetica", fontSize=8, leading=10))
styles.add(
    ParagraphStyle(name="CellB", fontName="Helvetica-Bold", fontSize=8, leading=10)
)

story = []
story.append(
    Paragraph(
        "VITALFI RESCUE — LISTA DE LO NECESARIO",
        styles["TitleMain"],
    )
)
story.append(
    Paragraph(
        "Pedido minimo para prototipo portable (prueba de campo)<br/>"
        f"Patrocinio · Amazon.com (USD) · {date.today().isoformat()} · Precios estimados",
        styles["Sub"],
    )
)
story.append(HRFlowable(width="100%", thickness=1.5, color=brand, spaceAfter=8))

# Necessary only
story.append(Paragraph("Lo necesario a comprar ahora", styles["H2"]))

items = [
    (
        "1",
        "Xiaomi Redmi Pad 2 Pro 12.1\" 128GB+6GB",
        "B0FY784GKX",
        1,
        247.50,
        "Tablet mando / demo",
    ),
    (
        "2",
        "Xiaomi Redmi 15 4G 256GB+8GB Titan Gray",
        "B0FN62V4RS",
        1,
        202.78,
        "App VitalFi + hotspot",
    ),
    (
        "3",
        "LoRa V3 915 MHz x2 (Heltec-compatible)",
        "B0CW6151WJ",
        1,
        44.98,
        "Alerta remota campo-mando",
    ),
    (
        "4",
        "Pantalla ESP32-S3 2.8\" tactil",
        "B0FKG7WRWV",
        1,
        18.99,
        "Consola embebida",
    ),
    (
        "5",
        "Antenas LoRa 915 MHz x3",
        "B0FJFK1YW1",
        1,
        11.95,
        "Alcance LoRa",
    ),
    (
        "6",
        "Antenas Wi-Fi 2.4 GHz + U.FL x3",
        "B0BX2HG26Y",
        1,
        9.99,
        "Alcance CSI / Wi-Fi",
    ),
    (
        "7",
        "Expansion GPIO ESP32-S3",
        "B0GGB966MD",
        1,
        19.99,
        "Breakout / alimentacion",
    ),
    (
        "8",
        "LiPo 3.7V 1100 mAh JST 1.25 x2",
        "B0DTDLGDN1",
        1,
        14.99,
        "Bateria LoRa",
    ),
    (
        "9",
        "Jumpers DuPont (kit)",
        "B072L1XMJR",
        1,
        6.99,
        "UART CSI-LoRa",
    ),
    (
        "10",
        "Raspberry Pi 5 Starter Kit 8GB (CanaKit)",
        "B0CVQT445P",
        1,
        219.99,
        "Nodo edge / procesamiento",
    ),
    (
        "11",
        "Raspberry Pi AI HAT+ 2 (Hailo-10H 40 TOPS)",
        "B0GK251MF3",
        1,
        130.00,
        "Aceleracion AI on-device",
    ),
]

rows = [
    [
        Paragraph("<b>#</b>", styles["CellB"]),
        Paragraph("<b>Producto necesario</b>", styles["CellB"]),
        Paragraph("<b>ASIN</b>", styles["CellB"]),
        Paragraph("<b>Qty</b>", styles["CellB"]),
        Paragraph("<b>P. unit.</b>", styles["CellB"]),
        Paragraph("<b>Subtotal</b>", styles["CellB"]),
        Paragraph("<b>Para que</b>", styles["CellB"]),
    ]
]

grand = 0.0
for num, name, asin, qty, unit, use in items:
    sub = qty * unit
    grand += sub
    rows.append(
        [
            Paragraph(num, styles["Cell"]),
            Paragraph(name, styles["Cell"]),
            Paragraph(asin, styles["Cell"]),
            Paragraph(str(qty), styles["Cell"]),
            Paragraph(f"${unit:,.2f}", styles["Cell"]),
            Paragraph(f"${sub:,.2f}", styles["CellB"]),
            Paragraph(use, styles["Cell"]),
        ]
    )

t2 = Table(
    rows,
    colWidths=[
        0.3 * inch,
        2.5 * inch,
        1.15 * inch,
        0.35 * inch,
        0.6 * inch,
        0.65 * inch,
        1.35 * inch,
    ],
)
style_cmds = [
    ("BACKGROUND", (0, 0), (-1, 0), brand),
    ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
    ("GRID", (0, 0), (-1, -1), 0.35, line),
    ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ("LEFTPADDING", (0, 0), (-1, -1), 3),
    ("RIGHTPADDING", (0, 0), (-1, -1), 3),
    ("TOPPADDING", (0, 0), (-1, -1), 4),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
]
for i in range(1, len(rows)):
    if i % 2 == 0:
        style_cmds.append(("BACKGROUND", (0, i), (-1, i), light))
t2.setStyle(TableStyle(style_cmds))
story.append(t2)

# Envio US -> Venezuela (casillero / courier estimado para ~3-5 kg electronicos)
shipping_ve = 120.00
# Gastos de entrada / aduana / traslado interno (10% sobre mercancia)
entry_pct = 0.10
entry_fees = round(grand * entry_pct, 2)
total_rec = grand + shipping_ve + entry_fees

story.append(Spacer(1, 8))
sum_data = [
    [
        Paragraph("Subtotal (lo necesario)", styles["Cell"]),
        Paragraph(f"${grand:,.2f}", styles["CellB"]),
    ],
    [
        Paragraph("Envio a Venezuela (estimado)", styles["Cell"]),
        Paragraph(f"${shipping_ve:,.2f}", styles["CellB"]),
    ],
    [
        Paragraph("Gastos de entrada / traslado (10%)", styles["Cell"]),
        Paragraph(f"${entry_fees:,.2f}", styles["CellB"]),
    ],
    [
        Paragraph("<b>TOTAL A SOLICITAR</b>", styles["CellB"]),
        Paragraph(f"<b>${total_rec:,.2f} USD</b>", styles["CellB"]),
    ],
]
ts = Table(sum_data, colWidths=[5.4 * inch, 1.8 * inch])
ts.setStyle(
    TableStyle(
        [
            ("BACKGROUND", (0, 3), (-1, 3), brand),
            ("TEXTCOLOR", (0, 3), (-1, 3), colors.white),
            ("BACKGROUND", (0, 0), (-1, 2), light),
            ("GRID", (0, 0), (-1, -1), 0.4, line),
            ("LEFTPADDING", (0, 0), (-1, -1), 6),
            ("RIGHTPADDING", (0, 0), (-1, -1), 6),
            ("TOPPADDING", (0, 0), (-1, -1), 5),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ]
    )
)
story.append(ts)

story.append(Paragraph("Enlaces Amazon", styles["H2"]))
story.append(
    Paragraph(
        "1 Tablet https://www.amazon.com/dp/B0FY784GKX<br/>"
        "2 Telefono https://www.amazon.com/dp/B0FN62V4RS<br/>"
        "3 LoRa x2 https://www.amazon.com/dp/B0CW6151WJ<br/>"
        "4 Pantalla 2.8\" https://www.amazon.com/dp/B0FKG7WRWV<br/>"
        "5 Antenas 915 https://www.amazon.com/dp/B0FJFK1YW1<br/>"
        "6 Antenas 2.4 https://www.amazon.com/dp/B0BX2HG26Y<br/>"
        "7 GPIO https://www.amazon.com/dp/B0GGB966MD<br/>"
        "8 LiPo https://www.amazon.com/dp/B0DTDLGDN1<br/>"
        "9 Jumpers https://www.amazon.com/dp/B072L1XMJR<br/>"
        "10 Raspberry Pi 5 https://www.amazon.com/dp/B0CVQT445P<br/>"
        "11 AI HAT+ 2 https://www.amazon.com/dp/B0GK251MF3",
        styles["Small"],
    )
)

story.append(Spacer(1, 8))
story.append(
    Paragraph(
        "<b>Nota:</b> Prototipo experimental. No sustituye equipos certificados USAR. "
        "Verificar LoRa <b>915 MHz (USA)</b>. Precios Amazon pueden variar. "
        "Envio a Venezuela: estimado casillero/courier (~3–5 kg); ajustar segun cotizacion real. "
        "El 10% cubre gastos de entrada, aduana y traslado interno.",
        styles["Body"],
    )
)
story.append(HRFlowable(width="100%", thickness=1, color=line, spaceBefore=8, spaceAfter=4))
story.append(
    Paragraph(
        "VitalFi Rescue Pack · Solo lo necesario · Solicitud de patrocinio",
        styles["Sub"],
    )
)

doc.build(story)
print(out_path)
print(f"SUBTOTAL={grand:.2f}")
print(f"SHIPPING_VE={shipping_ve:.2f}")
print(f"ENTRY_10PCT={entry_fees:.2f}")
print(f"TOTAL={total_rec:.2f}")
