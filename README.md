# FinNote

[![Abrir FinNote Web](https://img.shields.io/badge/FinNote-Web%20App-075b46?logo=pwa)](https://vicktordarosa07-source.github.io/FinNote/)
[![Baixar APK](https://img.shields.io/badge/Download-APK-brightgreen?logo=android)](https://github.com/vicktordarosa07-source/FinNote/releases/latest/download/FinNote.apk)

[⬇ Baixar FinNote para Android](https://github.com/vicktordarosa07-source/FinNote/releases/latest/download/FinNote.apk)

🌐 [Abrir FinNote Web](https://vicktordarosa07-source.github.io/FinNote/) · ⬇ [Baixar FinNote para Android](https://github.com/vicktordarosa07-source/FinNote/releases/latest/download/FinNote.apk)

## FinNote Web e instalação PWA

A versão web é responsiva e pode ser instalada como aplicativo em navegadores compatíveis. Abra [FinNote Web](https://vicktordarosa07-source.github.io/FinNote/) em HTTPS e use **Instalar app**. No iPhone/iPad, use **Compartilhar > Adicionar à Tela de Início** no Safari. O app guarda os lançamentos localmente e abre offline depois da primeira visita; login e compartilhamento público entre contas exigem configurar o Supabase abaixo em **Ajustes**.

Para executar localmente:

```sh
cd web
pnpm install
pnpm dev
```

O GitHub Actions publica automaticamente `web/` no GitHub Pages a cada alteração da aplicação web.

## Recursos
- Categorias e subcategorias personalizáveis, incluindo Gastos fixos, Transporte e Mercado.
- Lançamentos públicos para usuários conectados e lançamentos privados locais.
- Dashboard com comparativo dos últimos seis meses e gráficos por categoria e produto.
- Cadastro por nome e telefone, autenticação por SMS e convites de conexão por link.
- Renda mensal fixa, rendas extras, entradas, saídas e saldo do mês.
- Ditado pt-BR com sugestão de categoria e revisão antes de salvar.
- Sessão e telefone protegidos com Android Keystore.
- Atalho **Falar gasto** no menu do launcher ao manter o ícone pressionado (Android 7.1+ compatível).

## Configuração do compartilhamento

O compartilhamento requer um projeto Supabase (não há credenciais de produção neste repositório):

1. No Supabase, habilite autenticação por telefone e configure um provedor de SMS.
2. Execute [`supabase/schema.sql`](supabase/schema.sql) no SQL Editor do projeto.
3. No app, em **Ajustes**, informe a URL do projeto e a chave `anon`/`publishable` (nunca use uma service-role key no APK).
4. Cadastre o nome e o telefone internacional (por exemplo, `+5511999999999`) e valide o código recebido por SMS.
5. Toque em **Criar link** e envie o convite. A outra pessoa precisa instalar o FinNote, entrar com o próprio telefone e aceitar o link.

As políticas RLS do SQL permitem que uma conta leia os próprios lançamentos e os lançamentos públicos das contas conectadas. Lançamentos privados nunca são enviados pelo app. Lançamentos públicos registrados offline ficam pendentes no aparelho e são enviados na próxima sincronização.

## Limitações

O login e o compartilhamento ficam inativos até a configuração do Supabase e do provedor de SMS. A sincronização é acionada ao entrar, ao salvar um lançamento público e pelo botão **Sincronizar**; não há sincronização em tempo real. Categorias/subcategorias são armazenadas no aparelho e não são compartilhadas. O comparativo repete a renda fixa configurada nos meses anteriores, pois o app não mantém histórico de alteração salarial. O reconhecimento de voz depende do serviço de fala habilitado no Android. No menu que aparece ao manter o ícone pressionado, toque em **Falar gasto**.

## Build
Abra a pasta no Android Studio e use **Build > Build APK(s)**. O GitHub Actions compila o APK de debug em cada push e publica o arquivo como artefato da execução. O APK do botão de download é assinado para depuração; distribuição final exige assinatura de release.

