/**
 * Script de Simulação de Experimento de Qualidade (Aprovado vs Reprovado)
 * Disciplina de Qualidade de Software / DevOps - Gusto Backend
 *
 * Uso:
 *   node scripts/simulate_failure.js --inject-test-failure
 *   node scripts/simulate_failure.js --inject-type-failure
 *   node scripts/simulate_failure.js --revert
 */

const fs = require('fs');
const path = require('path');

const targetAuthPath = path.resolve(__dirname, '../backend/src/lib/auth.ts');
const backupAuthPath = path.resolve(__dirname, '../backend/src/lib/auth.ts.bak');

const mode = process.argv[2] || '--help';

function injectTestFailure() {
  if (!fs.existsSync(backupAuthPath)) {
    fs.copyFileSync(targetAuthPath, backupAuthPath);
  }
  let content = fs.readFileSync(targetAuthPath, 'utf8');

  // Injeta falha de regressão de negócio: o código de OTP passa a gerar apenas 3 dígitos em vez de 6
  // Isso quebra os testes unitários de segurança e validação de token
  content = content.replace(
    'const otp = Math.floor(100000 + Math.random() * 900000).toString();',
    '// REGRESSÃO CONTROLADA INJETADA: Gera 3 dígitos violando contrato de segurança\n  const otp = Math.floor(100 + Math.random() * 900).toString();'
  );

  fs.writeFileSync(targetAuthPath, content, 'utf8');
  console.log('❌ Regressão de regra de negócio injetada em backend/src/lib/auth.ts');
  console.log('   Efeito esperado: Os testes unitários de segurança de OTP irão FALHAR, bloqueando o Quality Gate de Testes.');
}

function injectTypeFailure() {
  if (!fs.existsSync(backupAuthPath)) {
    fs.copyFileSync(targetAuthPath, backupAuthPath);
  }
  let content = fs.readFileSync(targetAuthPath, 'utf8');

  // Injeta quebra de contrato de tipagem TypeScript
  content = content.replace(
    'export async function hashPassword(password: string): Promise<string> {',
    'export async function hashPassword(password: string): Promise<number> { // REGRESSÃO DE TIPO\n    return "string_invalida_onde_espera_number" as any;'
  );

  fs.writeFileSync(targetAuthPath, content, 'utf8');
  console.log('❌ Quebra de contrato de tipagem injetada em backend/src/lib/auth.ts');
  console.log('   Efeito esperado: O Quality Gate 1 (Typecheck / TypeScript) irá FALHAR com erro de compilação antes dos testes.');
}

function revertChanges() {
  if (fs.existsSync(backupAuthPath)) {
    fs.copyFileSync(backupAuthPath, targetAuthPath);
    fs.unlinkSync(backupAuthPath);
    console.log('✅ Alterações revertidas com sucesso! Código limpo restaurado.');
  } else {
    console.log('ℹ️ Nenhum backup encontrado. O código já está em estado original.');
  }
}

switch (mode) {
  case '--inject-test-failure':
    injectTestFailure();
    break;
  case '--inject-type-failure':
    injectTypeFailure();
    break;
  case '--revert':
    revertChanges();
    break;
  default:
    console.log(`
Uso do utilitário de simulação de experimento:
  node scripts/simulate_failure.js --inject-test-failure   -> Injeta defeito funcional para falhar no gate de testes
  node scripts/simulate_failure.js --inject-type-failure   -> Injeta erro de tipagem para falhar no gate de compilação
  node scripts/simulate_failure.js --revert               -> Restaura o estado limpo e aprovado
`);
}
