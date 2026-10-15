"""Script utilitário para executar os experimentos obrigatórios de QAOps.

Permite injetar falhas controladas para demonstrar o bloqueio dos Quality Gates no
pipeline e reverter para a versão aprovada.

Uso:
    python scripts/simulate_failure.py --cause test     (quebra regra de negócio / teste)
    python scripts/simulate_failure.py --cause lint     (quebra o linter flake8/black)
    python scripts/simulate_failure.py --cause syntax   (quebra a compilação/build)
    python scripts/simulate_failure.py --revert         (restaura o código aprovado via git)
"""

import argparse
import subprocess
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
SERVICES_FILE = ROOT_DIR / "src" / "services.py"


def apply_test_failure():
    print("[EXPERIMENTO] Injetando falha controlada de regra de negócio em src/services.py...")
    content = SERVICES_FILE.read_text(encoding="utf-8")
    # Altera o cálculo de subtotal para subtrair R$ 50 indevidamente
    tampered = content.replace(
        "subtotal = sum(item.unit_price * item.quantity for item in items)",
        "subtotal = sum(item.unit_price * item.quantity for item in items) - 50.0  # FALHA CONTROLADA QA",
    )
    SERVICES_FILE.write_text(tampered, encoding="utf-8")
    print("[OK] Falha injetada. Ao rodar os testes, o Quality Gate 4 e 5 irão falhar e bloquear o pipeline!")


def apply_lint_failure():
    print("[EXPERIMENTO] Injetando falha de análise estática / linter em src/services.py...")
    content = SERVICES_FILE.read_text(encoding="utf-8")
    # Injeta import não utilizado e linha excessivamente longa fora do padrão PEP8
    violation = "import os, sys  # UNUSED IMPORT\n# " + ("X" * 120) + "\n"
    tampered = violation + content
    SERVICES_FILE.write_text(tampered, encoding="utf-8")
    print("[OK] Violação de lint injetada. O Quality Gate 2 (Flake8 / Black) irá falhar e bloquear o pipeline!")


def apply_syntax_failure():
    print("[EXPERIMENTO] Injetando falha de sintaxe / build em src/services.py...")
    content = SERVICES_FILE.read_text(encoding="utf-8")
    tampered = "class BrokenSyntaxDef(\n" + content
    SERVICES_FILE.write_text(tampered, encoding="utf-8")
    print("[OK] Erro de sintaxe injetado. O Quality Gate 1 (Build/Compile) irá falhar e bloquear o pipeline!")


def revert_changes():
    print("[EXPERIMENTO] Restaurando arquivos para a versão aprovada...")
    subprocess.run(["git", "checkout", "--", "src/"], check=True)
    print("[OK] Código revertido com sucesso! O pipeline voltará a ser aprovado.")


def main():
    parser = argparse.ArgumentParser(description="Simulador de Experimentos QAOps")
    parser.add_argument(
        "--cause",
        choices=["test", "lint", "syntax"],
        help="Tipo de falha controlada a ser injetada",
    )
    parser.add_argument(
        "--revert",
        action="store_true",
        help="Reverte todas as alterações de volta para o estado limpo",
    )

    args = parser.parse_args()

    if args.revert:
        revert_changes()
    elif args.cause == "test":
        apply_test_failure()
    elif args.cause == "lint":
        apply_lint_failure()
    elif args.cause == "syntax":
        apply_syntax_failure()
    else:
        parser.print_help()
        sys.exit(1)


if __name__ == "__main__":
    main()
