# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

This repo is a **greenfield rewrite** of a legacy garment/apparel ERP called "sisconf". There is no source code yet — the repo currently contains only the `prd/` directory of reverse-engineered product requirement documents (in Portuguese). The tech stack for the new system has **not been decided**; do not assume any particular language or framework until it is chosen.

## The `prd/` directory

- `prd/PRD-00-indice.md` is the index of all PRDs.
- `prd/ORDEM-EXECUCAO.md` lays out the business-prioritized build order: Login/Multitenant → Pedidos/Vendas → Compras → Financeiro → Estoque → Sped Fiscal, with RH/Expedição/Cobrança/Contábil/Administração/Produção deferred to a later phase.
- `prd/PRD-01` through `prd/PRD-13` document **legacy system behavior as business rules to preserve** — they describe what the old sisconf app does, not instructions to modify legacy Java code (which isn't in this repo).
- `prd/PRD-14` (Login Multitenant) describes the actual first piece of the new system: a stack-agnostic multitenant identity/auth foundation that other domains build on top of.
- Treat these PRDs as a **read-only reference spec**. Don't edit them unless explicitly asked to.

## Security requirement

The legacy system committed DB/SMTP credentials in plaintext (`hibernate.cfg.xml`, `application.properties`). The new system must handle all secrets via environment variables or a secret manager from the first deploy — never commit credentials to source.
