# Roteiro de Demonstração e Defesa Oral (10 a 12 Minutos)
**Projeto Prático DevOps & QAOps — OrderFlow API**

Este documento é um guia passo a passo para a dupla realizar a apresentação para o professor, com minutagem sugerida, divisão de falas, telas a serem exibidas e respostas antecipadas para as perguntas prováveis da banca.

---

## Estrutura do Tempo (Total: 10 a 12 Minutos)

```text
[00:00 - 02:00] (2 min) -> Introdução, Contexto do Sistema e Riscos
[02:00 - 04:00] (2 min) -> Arquitetura do Pipeline e Quality Gates
[04:00 - 06:30] (2.5 min) -> Demonstração ao Vivo: Cenário Aprovado e Evidências
[06:30 - 09:00] (2.5 min) -> Demonstração ao Vivo: Cenário Reprovado e Bloqueio
[09:00 - 10:30] (1.5 min) -> Correção, Nova Aprovação e Histórico
[10:30 - 12:00] (1.5 min) -> Conclusão, Análise Crítica e Fechamento
```

---

## Roteiro Minuto a Minuto

### 1. Introdução e Contextualização (Minutos 00:00 a 02:00) — Integrante 1
* **O que falar:**
  > "Olá, professor. Nosso trabalho tem como tema a integração contínua da qualidade no fluxo DevOps, ou seja, o QAOps. Para este projeto, desenvolvemos o **OrderFlow API**, um microsserviço em Python com FastAPI voltado à precificação, regras de cupons, fidelidade de clientes e cálculo de frete logístico.  
  > Em vez de tratarmos a qualidade como uma verificação manual ao final do ciclo, nosso objetivo central foi responder à pergunta: *como automatizar verificações objetivas que detectem alterações inadequadas e impeçam seu avanço no fluxo de entrega?*  
  > Mapeamos riscos essenciais como quebra de sintaxe, código despadronizado, vulnerabilidades em dependências externas e regressões em cálculos financeiros."
* **O que mostrar na tela:** O slide inicial ou a seção 1 e 2 do `README.md` no repositório.

---

### 2. O Pipeline e os Quality Gates (Minutos 02:00 a 04:00) — Integrante 2
* **O que falar:**
  > "Construímos o pipeline no **GitHub Actions**, configurado para ser disparado a cada `push`, `pull request` ou disparo manual no branch `main`.  
  > Definimos **5 Quality Gates** com critérios objetivos e mensuráveis:
  > 1. **Gate 1 (Build/Sintaxe):** Pré-compilação com `compileall` para garantir que o código inicializa.
  > 2. **Gate 2 (Análise Estática):** Conformidade rigorosa com `black` e `flake8` contra débitos técnicos.
  > 3. **Gate 3 (Segurança):** Auditoria de vulnerabilidades conhecidas com `pip-audit`, protegendo a cadeia de suprimentos de software.
  > 4. **Gate 4 (Testes Automatizados):** 19 testes cobrindo regras de negócio unitárias e contratos de integração da API.
  > 5. **Gate 5 (Cobertura de Código):** Exigência de no mínimo 85% de cobertura via `pytest-cov`, tendo nosso projeto atingido **100% de cobertura** real."
* **O que mostrar na tela:** O arquivo `.github/workflows/ci-pipeline.yml` e o diagrama visual do pipeline.

---

### 3. Demonstração: Cenário 1 - Aprovado (Minutos 04:00 a 06:30) — Integrante 1
* **O que fazer na tela:**
  1. Abrir a aba **Actions** no repositório do GitHub (ou rodar no terminal `pytest`).
  2. Clicar na execução mais recente que está com o ícone verde (`✓`).
* **O que falar:**
  > "Aqui vemos o pipeline executado com sucesso sobre a versão homologada. Em aproximadamente 45 segundos, todos os jobs foram concluídos:
  > - O runner provisionou o Python 3.11 com cache de dependências.
  > - A análise estática e de segurança passaram com zero alertas.
  > - Os 19 testes passaram com 100% de sucesso.
  > - E na etapa final, o pipeline gerou e preservou os artefatos de evidências: o relatório estruturado JUnit XML e o relatório visual de cobertura em HTML, que podem ser baixados diretamente pelo GitHub Actions para fins de auditoria de conformidade."
