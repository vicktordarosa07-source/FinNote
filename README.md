# FinNote

[![Abrir FinNote Web](https://img.shields.io/badge/FinNote-Web%20App-075b46?logo=pwa)](https://finnote-chi.vercel.app/)
[![Baixar APK](https://img.shields.io/badge/Download-APK-brightgreen?logo=android)](https://github.com/vicktordarosa07-source/FinNote/releases/latest/download/FinNote.apk)

[⬇ Baixar FinNote para Android](https://github.com/vicktordarosa07-source/FinNote/releases/latest/download/FinNote.apk)

🌐 [Abrir FinNote Web na Vercel](https://finnote-chi.vercel.app/) · ⬇ [Baixar FinNote para Android](https://github.com/vicktordarosa07-source/FinNote/releases/latest/download/FinNote.apk)

## FinNote Web e instalação PWA

A versão web é responsiva e pode ser instalada como aplicativo em navegadores compatíveis. Abra [FinNote Web na Vercel](https://finnote-chi.vercel.app/) em HTTPS e use **Instalar app**. No iPhone/iPad, use **Compartilhar > Adicionar à Tela de Início** no Safari. O app guarda os lançamentos localmente e abre offline depois da primeira visita; login e compartilhamento público entre contas exigem configurar o Supabase abaixo em **Ajustes**.

Para executar localmente:

```sh
cd web
pnpm install
pnpm dev
```

A Vercel está conectada ao repositório GitHub, com `web/` como diretório raiz, e publica automaticamente cada alteração enviada à branch `main`.

## Recursos
- Categorias e subcategorias personalizáveis, incluindo Gastos fixos, Transporte e Mercado.
- Lançamentos públicos para usuários conectados e lançamentos privados locais.
- Dashboard com comparativo dos últimos seis meses e gráficos por categoria e produto.
- Cadastro por nome e telefone, senha com confirmação, verificação por SMS e convites por link.
- Renda mensal fixa, rendas extras, entradas, saídas e saldo do mês.
- Ditado pt-BR com sugestão de categoria e revisão antes de salvar.
- Sessão e telefone protegidos com Android Keystore.
- Atalho **Falar gasto** no menu do launcher ao manter o ícone pressionado (Android 7.1+ compatível).

## Configuração do compartilhamento

O compartilhamento e o login por telefone/senha requerem um projeto Supabase. Não há credenciais de produção neste repositório:

1. No Supabase, habilite autenticação por telefone e senha e configure um provedor de SMS para confirmar novos telefones.
2. Execute [`supabase/schema.sql`](supabase/schema.sql) no SQL Editor do projeto.
3. Para publicar com configuração compartilhada, adicione `VITE_SUPABASE_URL` e `VITE_SUPABASE_ANON_KEY` às variáveis do projeto Vercel e faça um novo deploy. Elas são públicas no navegador; **nunca** use uma chave `service_role` no frontend ou APK.
4. Se essas variáveis não estiverem no deploy, a tela inicial permite informar a URL e a chave pública no próprio dispositivo.
5. Crie uma conta com nome, telefone internacional (por exemplo, `+5511999999999`), senha e confirmação. Se a confirmação de telefone estiver ativa, informe o código recebido por SMS.
6. Toque em **Criar link** e envie o convite. A outra pessoa precisa entrar com a própria conta e aceitar o link.

As políticas RLS do SQL permitem que uma conta leia os próprios lançamentos e os lançamentos públicos das contas conectadas. Lançamentos privados nunca são enviados pelo app. Lançamentos públicos registrados offline ficam pendentes no aparelho e são enviados na próxima sincronização.

## Limitações

O login e o compartilhamento ficam inativos até a configuração do Supabase e, conforme as opções do projeto, do provedor de SMS. Telefones podem ser reciclados pelas operadoras; considere ativar MFA para contas em produção. A sincronização é acionada ao entrar, ao salvar um lançamento público e pelo botão **Sincronizar**; não há sincronização em tempo real. Categorias/subcategorias são armazenadas no aparelho e não são compartilhadas. O comparativo repete a renda fixa configurada nos meses anteriores, pois o app não mantém histórico de alteração salarial. O reconhecimento de voz depende do serviço de fala habilitado no Android. No menu que aparece ao manter o ícone pressionado, toque em **Falar gasto**.

## Build
Abra a pasta no Android Studio e use **Build > Build APK(s)**. O GitHub Actions compila o APK de debug em cada push e publica o arquivo como artefato da execução. O APK do botão de download é assinado para depuração; distribuição final exige assinatura de release.

