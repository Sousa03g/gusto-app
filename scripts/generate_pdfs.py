import os
import subprocess
import html
import markdown

EDGE_PATH = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
OUTPUT_DIR = r"C:\Users\gleis\OneDrive\Desktop\Gusto_Avaliacao_Previa_DevOps"
BASE_DIR = r"C:\Users\gleis\OneDrive\Desktop\T2"

CSS_STYLESHEET = """
@page {
  size: A4;
  margin: 18mm 15mm 18mm 15mm;
}
body {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  font-size: 10.5pt;
  line-height: 1.55;
  color: #1e293b;
  margin: 0;
  padding: 0;
}
h1 {
  font-size: 19pt;
  font-weight: 700;
  color: #0f172a;
  border-bottom: 2.5px solid #2563eb;
  padding-bottom: 6px;
  margin-top: 0;
  margin-bottom: 12px;
}
h2 {
  font-size: 14pt;
  font-weight: 600;
  color: #0f172a;
  border-bottom: 1px solid #cbd5e1;
  padding-bottom: 4px;
  margin-top: 20px;
  margin-bottom: 8px;
  page-break-after: avoid;
}
h3 {
  font-size: 12pt;
  font-weight: 600;
  color: #334155;
  margin-top: 14px;
  margin-bottom: 6px;
  page-break-after: avoid;
}
h4 {
  font-size: 10.5pt;
  font-weight: 600;
  color: #475569;
  margin-top: 10px;
  margin-bottom: 4px;
}
p, ul, ol {
  margin-top: 0;
  margin-bottom: 8px;
}
li {
  margin-bottom: 4px;
}
table {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: 9pt;
  page-break-inside: avoid;
}
th, td {
  border: 1px solid #cbd5e1;
  padding: 6px 9px;
  text-align: left;
  vertical-align: top;
}
th {
  background-color: #f1f5f9;
  font-weight: 600;
  color: #0f172a;
}
tr:nth-child(even) td {
  background-color: #f8fafc;
}
code {
  font-family: "Cascadia Code", Consolas, "Courier New", monospace;
  font-size: 9pt;
  background-color: #f1f5f9;
  padding: 2px 4px;
  border-radius: 4px;
  color: #0f172a;
  border: 1px solid #e2e8f0;
}
pre {
  background-color: #0f172a;
  color: #f8fafc;
  padding: 10px 12px;
  border-radius: 6px;
  overflow-x: auto;
  font-family: "Cascadia Code", Consolas, "Courier New", monospace;
  font-size: 8.5pt;
  line-height: 1.4;
  margin: 10px 0;
  page-break-inside: avoid;
}
pre code {
  background-color: transparent;
  color: inherit;
  padding: 0;
  border: none;
}
blockquote {
  border-left: 4px solid #2563eb;
  background-color: #eff6ff;
  padding: 6px 12px;
  margin: 10px 0;
  border-radius: 0 4px 4px 0;
  color: #1e3a8a;
  page-break-inside: avoid;
}
hr {
  border: none;
  border-top: 1px solid #e2e8f0;
  margin: 16px 0;
}
.txt-box {
  font-family: "Cascadia Code", Consolas, "Courier New", monospace;
  font-size: 8.5pt;
  line-height: 1.45;
  white-space: pre-wrap;
  word-wrap: break-word;
  background: #ffffff;
  color: #0f172a;
}
.banner {
  background-color: #f8fafc;
  border: 1px solid #e2e8f0;
  border-left: 4px solid #2563eb;
  padding: 8px 12px;
  margin-bottom: 16px;
  border-radius: 4px;
  font-size: 9.5pt;
}
"""

