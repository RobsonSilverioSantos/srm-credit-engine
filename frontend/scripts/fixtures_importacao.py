"""Filled public-template fixtures for UI tests; not a document resolver."""

import csv
import io
import json
from pathlib import Path
import sys
from xml.etree import ElementTree
from zipfile import ZipFile

MODELOS = Path(__file__).resolve().parents[1] / "public/modelos"
NS = {"s": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
DOCUMENTOS = ["00000000000191", "00.000.000/0001-91", "12ABC34501DE35", "12.abc.345/01de-35"]
CEDENTE_ID = "00000000-0000-4000-8000-000000000001"


def preencher_xlsx(origem, destino, marcador, valor):
    with ZipFile(origem) as modelo, ZipFile(destino, "w") as arquivo:
        for info in modelo.infolist():
            conteudo = modelo.read(info.filename)
            if info.filename == "xl/worksheets/sheet1.xml":
                conteudo = conteudo.replace(marcador.encode(), valor.encode())
            arquivo.writestr(info, conteudo)


def ler_xlsx(caminho):
    with ZipFile(caminho) as arquivo:
        planilha = ElementTree.fromstring(arquivo.read("xl/worksheets/sheet1.xml"))
        textos = []
        if "xl/sharedStrings.xml" in arquivo.namelist():
            textos = ["".join(si.itertext()) for si in ElementTree.fromstring(arquivo.read("xl/sharedStrings.xml"))]
    linhas = []
    for linha in planilha.findall(".//s:sheetData/s:row", NS):
        valores = [""] * 7
        for celula in linha:
            if celula.tag != f'{{{NS["s"]}}}c':
                continue
            coluna = ord(celula.attrib["r"][0]) - ord("A")
            if not 0 <= coluna < 7:
                continue
            tipo = celula.attrib.get("t")
            if tipo == "inlineStr":
                valor = "".join(celula.find("s:is", NS).itertext())
            else:
                valor = celula.findtext("s:v", "", NS)
                if tipo == "s":
                    valor = textos[int(valor)]
            valores[coluna] = valor
        if any(valores):
            linhas.append(valores)
    return linhas


def preparar(destino):
    destino.mkdir(parents=True, exist_ok=True)
    fixtures = []
    for indice, documento in enumerate(DOCUMENTOS):
        for formato in ["csv", "xlsx"]:
            caminho = destino / f"cnpj-{indice}.{formato}"
            if formato == "csv":
                caminho.write_text(
                    (MODELOS / "modelo-lote-cnpj.csv").read_text().replace("CEDENTE_CNPJ_AQUI", documento),
                    encoding="utf-8",
                )
                linhas = list(csv.reader(io.StringIO(caminho.read_text()), delimiter=";"))
            else:
                preencher_xlsx(MODELOS / "modelo-lote-cnpj.xlsx", caminho, "CEDENTE_CNPJ_AQUI", documento)
                linhas = ler_xlsx(caminho)
            if linhas[1][1] != documento:
                raise AssertionError("Documento da fixture foi alterado.")
            fixtures.append({"arquivo": str(caminho), "linhas": linhas})
    for formato in ["csv", "xlsx"]:
        caminho = destino / f"uuid.{formato}"
        if formato == "csv":
            caminho.write_text((MODELOS / "modelo-lote.csv").read_text().replace("CEDENTE_ID_AQUI", CEDENTE_ID))
            linhas = list(csv.reader(io.StringIO(caminho.read_text()), delimiter=";"))
        else:
            preencher_xlsx(MODELOS / "modelo-lote.xlsx", caminho, "CEDENTE_ID_AQUI", CEDENTE_ID)
            linhas = ler_xlsx(caminho)
        fixtures.append({"arquivo": str(caminho), "linhas": linhas})
    for letra in ["a", "b"]:
        linhas = list(csv.reader(io.StringIO((MODELOS / f"lote-{letra}-200.csv").read_text()), delimiter=";"))
        linhas[0][1] = "cedenteDocumento"
        for linha in linhas[1:]:
            linha[1] = DOCUMENTOS[0]
        caminho = destino / f"lote-{letra}-cnpj.csv"
        with caminho.open("w", encoding="utf-8", newline="") as arquivo:
            csv.writer(arquivo, delimiter=";", lineterminator="\n").writerows(linhas)
        fixtures.append({"arquivo": str(caminho), "linhas": linhas})
    return fixtures


if __name__ == "__main__":
    print(json.dumps(preparar(Path(sys.argv[1]))))
