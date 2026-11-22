"""Reproduce B16/B18 templates and demonstrative A/B fixtures without dependencies."""

import argparse
import csv
import io
from pathlib import Path
from xml.sax.saxutils import escape
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

root = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--destino", type=Path, default=root / "frontend/public/modelos")
destino = parser.parse_args().destino
destino.mkdir(parents=True, exist_ok=True)
cabecalho = [
    "referenciaExterna", "cedenteId", "modalidade", "valorFaceBrl",
    "prazoMeses", "vencimento", "moedaPagamento",
]
exemplo = [
    "MINHA-REFERENCIA-001", "CEDENTE_ID_AQUI", "DUPLICATA_MERCANTIL",
    "25000.00", "2", "2027-02-04", "BRL",
]
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
for sufixo, campo, marcador in [
    ("", "cedenteId", "CEDENTE_ID_AQUI"),
    ("-cnpj", "cedenteDocumento", "CEDENTE_CNPJ_AQUI"),
]:
    buffer = io.StringIO(newline="")
    colunas = [*cabecalho]
    valores = [*exemplo]
    colunas[1], valores[1] = campo, marcador
    csv.writer(buffer, delimiter=";", lineterminator="\n").writerows([colunas, valores])
    (destino / f"modelo-lote{sufixo}.csv").write_text(buffer.getvalue(), encoding="utf-8")
    conteudo = dict(partes)
    if sufixo == "-cnpj":
        conteudo["[Content_Types].xml"] = conteudo["[Content_Types].xml"].replace(
            "</Types>",
            '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>',
        )
        conteudo["xl/_rels/workbook.xml.rels"] = conteudo["xl/_rels/workbook.xml.rels"].replace(
            "</Relationships>",
            f'<Relationship Id="rId2" Type="{rels}/styles" Target="styles.xml"/></Relationships>',
        )
        conteudo["xl/styles.xml"] = (
            f'<styleSheet xmlns="{ns}">'
            '<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>'
            '<fills count="2"><fill><patternFill patternType="none"/></fill>'
            '<fill><patternFill patternType="gray125"/></fill></fills>'
            '<borders count="1"><border/></borders>'
            '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>'
            '<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>'
            '<xf numFmtId="49" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs>'
            '<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>'
            '</styleSheet>'
        )
        conteudo["xl/worksheets/sheet1.xml"] = conteudo["xl/worksheets/sheet1.xml"].replace(
            "<sheetData>", '<cols><col min="2" max="2" width="24" customWidth="1" style="1"/></cols><sheetData>',
        ).replace('<c r="B2"', '<c r="B2" s="1"')
    with ZipFile(destino / f"modelo-lote{sufixo}.xlsx", "w") as arquivo:
        for nome, xml in conteudo.items():
            info = ZipInfo(nome, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = ZIP_DEFLATED
            arquivo.writestr(info, '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                             + xml.replace("cedenteId", campo).replace("CEDENTE_ID_AQUI", marcador))

for origem, nome in [
    ("SRM_LOTE_A_200_RECEBIVEIS.csv", "lote-a-200.csv"),
    ("SRM_LOTE_B_200_RECEBIVEIS_COM_4_REPETIDOS.csv", "lote-b-200.csv"),
]:
    (destino / nome).write_bytes((root / "docs/validacao/lotes" / origem).read_bytes())
