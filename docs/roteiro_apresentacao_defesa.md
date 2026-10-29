# Roteiro de Apresentação e Defesa Oral (10 a 12 Minutos)
## Trabalho Prático de DevOps e QAOps — Gusto Platform

---

### Visão Geral da Apresentação
* **Tempo Total Estipulado:** 10 a 12 minutos (com tempo extra para perguntas da banca);
* **Divisão de Papéis:**
  * **Integrante 1 (Gleison Sousa):** Contexto da aplicação, arquitetura do Gusto Backend, riscos de qualidade e demonstração prática do experimento de falha/bloqueio;
  * **Integrante 2 (Integrante da Dupla):** Arquitetura do pipeline no GitHub Actions, estratégia dos 4 Quality Gates, análise crítica e maturidade QAOps.
* **Recursos para Apresentação:**
  * Tela 1: Repositório GitHub aberto na aba **Actions** (mostrando as execuções e o Job Summary);
  * Tela 2: Terminal aberto no VS Code / PowerShell executando o comando `npm test` e o script `simulate_failure.js`.

---

### Cronograma Minuto a Minuto

```text
[00:00 - 02:00] Abertura, Contextualização e Arquitetura do Gusto (Integrante 1)
[02:00 - 04:30] Pipeline no GitHub Actions e Matriz de Quality Gates (Integrante 2)
[04:30 - 07:30] Demonstração Prática: Execução Aprovada vs. Reprovada (Ambos)
[07:30 - 09:30] Análise Crítica, Evidências e Limitações (Integrante 2)
[09:30 - 11:00] Conclusão, Métricas DORA e Próximos Passos (Integrante 1)
[11:00 - 12:00] Considerações Finais e Abertura para Perguntas da Banca (Ambos)
```

---

### Roteiro Detalhado de Falas

#### Bloco 1: Contexto e Arquitetura do Sistema (00:00 - 02:00)
**Palestrante:** Integrante 1 (Gleison)

> *"Bom dia professor e colegas. Hoje apresentamos nosso projeto prático de DevOps e QAOps focado na automação de qualidade e bloqueio de regressões no fluxo de entrega contínua.*
>
> *Para o nosso estudo de caso, escolhemos a plataforma **Gusto**, uma aplicação de receitas gastronômicas estruturada em monorepo com um aplicativo nativo Android em Jetpack Compose e um microsserviço de backend de alta criticidade desenvolvido em **Next.js 15, TypeScript 5.7, Prisma ORM e PostgreSQL**.*
>
> *Em vez de tratar qualidade como uma etapa manual e isolada ao final do desenvolvimento, nosso objetivo foi implementar o conceito de **QAOps**: transformar a garantia da qualidade em código versionado e executado automaticamente a cada push ou pull request.*
>
> *Identificamos quatro grandes categorias de riscos no Gusto: regressões na criptografia e sessões JWT com Bcrypt, falhas de segurança em fluxos de recuperação de senha via OTP, vulnerabilidade a ataques de força bruta por falta de Rate Limiting e dados culinários corrompidos na integração com a API externa TheMealDB. A partir desses riscos, projetamos nossa esteira de qualidade."*

---

#### Bloco 2: O Pipeline no GitHub Actions e os Quality Gates (02:00 - 04:30)
**Palestrante:** Integrante 2

> *(Compartilha a tela com o arquivo `ci-pipeline.yml` e o GitHub Actions)*
>
> *"Como vocês podem ver aqui na tela, nosso pipeline foi implementado de forma declarativa no **GitHub Actions** (`ubuntu-latest`). Ele é disparado a cada `push` ou `pull request` direcionado às branches principais.*
>
> *Para garantir feedback rápido (menos de 1 minuto), estruturamos **4 Quality Gates** estritos que atuam em formato fail-fast:*
>
> 1. **Quality Gate 1 — Typecheck Rigoroso:** Usamos o compilador do TypeScript (`tsc --noEmit`). Se houver qualquer divergência de tipo ou assinatura de método, a esteira bloqueia antes mesmo de rodar os testes;
> 2. **Quality Gate 2 — Auditoria de Dependências (SAST):** Executamos o `npm audit` com nível crítico. Nenhuma vulnerabilidade de severidade crítica em pacotes de terceiros é tolerada;
> 3. **Quality Gate 3 — Suíte Completa de Testes Automatizados:** Executamos 48 testes unitários e de integração de rotas com o **Vitest 5.0**, cobrindo 100% das regras críticas de autenticação, rotação de refresh token com defesa anti-reuso, rate limiting em memória e conversão de frações culinárias imperfeitas;
> 4. **Quality Gate 4 — Limite Mínimo de Cobertura de Código:** Usamos o provedor V8 do Vitest exigindo no mínimo **80% de cobertura de linhas e declarações** e **70% de branches**. Atualmente nossa linha base atinge **87.16%** de cobertura de código real.*
>
> *Ao final, o pipeline publica um resumo executivo direto no **Job Summary** do GitHub Actions e arquiva o relatório de cobertura em HTML/LCOV como artefato de evidência."*

---

#### Bloco 3: Demonstração Prática do Experimento Aprovado vs. Reprovado (04:30 - 07:30)
**Palestrante:** Ambos (Gleison na digitação/terminal e Integrante 2 narrando)