def convert_md_to_pdf(md_path, pdf_path):
    with open(md_path, "r", encoding="utf-8") as f:
        md_text = f.read()

    html_body = markdown.markdown(
        md_text,
        extensions=["tables", "fenced_code", "nl2br"]
    )

    full_html = f"""<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<title>Documento Técnico</title>
<style>{CSS_STYLESHEET}</style>
</head>
<body>
{html_body}
</body>
</html>"""

    temp_html = pdf_path.replace(".pdf", ".temp.html")
    with open(temp_html, "w", encoding="utf-8") as f:
        f.write(full_html)

    cmd = [
        EDGE_PATH,
        "--headless",
        "--disable-gpu",
        "--no-pdf-header-footer",
        f"--print-to-pdf={pdf_path}",
        temp_html
    ]
    subprocess.run(cmd, check=True)

    if os.path.exists(temp_html):
        os.remove(temp_html)

    print(f"[OK] Gerado com sucesso: {os.path.basename(pdf_path)} ({os.path.getsize(pdf_path)} bytes)")


def convert_txt_to_pdf(txt_path, pdf_path, title="Documento Técnico"):
    with open(txt_path, "r", encoding="utf-8") as f:
        raw_text = f.read()

    escaped_text = html.escape(raw_text)

    full_html = f"""<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<title>{title}</title>
<style>{CSS_STYLESHEET}</style>
</head>
<body>
<div class="txt-box">{escaped_text}</div>
</body>
</html>"""

    temp_html = pdf_path.replace(".pdf", ".temp.html")
    with open(temp_html, "w", encoding="utf-8") as f:
        f.write(full_html)

    cmd = [
        EDGE_PATH,
        "--headless",
        "--disable-gpu",
        "--no-pdf-header-footer",
        f"--print-to-pdf={pdf_path}",
        temp_html
    ]
    subprocess.run(cmd, check=True)

    if os.path.exists(temp_html):
        os.remove(temp_html)

    print(f"[OK] Gerado com sucesso: {os.path.basename(pdf_path)} ({os.path.getsize(pdf_path)} bytes)")


def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    tasks = [
        # 1. Relatório Técnico Completo (Renderizado do Markdown original com tabelas e estilos profissionais)
        (
            os.path.join(BASE_DIR, "docs", "relatorio_tecnico_devops_qaops.md"),
            os.path.join(OUTPUT_DIR, "01_RELATORIO_TECNICO_DEVOPS_QAOPS.pdf"),
            "md"
        ),
        # 2. Roteiro de Apresentação e Defesa
        (
            os.path.join(BASE_DIR, "docs", "roteiro_apresentacao_defesa.md"),
            os.path.join(OUTPUT_DIR, "02_ROTEIRO_APRESENTACAO_E_DEFESA.pdf"),
            "md"
        ),
        # 3. README e Documentação Geral
        (
            os.path.join(BASE_DIR, "README.md"),
            os.path.join(OUTPUT_DIR, "03_README_DOCUMENTACAO_GERAL.pdf"),
            "md"
        ),
        # 4. Resumo Executivo para Avaliação Prévia
        (
            os.path.join(OUTPUT_DIR, "04_RESUMO_EXECUTIVO_AVALIACAO_PREVIA.txt"),
            os.path.join(OUTPUT_DIR, "04_RESUMO_EXECUTIVO_AVALIACAO_PREVIA.pdf"),
            "txt"
        ),
        # 5. Matriz de Quality Gates e Evidências
        (
            os.path.join(OUTPUT_DIR, "05_MATRIZ_QUALITY_GATES_E_EVIDENCIAS.txt"),
            os.path.join(OUTPUT_DIR, "05_MATRIZ_QUALITY_GATES_E_EVIDENCIAS.pdf"),
            "txt"
        ),
        # 6. Guia Rápido de Demonstração para o Professor
        (
            os.path.join(OUTPUT_DIR, "06_GUIA_DEMONSTRACAO_RAPIDA_PROFESSOR.txt"),
            os.path.join(OUTPUT_DIR, "06_GUIA_DEMONSTRACAO_RAPIDA_PROFESSOR.pdf"),
            "txt"
        ),
    ]

    for src, dst, kind in tasks:
        if kind == "md":
            convert_md_to_pdf(src, dst)
        else:
            convert_txt_to_pdf(src, dst)

    print("\nTodos os 6 documentos em PDF foram gerados na pasta:")
    print(OUTPUT_DIR)

if __name__ == "__main__":
    main()
