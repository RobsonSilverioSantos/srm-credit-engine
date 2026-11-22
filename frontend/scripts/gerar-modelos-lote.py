"""Reproduce the public B16 templates and demonstrative A/B fixtures without dependencies."""

import csv
import io
from pathlib import Path
from xml.sax.saxutils import escape
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

root = Path(__file__).resolve().parents[2]
destino = root / "frontend/public/modelos"
destino.mkdir(parents=True, exist_ok=True)
cabecalho = [
    "referenciaExterna", "cedenteId", "modalidade", "valorFaceBrl",
    "prazoMeses", "vencimento", "moedaPagamento",
]
exemplo = [
    "MINHA-REFERENCIA-001", "CEDENTE_ID_AQUI", "DUPLICATA_MERCANTIL",
    "25000.00", "2", "2027-02-04", "BRL",
]
buffer = io.StringIO(newline="")
csv.writer(buffer, delimiter=";", lineterminator="\n").writerows([cabecalho, exemplo])
(destino / "modelo-lote.csv").write_text(buffer.getvalue(), encoding="utf-8")
ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
rels = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
partes = {
    "[Content_Types].xml": (
        '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
        '<Default Extension="xml" ContentType="application/xml"/>'
        '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
        '<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
        '</Types>'
    ),
    "_rels/.rels": (
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        f'<Relationship Id="rId1" Type="{rels}/officeDocument" Target="xl/workbook.xml"/>'
        '</Relationships>'
    ),
    "xl/workbook.xml": (
        f'<workbook xmlns="{ns}" xmlns:r="{rels}">'
        '<sheets><sheet name="Recebiveis" sheetId="1" r:id="rId1"/></sheets></workbook>'
    ),
    "xl/_rels/workbook.xml.rels": (
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        f'<Relationship Id="rId1" Type="{rels}/worksheet" Target="worksheets/sheet1.xml"/>'
        '</Relationships>'
    ),
    "xl/worksheets/sheet1.xml": (
        f'<worksheet xmlns="{ns}"><sheetData>'
        + "".join(
            f'<row r="{linha}">'
            + "".join(
                f'<c r="{chr(65 + coluna)}{linha}" t="inlineStr"><is><t>{escape(valor)}</t></is></c>'
                for coluna, valor in enumerate(valores)
            )
            + "</row>"
            for linha, valores in enumerate([cabecalho, exemplo], 1)
        )
        + "</sheetData></worksheet>"
    ),
}
with ZipFile(destino / "modelo-lote.xlsx", "w") as arquivo:
    for nome, xml in partes.items():
        info = ZipInfo(nome, date_time=(2026, 1, 1, 0, 0, 0))
        info.compress_type = ZIP_DEFLATED
        arquivo.writestr(info, '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' + xml)

for origem, nome in [
    ("SRM_LOTE_A_200_RECEBIVEIS.csv", "lote-a-200.csv"),
    ("SRM_LOTE_B_200_RECEBIVEIS_COM_4_REPETIDOS.csv", "lote-b-200.csv"),
]:
    (destino / nome).write_bytes((root / "docs/validacao/lotes" / origem).read_bytes())