* **O que mostrar na tela:** A árvore de etapas do GitHub Actions e o download dos artefatos em `Artifacts: qaops-evidencias-relatorios`.

---

### 4. Demonstração: Cenário 2 - Reprovado Controlado (Minutos 06:30 a 09:00) — Integrante 2
* **O que fazer na tela:**
  1. Abrir o terminal e mostrar a injeção da falha com o comando:
     ```bash
     python scripts/simulate_failure.py --cause test
     git add src/services.py
     git commit -m "feat: alteracao experimental para validar bloqueio do pipeline"
     git push origin main
     ```
  2. Alternar para a aba **Actions** do GitHub e acompanhar a execução ao vivo.
* **O que falar:**
  > "Agora vamos demonstrar o que acontece quando um desenvolvedor comete um erro lógico. Simulamos uma alteração que subtrai R$ 50 do subtotal de todos os pedidos.
  > Observem que o Gate 1 de compilação passa, o Gate 2 de linter passa e o Gate 3 de segurança passa. No entanto, ao atingir o **Quality Gate 4**, a esteira detecta imediatamente as asserções inválidas.
  > O pipeline é interrompido com status vermelho (código 1). A esteira não permite que esse código defeituoso seja empacotado, não gera artefatos de deploy e notifica a equipe do erro exato com stack trace detalhado."
* **O que mostrar na tela:** O job falhando em vermelho na etapa `Quality Gate 4 & 5 - Testes Automatizados e Cobertura de Código`.

---

### 5. Correção e Restauração (Minutos 09:00 a 10:30) — Integrante 1
* **O que fazer na tela:**
  1. No terminal:
     ```bash
     python scripts/simulate_failure.py --revert
     git add src/services.py
     git commit -m "fix: restaurada a regra correta de calculo do subtotal"
     git push origin main
     ```
  2. Mostrar o novo commit disparando o pipeline e ficando verde novamente.
* **O que falar:**
  > "Aplicamos a correção, restaurando o cálculo correto, e fizemos o push. O pipeline foi disparado automaticamente, revalidou todas as barreiras de qualidade e retornou ao estado verde de aprovação. O histórico do Git e do GitHub Actions preserva a rastreabilidade completa: quem quebrou, o que quebrou, onde o gate bloqueou e quem corrigiu."

---

### 6. Conclusão e Análise Crítica (Minutos 10:30 a 12:00) — Integrante 1 e Integrante 2
* **O que falar:**
  > "Para finalizar, nossa análise crítica destaca que o QAOps muda a mentalidade do time: a qualidade deixa de ser uma auditoria tardia e vira uma salvaguarda contínua (*shift-left*).
  > Como limitações do trabalho, reconhecemos que verificações manuais de usabilidade e testes de carga de alta concorrência ainda precisariam de ferramentas complementares antes de uma ida definitiva a um cluster Kubernetes.
  > Estamos abertos às perguntas do professor. Obrigado!"

---

## Possíveis Perguntas do Professor e Como Responder

1. **Pergunta: "Por que vocês escolheram usar o `pip-audit` no pipeline?"**
   * **Resposta:** *"Escolhemos o `pip-audit` porque ele analisa o banco de dados oficial de CVEs da Python Packaging Authority (PyPA). Isso atende ao risco A06 do OWASP Top 10 (Componentes Vulneráveis e Desatualizados), garantindo que nenhuma biblioteca com falhas de segurança conhecidas seja usada em produção."*

2. **Pergunta: "Por que definir o threshold de cobertura em 85% e não em 100% como meta?"**
   * **Resposta:** *"No nosso projeto atual atingimos 100% de cobertura. Porém, definimos a barreira mínima (threshold de corte) em 85% na configuração do pipeline porque em projetos corporativos reais exigir 100% de cobertura estrita pode levar a testes frágeis ou desencorajar refatorações. 85% garante uma margem de segurança extremamente alta para a lógica de negócio, permitindo flexibilidade em trechos não críticos."*

3. **Pergunta: "Qual a diferença entre automação de testes e QAOps?"**
   * **Resposta:** *"Automação de testes é apenas a criação do script que roda verificações. QAOps é a cultura e o processo integrado: os testes são executados automaticamente no fluxo de integração contínua (CI), alimentam métricas, bloqueiam deploys através de Quality Gates e produzem evidências auditáveis sem intervenção humana."*