> **Passo 1: Demonstração do Cenário Aprovado**
> * Integrante 1 roda no terminal:
>   ```bash
>   npm test
>   ```
> * Integrante 2 comenta:
>   > *"Vejam que todos os 48 testes passam em menos de 4 segundos. A tipagem está íntegra e a cobertura está em mais de 87%."*
>
> **Passo 2: Injeção de Regressão Deliberada**
> * Integrante 1 executa:
>   ```bash
>   node scripts/simulate_failure.js --inject-test-failure
>   ```
> * Integrante 1 explica:
>   > *"Agora simulamos um cenário real: um desenvolvedor cometeu um descuido e alterou a regra de negócio da função `generateOtpCode()` em `auth.ts`, fazendo com que ela gere apenas 3 dígitos em vez dos 6 obrigatórios por contrato de segurança."*
> * Integrante 1 roda:
>   ```bash
>   npm test
>   ```
> * A tela exibe o erro em vermelho:
>   ```text
>   FAIL tests/auth.test.ts > deve gerar código OTP numérico exatamente com 6 dígitos
>   AssertionError: expected '184' to match /^\d{6}$/
>   ```
> * Integrante 2 comenta:
>   > *"Observem o resultado: o Quality Gate 3 detectou a quebra imediatamente e encerrou com código 1. No GitHub Actions, o pipeline interrompe a execução, não gera o pacote e impede que essa vulnerabilidade chegue a qualquer usuário."*
>
> **Passo 3: Correção e Restauração da Integridade**
> * Integrante 1 executa:
>   ```bash
>   node scripts/simulate_failure.js --revert
>   npm test
>   ```
> * Integrante 1 comenta:
>   > *"Aplicamos a correção, revertemos a regressão e re-executamos. Como podem ver, a suíte volta ao estado 100% verde, reabilitando a aprovação do Quality Gate."*

---

#### Bloco 4: Análise Crítica e Respostas ao Roteiro da Disciplina (07:30 - 09:30)
**Palestrante:** Integrante 2

> *"Em nosso relatório técnico, respondemos às seis questões norteadoras da disciplina:*
>
> * **Riscos Cobertos vs. Residuais:** Cobrimos com excelência toda a lógica de negócio, sessões e contratos de API. Como risco residual consciente, deixamos os testes de estresse sob carga pesada e testes de renderização visual no dispositivo Android físico, que demandariam emuladores pesados no CI;
> * **Tempo de Feedback:** Nosso ciclo de feedback completo roda em menos de 10 segundos localmente e 45 segundos na nuvem, atingindo o padrão ouro preconizado pelo DORA;
> * **Custo de Manutenção:** O custo computacional é praticamente nulo graças aos mocks em memória do Prisma e à velocidade do Vitest;
> * **O diferencial do QAOps:** Automação isolada é um script local que pode ser esquecido; QAOps é a qualidade institucionalizada no fluxo contínuo de engenharia, onde o software só avança se comprovar matematicamente que atende aos critérios pré-estabelecidos."*

---

#### Bloco 5: Conclusão e Abertura para Perguntas (09:30 - 11:00)
**Palestrante:** Integrante 1 (Gleison)

> *"Para concluir: demonstramos que o pipeline do Gusto não é apenas uma automação técnica, mas uma ferramenta de governança de software. Conseguimos cobrir riscos críticos de segurança e de negócio com um sistema reprodutível, documentado e respaldado por evidências auditáveis.*
>
> *Nosso repositório contém o código, os testes, as instruções no README e o relatório de 8 páginas sem anexos.*
>
> *Agradecemos a atenção e estamos abertos às perguntas do professor."*

---

### Guia de Respostas Rápidas para Perguntas da Banca

#### Pergunta 1: *"Por que vocês escolheram o Vitest em vez do Jest no Backend?"*
> **Resposta:** *"O Vitest foi desenvolvido sobre a engine do Vite e possui suporte nativo a TypeScript e ESM sem necessidade de transpilação pesada com babel ou ts-jest. Além disso, executa testes com paralelismo nativo e se integra perfeitamente com o módulo de cobertura V8, executando nossos 48 testes em menos de 4 segundos."*

#### Pergunta 2: *"Como vocês testaram o banco de dados sem conectar a uma instância externa no CI?"*
> **Resposta:** *"Utilizamos mocks controlados do Prisma Client através do `vi.mock('@/lib/prisma')`. Isso isola as regras de negócio de rede e credenciais externas, garantindo testes determinísticos, ultra-rápidos e que nunca falham por instabilidade de conexão com o banco Neon."*

#### Pergunta 3: *"Por que o Quality Gate de Cobertura está fixado em 80% e não 100%?"*
> **Resposta:** *"Exigir 100% de cobertura frequentemente incentiva testes superficiais apenas para cumprir meta numérica. Definimos 80% para cobrir todas as regras de negócio e fluxos alternativos dos módulos críticos (`auth`, `rate-limit`, rotas principais), deixando arquivos puramente declarativos de infraestrutura fora da penalização."*

#### Pergunta 4: *"O que aconteceria se um desenvolvedor tentasse ignorar o pipeline e fazer merge direto?"*
> **Resposta:** *"No GitHub Actions, configuramos o workflow para ser exigido nas regras de Branch Protection da branch `main`. Se o pipeline falhar ou não tiver sido executado, o botão de merge fica bloqueado por padrão."*
