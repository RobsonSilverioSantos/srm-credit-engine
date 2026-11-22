import csv
import io
from pathlib import Path
import shutil
import subprocess
import sys
from tempfile import TemporaryDirectory
import unittest
from xml.etree import ElementTree
from zipfile import ZipFile
from fixtures_importacao import DOCUMENTOS, ler_xlsx, preparar


DESTINO = Path(__file__).resolve().parents[1] / "public/modelos"
NS = {"s": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}


class ModelosLoteTest(unittest.TestCase):
    def test_csv_xlsx_equivalentes_e_celulas_textuais(self):
        for sufixo, campo, marcador in [
            ("", "cedenteId", "CEDENTE_ID_AQUI"),
            ("-cnpj", "cedenteDocumento", "CEDENTE_CNPJ_AQUI"),
        ]:
            with self.subTest(modelo=campo):
                linhas = list(csv.reader(
                    io.StringIO((DESTINO / f"modelo-lote{sufixo}.csv").read_text(encoding="utf-8")),
                    delimiter=";",
                ))
                self.assertEqual(linhas[0], [
                    "referenciaExterna", campo, "modalidade", "valorFaceBrl",
                    "prazoMeses", "vencimento", "moedaPagamento",
                ])
                self.assertEqual(linhas[1][1], marcador)
                with ZipFile(DESTINO / f"modelo-lote{sufixo}.xlsx") as arquivo:
                    planilha = ElementTree.fromstring(arquivo.read("xl/worksheets/sheet1.xml"))
                    if sufixo:
                        estilos = ElementTree.fromstring(arquivo.read("xl/styles.xml"))
                        indice = int(planilha.find("s:cols/s:col", NS).attrib["style"])
                        self.assertEqual(estilos.find("s:cellXfs", NS)[indice].attrib["numFmtId"], "49")
                        self.assertEqual(planilha.find('.//s:c[@r="B2"]', NS).attrib["s"], str(indice))
                celulas = planilha.findall(".//s:c", NS)
                self.assertTrue(all(c.attrib["t"] == "inlineStr" for c in celulas))
                self.assertFalse(planilha.findall(".//s:f", NS))
                self.assertEqual([
                    [c.find("s:is/s:t", NS).text for c in linha]
                    for linha in planilha.findall(".//s:row", NS)
                ], linhas)

    def test_geracao_reproduz_artefatos_publicados(self):
        nomes = [
            "modelo-lote.csv", "modelo-lote.xlsx", "modelo-lote-cnpj.csv",
            "modelo-lote-cnpj.xlsx", "lote-a-200.csv", "lote-b-200.csv",
        ]
        publicados = {nome: (DESTINO / nome).read_bytes() for nome in nomes}
        with TemporaryDirectory(prefix="srm-modelos-") as temporario:
            subprocess.run([
                sys.executable, str(Path(__file__).with_name("gerar-modelos-lote.py")),
                "--destino", temporario,
            ], check=True)
            self.assertEqual(set(nomes), {p.name for p in Path(temporario).iterdir()})
            self.assertEqual(publicados, {nome: (Path(temporario) / nome).read_bytes() for nome in nomes})

    def test_massas_preservam_200_linhas_e_quatro_referencias(self):
        massas = []
        for nome in ["lote-a-200.csv", "lote-b-200.csv"]:
            with (DESTINO / nome).open(encoding="utf-8-sig", newline="") as arquivo:
                linhas = list(csv.DictReader(arquivo, delimiter=";"))
            self.assertEqual(len(linhas), 200)
            massas.append([linha["referenciaExterna"] for linha in linhas])
        repetidos = set(massas[0]) & set(massas[1])
        self.assertEqual(len(repetidos), 4)
        self.assertEqual(sum(massas[0].index(ref) == massas[1].index(ref) for ref in repetidos), 2)

    @unittest.skipUnless(shutil.which("libreoffice"), "Round-trip XLSX requer LibreOffice instalado.")
    def test_xlsx_preenchido_preserva_documentos_apos_salvar_no_calc(self):
        with TemporaryDirectory(prefix="srm-xlsx-roundtrip-") as temporario:
            raiz = Path(temporario)
            fixtures = preparar(raiz / "entrada")
            planilhas = [f for f in fixtures if f["arquivo"].endswith(".xlsx") and "cnpj-" in f["arquivo"]]
            saida = raiz / "saida"
            saida.mkdir()
            subprocess.run([
                "libreoffice", f"-env:UserInstallation={(raiz / 'perfil').as_uri()}",
                "--headless", "--convert-to", "xlsx", "--outdir", str(saida),
                *[f["arquivo"] for f in planilhas],
            ], check=True, capture_output=True, timeout=60)
            for fixture, documento in zip(planilhas, DOCUMENTOS):
                with self.subTest(documento=documento):
                    linhas = ler_xlsx(saida / Path(fixture["arquivo"]).name)
                    self.assertEqual(linhas, fixture["linhas"])
                    self.assertEqual(linhas[1][1], documento)
